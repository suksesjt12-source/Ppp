package com.stormrevolution.launcher.manager

import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.LaunchSessionState
import com.stormrevolution.launcher.model.PerformanceSnapshot
import com.stormrevolution.launcher.model.ProcessState

class PerformanceMonitor {

    fun sample(config: GameConfiguration, session: LaunchSessionState): PerformanceSnapshot {
        val memKb = NativeBridge.readProcMemoryKb()
        val totalMb = memKb.first / 1024L
        val availMb = memKb.second / 1024L
        val tempC = NativeBridge.readThermalCelsius()
        val estFps = if (session.state == ProcessState.RUNNING) config.targetFps.fps else 0

        return PerformanceSnapshot(
            estimatedFps = estFps,
            targetFps = config.targetFps.fps,
            totalRamMb = totalMb,
            freeRamMb = availMb,
            processRssMb = session.rssMemoryMb,
            thermalCelsius = tempC,
            thermalThrottleWarning = tempC >= 45f
        )
    }
}
