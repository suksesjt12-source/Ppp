package com.stormrevolution.launcher.manager

import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.DeviceSystemInfo
import com.stormrevolution.launcher.model.DiagnosticItem
import com.stormrevolution.launcher.model.DiagnosticReport
import com.stormrevolution.launcher.model.DiagnosticSeverity
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GameLibraryState
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.GraphicsBackendMode
import com.stormrevolution.launcher.model.GraphicsDriverOption
import com.stormrevolution.launcher.model.GraphicsRuntimeDecision
import com.stormrevolution.launcher.model.RuntimeArchitectureMode
import com.stormrevolution.launcher.model.RuntimeComponent
import com.stormrevolution.launcher.model.RuntimeComponentId
import com.stormrevolution.launcher.model.WinePrefixSummary

class DiagnosticManager {

    fun runDiagnostics(
        gameState: GameLibraryState,
        runtimeComponents: Map<RuntimeComponentId, RuntimeComponent>,
        config: GameConfiguration,
        systemInfo: DeviceSystemInfo,
        graphicsDecision: GraphicsRuntimeDecision,
        prefixSummary: WinePrefixSummary?,
        availableStorageBytes: Long
    ): DiagnosticReport {
        val items = mutableListOf<DiagnosticItem>()

        // 1. CPU ABI support
        val supportsArm64 = systemInfo.supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
        items += if (supportsArm64) {
            DiagnosticItem(
                id = "abi_arm64",
                code = "SYS-01",
                title = "ARM64 ABI Verified",
                detail = "Primary ABI: ${systemInfo.supportedAbis.joinToString()}",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        } else {
            DiagnosticItem(
                id = "abi_arm64",
                code = "SYS-01",
                title = "ARM64 ABI Missing",
                detail = "Detected ABIs: ${systemInfo.supportedAbis.joinToString()}",
                remediation = "Run on an arm64-v8a Android device (Infinix Hot 50 4G / Helio G100).",
                severity = DiagnosticSeverity.CRITICAL
            )
        }

        // 2. Native JNI Bridge
        items += if (systemInfo.nativeBridgeLoaded) {
            DiagnosticItem(
                id = "jni_bridge",
                code = "NDK-01",
                title = "C++20 Native Runtime Bridge Active",
                detail = systemInfo.nativeBridgeVersion,
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        } else {
            DiagnosticItem(
                id = "jni_bridge",
                code = "NDK-01",
                title = "Native Bridge Running in Fallback Mode",
                detail = "${systemInfo.nativeBridgeVersion} — libstorm_runtime_bridge.so not loaded.",
                remediation = "Build APK with NDK/CMake enabled (-PenableNativeBridge=true) for POSIX fork/execve launch.",
                severity = DiagnosticSeverity.WARNING
            )
        }

        // 3. Game executable & PE inspection
        items += when {
            gameState.status == GameStatus.GAME_NOT_FOUND -> DiagnosticItem(
                id = "game_exe",
                code = "GAME-01",
                title = "NSUNSR.exe Not Imported",
                detail = "No valid PC game folder has been linked or imported.",
                remediation = "Open Game Files and select your Naruto STORM Revolution folder containing NSUNSR.exe.",
                severity = DiagnosticSeverity.CRITICAL
            )
            !gameState.peResult.isValidPe -> DiagnosticItem(
                id = "game_exe",
                code = "GAME-01",
                title = "PE32 Validation Failed",
                detail = gameState.validationErrors.firstOrNull() ?: gameState.peResult.summary,
                remediation = "Verify your NSUNSR.exe installation is intact and uncorrupted.",
                severity = DiagnosticSeverity.CRITICAL
            )
            else -> DiagnosticItem(
                id = "game_exe",
                code = "GAME-01",
                title = "NSUNSR.exe Verified (${gameState.peResult.architectureLabel})",
                detail = gameState.peResult.summary,
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        // 4. Data CPK integrity
        items += if (gameState.dataCpkFound) {
            DiagnosticItem(
                id = "game_cpk",
                code = "GAME-02",
                title = "Game Data Archive Structure Present",
                detail = "data_win32 / CPK assets detected (${gameState.fileCount} files scanned).",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        } else {
            DiagnosticItem(
                id = "game_cpk",
                code = "GAME-02",
                title = "Game Data Directory Incomplete",
                detail = "Expected data_win32/*.cpk archives alongside NSUNSR.exe were not found.",
                remediation = "Select the root NSUNSR installation directory, not just a single file.",
                severity = if (gameState.peResult.isValidPe) DiagnosticSeverity.WARNING else DiagnosticSeverity.CRITICAL
            )
        }

        // 5. 32-bit PE vs Box64/WoW64 Architecture check
        val wineOk = runtimeComponents[RuntimeComponentId.WINE]?.status == ComponentStatus.INSTALLED
        val wow64Ok = runtimeComponents[RuntimeComponentId.WOW64]?.status == ComponentStatus.INSTALLED
        val box86Ok = runtimeComponents[RuntimeComponentId.BOX86]?.status == ComponentStatus.INSTALLED
        val box64Ok = runtimeComponents[RuntimeComponentId.BOX64]?.status == ComponentStatus.INSTALLED

        items += if (config.architectureMode == RuntimeArchitectureMode.WINE_BOX64_ONLY && gameState.peResult.is32Bit) {
            DiagnosticItem(
                id = "arch_mismatch",
                code = "ARCH-01",
                title = "32-bit PE vs Pure Box64 Mismatch",
                detail = "NSUNSR.exe is 32-bit x86 (PE32) and cannot execute on pure 64-bit Box64 without WoW64 or Box86.",
                remediation = "Switch Architecture Mode to Wine WoW64 or Box86 + Wine32.",
                severity = DiagnosticSeverity.CRITICAL
            )
        } else {
            DiagnosticItem(
                id = "arch_mismatch",
                code = "ARCH-01",
                title = "Translation Mode Compatible",
                detail = "${config.architectureMode.label} supports 32-bit Windows PE execution.",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        // 6. Core Runtime Components (Wine, RootFS, Libs, Translator)
        val missingRuntime = buildList {
            if (!wineOk) add("Wine")
            if (runtimeComponents[RuntimeComponentId.ROOTFS]?.status != ComponentStatus.INSTALLED) add("ARM64 RootFS")
            if (runtimeComponents[RuntimeComponentId.RUNTIME_LIBS]?.status != ComponentStatus.INSTALLED) add("Core Libs")
            when (config.architectureMode) {
                RuntimeArchitectureMode.WINE_WOW64 -> {
                    if (!wow64Ok && !box64Ok) add("WoW64/Box64")
                }
                RuntimeArchitectureMode.WINE_BOX86_ARM32 -> {
                    if (!box86Ok) add("Box86")
                }
                RuntimeArchitectureMode.WINE_BOX64_ONLY -> {
                    if (!box64Ok) add("Box64")
                }
            }
        }
        items += if (missingRuntime.isEmpty()) {
            DiagnosticItem(
                id = "runtime_core",
                code = "RT-01",
                title = "Windows ARM64 Runtime Ready",
                detail = "All required Wine, RootFS, and translator ELF binaries verified.",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        } else {
            DiagnosticItem(
                id = "runtime_core",
                code = "RT-01",
                title = "Missing Verified Runtime Packages (${missingRuntime.size})",
                detail = "Missing: ${missingRuntime.joinToString(", ")}",
                remediation = "Open Runtime Manager to import or build missing ARM64 runtime archives.",
                severity = DiagnosticSeverity.CRITICAL
            )
        }

        // 7. Mali GPU & Turnip guard
        items += if (systemInfo.isMaliGpu && config.graphicsDriver == GraphicsDriverOption.ADRENO_TURNIP) {
            DiagnosticItem(
                id = "gpu_turnip_block",
                code = "GFX-01",
                title = "Turnip Blocked on Mali GPU",
                detail = "Detected GPU '${systemInfo.gpuRenderer}'. Adreno Turnip is incompatible with Mali-G57 MC2.",
                remediation = "Select System Mali Vulkan Driver or System Mali OpenGL ES Driver.",
                severity = DiagnosticSeverity.CRITICAL
            )
        } else {
            DiagnosticItem(
                id = "gpu_turnip_block",
                code = "GFX-01",
                title = "GPU Driver Compatible (${graphicsDecision.selectedDriver.label})",
                detail = "Renderer: ${systemInfo.gpuRenderer} • Effective backend: ${graphicsDecision.effectiveBackend.label}",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        // 8. DXVK vs Vulkan availability
        val dxvkOk = runtimeComponents[RuntimeComponentId.DXVK]?.status == ComponentStatus.INSTALLED
        items += if (config.graphicsBackend == GraphicsBackendMode.DXVK_VULKAN && (!systemInfo.vulkanSupported || !dxvkOk)) {
            DiagnosticItem(
                id = "dxvk_ready",
                code = "GFX-02",
                title = "DXVK Selected Without Full Vulkan/DLL Stack",
                detail = "Vulkan=${systemInfo.vulkanSupported}, DXVK d3d9.dll=$dxvkOk. Automatic fallback to WineD3D may be needed.",
                remediation = "Install DXVK package or switch Graphics Backend to Automatic / WineD3D OpenGL ES.",
                severity = DiagnosticSeverity.WARNING
            )
        } else {
            DiagnosticItem(
                id = "dxvk_ready",
                code = "GFX-02",
                title = "DirectX 9 Translation Path Ready",
                detail = "Active translation: ${graphicsDecision.effectiveBackend.label}",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        // 9. Wine Prefix Health
        items += if (prefixSummary != null && prefixSummary.exists) {
            DiagnosticItem(
                id = "wine_prefix",
                code = "WINE-01",
                title = "Wine Prefix '${prefixSummary.name}' Initialized",
                detail = "Windows Target: ${prefixSummary.windowsVersion} • Registry=${prefixSummary.hasSystemReg}",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        } else {
            DiagnosticItem(
                id = "wine_prefix",
                code = "WINE-01",
                title = "Wine Prefix Not Yet Created",
                detail = "Prefix '${config.winePrefixName}' will be initialized before first game launch.",
                remediation = "Create/Initialize prefix in Game Configuration → Prefix Manager.",
                severity = DiagnosticSeverity.WARNING
            )
        }

        // 10. RAM Headroom
        items += if (systemInfo.availableRamMb in 1..1799) {
            DiagnosticItem(
                id = "ram_headroom",
                code = "MEM-01",
                title = "Low Available RAM (${systemInfo.availableRamMb} MB)",
                detail = "Closing background apps is recommended to prevent Android LMK killing Wine/NSUNSR.",
                remediation = "Use 854×480 or 960×540 resolution and close background apps.",
                severity = DiagnosticSeverity.WARNING
            )
        } else {
            DiagnosticItem(
                id = "ram_headroom",
                code = "MEM-01",
                title = "Memory Headroom Healthy",
                detail = "${systemInfo.availableRamMb} MB free / ${systemInfo.totalRamMb} MB total.",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        // 11. Internal Storage Headroom
        val freeMb = availableStorageBytes / (1024L * 1024L)
        items += if (freeMb < 512L) {
            DiagnosticItem(
                id = "storage_free",
                code = "IO-01",
                title = "Low Internal Storage (${freeMb} MB)",
                detail = "Wine prefix, shader cache, and runtime archives require at least 512 MB free.",
                remediation = "Free device storage or keep large game files on SAF external folder.",
                severity = DiagnosticSeverity.WARNING
            )
        } else {
            DiagnosticItem(
                id = "storage_free",
                code = "IO-01",
                title = "Storage Space Sufficient (${freeMb} MB Free)",
                detail = "Sufficient space for Wine prefix and shader cache.",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        // 12. Thermal state
        items += if (systemInfo.thermalCelsius >= 45f) {
            DiagnosticItem(
                id = "thermal_state",
                code = "THERM-01",
                title = "Elevated SoC Temperature (${systemInfo.thermalCelsius.toInt()}°C)",
                detail = "Helio G100 may throttle GPU/CPU clocks above 45°C.",
                remediation = "Lock target FPS to 30 and avoid 1280×720 experimental preset.",
                severity = DiagnosticSeverity.WARNING
            )
        } else {
            DiagnosticItem(
                id = "thermal_state",
                code = "THERM-01",
                title = "Thermal Headroom Nominal",
                detail = if (systemInfo.thermalCelsius > 0f) "${systemInfo.thermalCelsius}°C" else "Sensor nominal / unavailable in container",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        // 13. Resolution Safety Check
        items += if (config.resolution.width >= 1280 && systemInfo.isMaliGpu) {
            DiagnosticItem(
                id = "res_warning",
                code = "PERF-01",
                title = "Experimental 720p Resolution on Mali-G57 MC2",
                detail = "1280×720 increases fill-rate demand on 2-core Mali-G57 GPU.",
                remediation = "Use 960×540 (default) or 854×480 for locked 30 FPS.",
                severity = DiagnosticSeverity.INFO
            )
        } else {
            DiagnosticItem(
                id = "res_warning",
                code = "PERF-01",
                title = "Resolution Tuned for Mali-G57 MC2",
                detail = "${config.resolution.label} @ ${config.targetFps.fps} FPS",
                remediation = "None required.",
                severity = DiagnosticSeverity.PASS
            )
        }

        val critical = items.count { it.severity == DiagnosticSeverity.CRITICAL }
        val warning = items.count { it.severity == DiagnosticSeverity.WARNING }
        val pass = items.count { it.severity == DiagnosticSeverity.PASS }

        return DiagnosticReport(
            timestampMs = System.currentTimeMillis(),
            items = items,
            criticalCount = critical,
            warningCount = warning,
            passCount = pass,
            canLaunch = critical == 0
        )
    }

    fun sanitizeLogTextForExport(rawText: String): String {
        return rawText
            .replace(Regex("/data/user/\\d+/[^/\\s]+"), "/data/user/0/<app_pkg>")
            .replace(Regex("content://[^\\s]+"), "content://<saf_tree_uri>")
            .replace(Regex("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})"), "<redacted_email>")
    }
}
