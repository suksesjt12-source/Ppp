package com.stormrevolution.launcher

import android.app.Application
import com.stormrevolution.launcher.manager.AudioManager
import com.stormrevolution.launcher.manager.ControllerManager
import com.stormrevolution.launcher.manager.DiagnosticManager
import com.stormrevolution.launcher.manager.GameManager
import com.stormrevolution.launcher.manager.GraphicsManager
import com.stormrevolution.launcher.manager.PerformanceMonitor
import com.stormrevolution.launcher.manager.ProcessManager
import com.stormrevolution.launcher.manager.RuntimeManager
import com.stormrevolution.launcher.manager.SettingsManager
import com.stormrevolution.launcher.manager.StorageManager
import com.stormrevolution.launcher.manager.WineManager
import com.stormrevolution.launcher.model.LogEntry
import com.stormrevolution.launcher.model.LogLevel
import com.stormrevolution.launcher.model.LogSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

class StormAppContainer(application: Application) {
    val storageManager = StorageManager(application)
    val graphicsManager = GraphicsManager(application)
    val audioManager = AudioManager(application)
    val runtimeManager = RuntimeManager(application, storageManager)
    val wineManager = WineManager(storageManager)
    val gameManager = GameManager(application, storageManager)
    val controllerManager = ControllerManager(application)
    val diagnosticManager = DiagnosticManager()
    val settingsManager = SettingsManager(application)
    val performanceMonitor = PerformanceMonitor()

    private val logSeq = AtomicLong(1L)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    val processManager = ProcessManager(
        storageManager = storageManager,
        runtimeManager = runtimeManager,
        wineManager = wineManager,
        graphicsManager = graphicsManager,
        audioManager = audioManager,
        controllerManager = controllerManager,
        onEmitLog = ::appendLog
    )

    fun appendLog(level: LogLevel, source: LogSource, message: String) {
        val entry = LogEntry(
            id = logSeq.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            level = level,
            source = source,
            message = message
        )
        _logs.update { current ->
            (current + entry).takeLast(600)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

class StormApplication : Application() {
    lateinit var container: StormAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = StormAppContainer(this)
        container.appendLog(
            LogLevel.INFO,
            LogSource.LAUNCHER,
            "Storm Launcher initialized • Bridge=${NativeBridge.getBridgeVersion()}"
        )
    }
}
