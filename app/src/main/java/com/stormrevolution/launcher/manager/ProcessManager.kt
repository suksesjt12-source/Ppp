package com.stormrevolution.launcher.manager

import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GameLibraryState
import com.stormrevolution.launcher.model.LaunchSessionState
import com.stormrevolution.launcher.model.LogLevel
import com.stormrevolution.launcher.model.LogSource
import com.stormrevolution.launcher.model.ProcessState
import com.stormrevolution.launcher.model.RuntimeArchitectureMode
import com.stormrevolution.launcher.model.RuntimeComponent
import com.stormrevolution.launcher.model.RuntimeComponentId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class ProcessManager(
    private val storageManager: StorageManager,
    private val wineManager: WineManager,
    private val graphicsManager: GraphicsManager,
    private val audioManager: AudioManager,
    private val controllerManager: ControllerManager,
    private val onEmitLog: (LogLevel, LogSource, String) -> Unit
) {

    private val _sessionState = MutableStateFlow(LaunchSessionState())
    val sessionState: StateFlow<LaunchSessionState> = _sessionState.asStateFlow()

    private var monitorJob: Job? = null

    fun launchGame(
        scope: CoroutineScope,
        gameState: GameLibraryState,
        runtimeComponents: Map<RuntimeComponentId, RuntimeComponent>,
        config: GameConfiguration
    ): Boolean {
        if (!NativeBridge.isNativeLoaded) {
            val msg = "Native C++20 runtime bridge (libstorm_runtime_bridge.so) is not packaged in this APK build."
            onEmitLog(LogLevel.ERROR, LogSource.RUNTIME, msg)
            _sessionState.value = LaunchSessionState(
                state = ProcessState.FAILED,
                statusDetail = msg
            )
            return false
        }

        val wineBin = File(storageManager.runtimeRootDir, "wine/bin/wine")
        val box64Bin = File(storageManager.runtimeRootDir, "box64/bin/box64")
        val box86Bin = File(storageManager.runtimeRootDir, "box86/bin/box86")

        if (runtimeComponents[RuntimeComponentId.WINE]?.status != ComponentStatus.INSTALLED || !wineBin.exists()) {
            val msg = "Wine ARM64 runtime binary is missing or unverified."
            onEmitLog(LogLevel.ERROR, LogSource.WINE, msg)
            _sessionState.value = LaunchSessionState(state = ProcessState.FAILED, statusDetail = msg)
            return false
        }

        val exePath = when {
            gameState.copiedPrivateExePath.isNotBlank() && File(gameState.copiedPrivateExePath).exists() ->
                gameState.copiedPrivateExePath
            else -> ""
        }
        if (exePath.isBlank()) {
            val msg = "NSUNSR.exe must be imported into app-private game storage so Wine can access it via POSIX path."
            onEmitLog(LogLevel.ERROR, LogSource.LAUNCHER, msg)
            _sessionState.value = LaunchSessionState(state = ProcessState.FAILED, statusDetail = msg)
            return false
        }

        _sessionState.value = LaunchSessionState(
            state = ProcessState.PREPARING_PREFIX,
            statusDetail = "Initializing Wine prefix '${config.winePrefixName}'..."
        )
        wineManager.initializePrefixStructure(config.winePrefixName, config)

        val sysInfo = graphicsManager.collectDeviceSystemInfo()
        val gfxDecision = graphicsManager.resolveGraphicsDecision(config, sysInfo, runtimeComponents)
        val audioEnv = audioManager.buildAudioEnvVars(config, storageManager.runtimeRootDir.absolutePath)
        val envMap = wineManager.buildRuntimeEnvironment(config, gfxDecision, audioEnv)

        val (runnerExe, argv) = when (config.architectureMode) {
            RuntimeArchitectureMode.WINE_WOW64 -> {
                if (box64Bin.exists()) {
                    box64Bin.absolutePath to arrayOf(box64Bin.absolutePath, wineBin.absolutePath, exePath)
                } else {
                    wineBin.absolutePath to arrayOf(wineBin.absolutePath, exePath)
                }
            }
            RuntimeArchitectureMode.WINE_BOX86_ARM32 -> {
                if (!box86Bin.exists()) {
                    val msg = "Box86 selected but box86 binary is not installed."
                    onEmitLog(LogLevel.ERROR, LogSource.RUNTIME, msg)
                    _sessionState.value = LaunchSessionState(state = ProcessState.FAILED, statusDetail = msg)
                    return false
                }
                box86Bin.absolutePath to arrayOf(box86Bin.absolutePath, wineBin.absolutePath, exePath)
            }
            RuntimeArchitectureMode.WINE_BOX64_ONLY -> {
                val msg = "Pure Box64 mode is blocked for 32-bit PE executable NSUNSR.exe."
                onEmitLog(LogLevel.ERROR, LogSource.RUNTIME, msg)
                _sessionState.value = LaunchSessionState(state = ProcessState.FAILED, statusDetail = msg)
                return false
            }
        }

        val logFile = File(storageManager.logsDir, "nsunsr_session_${System.currentTimeMillis()}.log")
        logFile.writeText("=== Storm Launcher Session Log ===\nCMD: ${argv.joinToString(" ")}\n")

        val envArray = envMap.entries.map { "${it.key}=${it.value}" }.toTypedArray()
        val workingDir = File(exePath).parentFile?.absolutePath ?: storageManager.winePrefixesDir.absolutePath

        _sessionState.update {
            it.copy(
                state = ProcessState.LAUNCHING,
                activeCommandLine = argv.joinToString(" "),
                activeLogFilePath = logFile.absolutePath,
                statusDetail = "Spawning ${File(runnerExe).name}..."
            )
        }

        val pid = NativeBridge.spawnProcess(
            executable = runnerExe,
            argv = argv,
            envp = envArray,
            workingDir = workingDir,
            logFilePath = logFile.absolutePath
        )

        if (pid <= 0) {
            val msg = "Process spawn failed (code=$pid)."
            onEmitLog(LogLevel.ERROR, LogSource.RUNTIME, msg)
            _sessionState.update { it.copy(state = ProcessState.FAILED, statusDetail = msg) }
            return false
        }

        envMap["STORM_XINPUT_SOCKET"]?.let { controllerManager.openXInputChannel(it) }
        val startTime = System.currentTimeMillis()
        _sessionState.update {
            it.copy(
                state = ProcessState.RUNNING,
                pid = pid,
                startedAtMs = startTime,
                statusDetail = "Running (PID $pid) • ${gfxDecision.effectiveBackend.label}"
            )
        }
        onEmitLog(LogLevel.INFO, LogSource.WINE, "Started process PID=$pid: ${argv.joinToString(" ")}")

        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            var lastLogOffset = 0L
            while (isActive) {
                val (statusCode, exitCode) = NativeBridge.pollProcessStatus(pid)
                val rssMb = NativeBridge.readProcessRssKb(pid) / 1024L

                if (logFile.exists() && logFile.length() > lastLogOffset) {
                    runCatching {
                        val allBytes = logFile.readBytes()
                        if (allBytes.size > lastLogOffset) {
                            val chunk = String(allBytes, lastLogOffset.toInt(), (allBytes.size - lastLogOffset.toInt()))
                            lastLogOffset = allBytes.size.toLong()
                            chunk.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                onEmitLog(LogLevel.INFO, LogSource.WINE, line)
                            }
                        }
                    }
                }

                if (statusCode != 0) {
                    controllerManager.closeXInputChannel()
                    val finalState = if (exitCode == 0) ProcessState.EXITED else ProcessState.FAILED
                    _sessionState.update {
                        it.copy(
                            state = finalState,
                            exitCode = exitCode,
                            pid = -1,
                            statusDetail = "Process exited with code $exitCode"
                        )
                    }
                    onEmitLog(LogLevel.INFO, LogSource.RUNTIME, "Process $pid ended (status=$statusCode, exit=$exitCode)")
                    break
                } else {
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000L).coerceAtLeast(0L)
                    _sessionState.update {
                        it.copy(uptimeSeconds = elapsedSec, rssMemoryMb = rssMb)
                    }
                }
                delay(500L)
            }
        }
        return true
    }

    fun stopGame(forceKill: Boolean = false) {
        val pid = _sessionState.value.pid
        controllerManager.closeXInputChannel()
        if (pid > 0) {
            _sessionState.update { it.copy(state = ProcessState.STOPPING, statusDetail = "Stopping PID $pid...") }
            NativeBridge.terminateProcess(pid, forceKill)
            onEmitLog(LogLevel.WARN, LogSource.RUNTIME, "Sent ${if (forceKill) "SIGKILL" else "SIGTERM"} to PID $pid")
        } else {
            _sessionState.update { it.copy(state = ProcessState.IDLE, statusDetail = "Stopped") }
        }
        monitorJob?.cancel()
    }
}
