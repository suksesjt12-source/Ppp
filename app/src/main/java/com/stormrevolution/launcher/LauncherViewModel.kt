package com.stormrevolution.launcher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormrevolution.launcher.model.AudioBackendMode
import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.ControlLayoutConfig
import com.stormrevolution.launcher.model.DeviceSystemInfo
import com.stormrevolution.launcher.model.DiagnosticReport
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GameLibraryState
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.GraphicsBackendMode
import com.stormrevolution.launcher.model.GraphicsDriverOption
import com.stormrevolution.launcher.model.GraphicsRuntimeDecision
import com.stormrevolution.launcher.model.LaunchProfile
import com.stormrevolution.launcher.model.LaunchSessionState
import com.stormrevolution.launcher.model.LogEntry
import com.stormrevolution.launcher.model.LogLevel
import com.stormrevolution.launcher.model.LogSource
import com.stormrevolution.launcher.model.PerformancePreset
import com.stormrevolution.launcher.model.PerformanceSnapshot
import com.stormrevolution.launcher.model.ResolutionOption
import com.stormrevolution.launcher.model.RuntimeArchitectureMode
import com.stormrevolution.launcher.model.RuntimeComponent
import com.stormrevolution.launcher.model.RuntimeComponentId
import com.stormrevolution.launcher.model.TargetFpsOption
import com.stormrevolution.launcher.model.WinePrefixSummary
import com.stormrevolution.launcher.model.defaultStormControlElements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LauncherUiState(
    val isLoading: Boolean = true,
    val effectiveStatus: GameStatus = GameStatus.GAME_NOT_FOUND,
    val gameLibrary: GameLibraryState = GameLibraryState(),
    val runtimeComponents: Map<RuntimeComponentId, RuntimeComponent> = emptyMap(),
    val config: GameConfiguration = GameConfiguration(),
    val controlLayout: ControlLayoutConfig = ControlLayoutConfig(),
    val systemInfo: DeviceSystemInfo = DeviceSystemInfo(),
    val graphicsDecision: GraphicsRuntimeDecision = GraphicsRuntimeDecision(),
    val prefixSummary: WinePrefixSummary? = null,
    val allPrefixes: List<WinePrefixSummary> = emptyList(),
    val audioOutputs: List<String> = emptyList(),
    val diagnosticReport: DiagnosticReport = DiagnosticReport(),
    val performance: PerformanceSnapshot = PerformanceSnapshot(),
    val availableStorageText: String = "—",
    val copyProgressText: String? = null,
    val bannerFeedback: String? = null
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StormApplication
    private val container = app.container

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    val logs: StateFlow<List<LogEntry>> = container.logs
    val launchSession: StateFlow<LaunchSessionState> = container.processManager.sessionState
    val controllerState = container.controllerManager.state

    init {
        initializeAll()
        startTelemetryTicker()
    }

    private fun startTelemetryTicker() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val cur = _uiState.value
                val perf = container.performanceMonitor.sample(cur.config, launchSession.value)
                _uiState.update { it.copy(performance = perf) }
                delay(2000L)
            }
        }
    }

    fun initializeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedConfig = container.settingsManager.loadConfiguration()
            val savedControls = container.settingsManager.loadControlLayout()
            val savedGame = container.settingsManager.loadGameLibraryState()
            val privateScan = container.gameManager.inspectPrivateGameCopy()
            val effectiveGame = when {
                privateScan != null && privateScan.peResult.isValidPe -> privateScan
                savedGame.rootTreeUri.isNotBlank() && !savedGame.rootTreeUri.startsWith("private://") -> {
                    runCatching {
                        container.gameManager.scanGameDirectory(Uri.parse(savedGame.rootTreeUri))
                    }.getOrDefault(savedGame)
                }
                else -> savedGame
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    config = savedConfig,
                    controlLayout = savedControls,
                    gameLibrary = effectiveGame
                )
            }
            refreshRuntimeAndDiagnostics()
        }
    }

    fun refreshRuntimeAndDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val sysInfo = container.graphicsManager.collectDeviceSystemInfo()
            val components = container.runtimeManager.scanAllComponents()
            val cfg = _uiState.value.config
            val gfxDecision = container.graphicsManager.resolveGraphicsDecision(cfg, sysInfo, components)
            val prefixSummary = container.wineManager.inspectPrefix(cfg.winePrefixName)
            val prefixes = container.wineManager.listPrefixes()
            val audioOuts = container.audioManager.detectAudioOutputs()
            val freeBytes = container.storageManager.getAvailableInternalBytes()
            val gameState = _uiState.value.gameLibrary

            val diag = container.diagnosticManager.runDiagnostics(
                gameState = gameState,
                runtimeComponents = components,
                config = cfg,
                systemInfo = sysInfo,
                graphicsDecision = gfxDecision,
                prefixSummary = prefixSummary,
                availableStorageBytes = freeBytes
            )

            val runtimeCoreReady = isCoreRuntimeReady(components, cfg)
            val effectiveStatus = computeEffectiveStatus(gameState, runtimeCoreReady)

            _uiState.update {
                it.copy(
                    effectiveStatus = effectiveStatus,
                    runtimeComponents = components,
                    systemInfo = sysInfo,
                    graphicsDecision = gfxDecision,
                    prefixSummary = prefixSummary,
                    allPrefixes = prefixes,
                    audioOutputs = audioOuts,
                    diagnosticReport = diag,
                    availableStorageText = container.storageManager.formatByteSize(freeBytes)
                )
            }
        }
    }

    private fun isCoreRuntimeReady(
        components: Map<RuntimeComponentId, RuntimeComponent>,
        cfg: GameConfiguration
    ): Boolean {
        val wineOk = components[RuntimeComponentId.WINE]?.status == ComponentStatus.INSTALLED
        val rootfsOk = components[RuntimeComponentId.ROOTFS]?.status == ComponentStatus.INSTALLED
        val libsOk = components[RuntimeComponentId.RUNTIME_LIBS]?.status == ComponentStatus.INSTALLED
        val translatorOk = when (cfg.architectureMode) {
            RuntimeArchitectureMode.WINE_WOW64 ->
                components[RuntimeComponentId.WOW64]?.status == ComponentStatus.INSTALLED ||
                    components[RuntimeComponentId.BOX64]?.status == ComponentStatus.INSTALLED
            RuntimeArchitectureMode.WINE_BOX86_ARM32 ->
                components[RuntimeComponentId.BOX86]?.status == ComponentStatus.INSTALLED
            RuntimeArchitectureMode.WINE_BOX64_ONLY -> false
        }
        return wineOk && rootfsOk && libsOk && translatorOk
    }

    private fun computeEffectiveStatus(game: GameLibraryState, runtimeReady: Boolean): GameStatus {
        return when {
            game.status == GameStatus.GAME_NOT_FOUND -> GameStatus.GAME_NOT_FOUND
            game.status == GameStatus.GAME_VALIDATION_FAILED -> GameStatus.GAME_VALIDATION_FAILED
            !game.peResult.isValidPe -> GameStatus.GAME_FOUND
            !runtimeReady -> GameStatus.RUNTIME_MISSING
            else -> GameStatus.READY_TO_LAUNCH
        }
    }

    fun onGameDirectorySelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            container.gameManager.takePersistablePermission(uri)
            container.appendLog(LogLevel.INFO, LogSource.LAUNCHER, "Scanning selected game directory...")
            val scanned = container.gameManager.scanGameDirectory(uri)
            container.settingsManager.saveGameLibraryState(scanned)
            _uiState.update {
                it.copy(
                    gameLibrary = scanned,
                    bannerFeedback = when (scanned.status) {
                        GameStatus.GAME_READY -> "Verified NSUNSR.exe (${scanned.peResult.architectureLabel})"
                        GameStatus.GAME_NOT_FOUND -> "NSUNSR.exe not found in selected folder."
                        else -> scanned.validationErrors.firstOrNull() ?: "Game validation failed."
                    }
                )
            }
            container.appendLog(
                if (scanned.peResult.isValidPe) LogLevel.INFO else LogLevel.WARN,
                LogSource.LAUNCHER,
                "Game scan result: ${scanned.status.label} • ${scanned.peResult.summary}"
            )
            refreshRuntimeAndDiagnostics()
        }
    }

    fun copyLinkedGameToPrivateStorage() {
        val treeUriStr = _uiState.value.gameLibrary.rootTreeUri
        if (treeUriStr.isBlank() || treeUriStr.startsWith("private://")) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(copyProgressText = "Preparing copy...") }
            val (ok, msg) = container.gameManager.copyDirectoryToPrivateStorage(Uri.parse(treeUriStr)) { done, total, name ->
                _uiState.update { it.copy(copyProgressText = "Copying $done/$total: $name") }
            }
            val updated = if (ok) {
                container.gameManager.inspectPrivateGameCopy() ?: _uiState.value.gameLibrary
            } else {
                _uiState.value.gameLibrary
            }
            container.settingsManager.saveGameLibraryState(updated)
            _uiState.update {
                it.copy(
                    gameLibrary = updated,
                    copyProgressText = null,
                    bannerFeedback = msg
                )
            }
            container.appendLog(if (ok) LogLevel.INFO else LogLevel.ERROR, LogSource.LAUNCHER, msg)
            refreshRuntimeAndDiagnostics()
        }
    }

    fun clearGameSelection() {
        viewModelScope.launch(Dispatchers.IO) {
            val cleared = container.gameManager.clearLinkedGame()
            container.settingsManager.saveGameLibraryState(cleared)
            _uiState.update { it.copy(gameLibrary = cleared, bannerFeedback = "Game link cleared.") }
            refreshRuntimeAndDiagnostics()
        }
    }

    fun importRuntimePackage(uri: Uri, componentId: RuntimeComponentId? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            container.appendLog(LogLevel.INFO, LogSource.RUNTIME, "Importing runtime archive (${componentId?.displayName ?: "Bundle"})...")
            val (ok, msg) = container.runtimeManager.importRuntimeZipPackage(uri, componentId)
            container.appendLog(if (ok) LogLevel.INFO else LogLevel.ERROR, LogSource.RUNTIME, msg)
            _uiState.update { it.copy(bannerFeedback = msg) }
            refreshRuntimeAndDiagnostics()
        }
    }

    fun removeRuntimeComponent(id: RuntimeComponentId) {
        viewModelScope.launch(Dispatchers.IO) {
            val removed = container.runtimeManager.removeComponent(id)
            _uiState.update {
                it.copy(bannerFeedback = if (removed) "Removed ${id.displayName}" else "Nothing to remove")
            }
            refreshRuntimeAndDiagnostics()
        }
    }

    fun updateConfiguration(transform: (GameConfiguration) -> GameConfiguration) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = transform(_uiState.value.config)
            container.settingsManager.saveConfiguration(updated)
            _uiState.update { it.copy(config = updated) }
            refreshRuntimeAndDiagnostics()
        }
    }

    fun applyPerformancePreset(preset: PerformancePreset) {
        updateConfiguration { cur ->
            when (preset) {
                PerformancePreset.LOW -> cur.copy(
                    performancePreset = PerformancePreset.LOW,
                    launchProfile = LaunchProfile.COMPATIBILITY_SAFE,
                    resolution = ResolutionOption.R854X480,
                    targetFps = TargetFpsOption.FPS_30,
                    graphicsBackend = GraphicsBackendMode.WINED3D_OPENGL,
                    dxvkAsyncShaders = false,
                    csmtEnabled = false
                )
                PerformancePreset.BALANCED -> cur.copy(
                    performancePreset = PerformancePreset.BALANCED,
                    launchProfile = LaunchProfile.NSUNSR_OPTIMIZED,
                    resolution = ResolutionOption.R960X540,
                    targetFps = TargetFpsOption.FPS_30,
                    graphicsBackend = GraphicsBackendMode.AUTOMATIC,
                    graphicsDriver = GraphicsDriverOption.SYSTEM_MALI_VULKAN,
                    audioBackend = AudioBackendMode.PULSEAUDIO,
                    architectureMode = RuntimeArchitectureMode.WINE_WOW64,
                    dxvkAsyncShaders = true,
                    csmtEnabled = true
                )
                PerformancePreset.HIGH -> cur.copy(
                    performancePreset = PerformancePreset.HIGH,
                    launchProfile = LaunchProfile.EXPERIMENTAL_HD,
                    resolution = ResolutionOption.R1280X720,
                    targetFps = TargetFpsOption.FPS_45,
                    graphicsBackend = GraphicsBackendMode.DXVK_VULKAN,
                    dxvkAsyncShaders = true,
                    csmtEnabled = true
                )
                PerformancePreset.CUSTOM -> cur.copy(performancePreset = PerformancePreset.CUSTOM)
            }
        }
    }

    fun initializeActivePrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = _uiState.value.config
            val summary = container.wineManager.initializePrefixStructure(cfg.winePrefixName, cfg)
            container.appendLog(LogLevel.INFO, LogSource.WINE, "Initialized Wine prefix '${summary.name}' at ${summary.path}")
            _uiState.update { it.copy(bannerFeedback = "Prefix '${summary.name}' initialized.") }
            refreshRuntimeAndDiagnostics()
        }
    }

    fun resetActivePrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = _uiState.value.config
            val summary = container.wineManager.resetPrefix(cfg.winePrefixName, cfg)
            container.appendLog(LogLevel.WARN, LogSource.WINE, "Reset Wine prefix '${summary.name}'")
            _uiState.update { it.copy(bannerFeedback = "Prefix '${summary.name}' reset cleanly.") }
            refreshRuntimeAndDiagnostics()
        }
    }

    fun updateControlLayout(transform: (ControlLayoutConfig) -> ControlLayoutConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = transform(_uiState.value.controlLayout)
            container.settingsManager.saveControlLayout(next)
            _uiState.update { it.copy(controlLayout = next) }
        }
    }

    fun resetControlsToDefault() {
        updateControlLayout {
            ControlLayoutConfig(elements = defaultStormControlElements())
        }
    }

    fun refreshConnectedGamepads() {
        container.controllerManager.refreshConnectedGamepads()
    }

    fun launchGameSession(): Boolean {
        val state = _uiState.value
        if (state.effectiveStatus != GameStatus.READY_TO_LAUNCH) {
            val reason = "Launch blocked: status is ${state.effectiveStatus.label}."
            container.appendLog(LogLevel.WARN, LogSource.LAUNCHER, reason)
            _uiState.update { it.copy(bannerFeedback = reason) }
            return false
        }
        return container.processManager.launchGame(
            scope = viewModelScope,
            gameState = state.gameLibrary,
            runtimeComponents = state.runtimeComponents,
            config = state.config
        )
    }

    fun stopGameSession(forceKill: Boolean = false) {
        container.processManager.stopGame(forceKill)
    }

    fun buildSanitizedExportLogs(): String {
        val raw = logs.value.joinToString("\n") { entry ->
            "[${entry.level}] [${entry.source}] ${entry.message}"
        }
        val sys = _uiState.value.systemInfo
        val header = buildString {
            appendLine("=== Storm Launcher Sanitized Diagnostic Export ===")
            appendLine("Device: ${sys.deviceModel} | SoC: ${sys.socName}")
            appendLine("GPU: ${sys.gpuRenderer} (${sys.gpuVendor}) | Vulkan: ${sys.vulkanVersionString}")
            appendLine("Bridge: ${sys.nativeBridgeVersion}")
            appendLine("Effective Status: ${_uiState.value.effectiveStatus.label}")
            appendLine("Diagnostics: pass=${_uiState.value.diagnosticReport.passCount}, warn=${_uiState.value.diagnosticReport.warningCount}, crit=${_uiState.value.diagnosticReport.criticalCount}")
            appendLine()
        }
        return header + container.diagnosticManager.sanitizeLogTextForExport(raw)
    }

    fun clearLogs() {
        container.clearLogs()
    }

    fun dismissBannerFeedback() {
        _uiState.update { it.copy(bannerFeedback = null) }
    }
}
