package com.stormrevolution.launcher.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameStatus(val label: String) {
    GAME_NOT_FOUND("Game Not Found"),
    GAME_FOUND("Game Found"),
    GAME_VALIDATION_FAILED("Game Validation Failed"),
    GAME_READY("Game Ready"),
    RUNTIME_MISSING("Runtime Missing"),
    READY_TO_LAUNCH("Ready to Launch")
}

@Serializable
enum class ComponentStatus(val label: String) {
    NOT_INSTALLED("Not Installed"),
    DOWNLOADING("Importing / Installing"),
    VERIFYING("Verifying"),
    INSTALLED("Installed & Verified"),
    CORRUPTED("Corrupted"),
    INCOMPATIBLE("Incompatible")
}

@Serializable
enum class RuntimeComponentId(
    val displayName: String,
    val expectedBinary: String,
    val relativeSubdir: String
) {
    WINE("Wine WoW64 / ARM64", "wine", "wine/bin"),
    BOX86("Box86 (x86 Translator)", "box86", "box86/bin"),
    BOX64("Box64 (x86_64 Translator)", "box64", "box64/bin"),
    WOW64("Wine WoW64 Layer", "wow64.dll", "wine/lib/wine/x86_64-windows"),
    DXVK("DXVK (D3D9 -> Vulkan)", "d3d9.dll", "dxvk/x32"),
    VULKAN_BACKEND("Mali Vulkan ICD / Loader", "libvulkan_wrapper.so", "graphics"),
    GRAPHICS_DRIVER("Mali-G57 Graphics Env", "mali_env.sh", "graphics"),
    AUDIO_BACKEND("PulseAudio / ALSA Bridge", "pulseaudio", "audio/bin"),
    ROOTFS("ARM64 Linux RootFS", "busybox", "rootfs/bin"),
    RUNTIME_LIBS("Core Shared Libs", "libstdc++.so.6", "libs/arm64")
}

@Serializable
data class RuntimeComponent(
    val id: RuntimeComponentId,
    val status: ComponentStatus = ComponentStatus.NOT_INSTALLED,
    val version: String = "—",
    val filePath: String = "",
    val fileSizeBytes: Long = 0L,
    val expectedArch: String = "ARM64 ELF",
    val sha256Short: String = "—",
    val detailMessage: String = "Component package has not been imported yet."
)

@Serializable
enum class RuntimeArchitectureMode(val label: String, val description: String) {
    WINE_WOW64(
        "Wine WoW64 (Recommended)",
        "Runs 32-bit NSUNSR.exe via Wine i386-windows + ARM64/x86_64 WoW64 host."
    ),
    WINE_BOX86_ARM32(
        "Box86 + Wine32 (Fallback)",
        "Translates x86 instructions via Box86 when arm32 runtime libs are provisioned."
    ),
    WINE_BOX64_ONLY(
        "Box64 Pure 64-bit (Blocked for PE32)",
        "Cannot run 32-bit NSUNSR.exe directly without WoW64 or Box86."
    )
}

@Serializable
enum class ResolutionOption(val width: Int, val height: Int, val label: String) {
    R854X480(854, 480, "854×480 (Performance)"),
    R960X540(960, 540, "960×540 (Helio G100 Default)"),
    R1024X576(1024, 576, "1024×576 (Balanced Experimental)"),
    R1280X720(1280, 720, "1280×720 (HD Experimental)")
}

@Serializable
enum class TargetFpsOption(val fps: Int, val label: String) {
    FPS_30(30, "30 FPS (Thermal Target)"),
    FPS_45(45, "45 FPS (Unlocked Burst)"),
    FPS_60(60, "60 FPS (Experimental)")
}

@Serializable
enum class GraphicsBackendMode(val label: String) {
    AUTOMATIC("Automatic (Mali Safe)"),
    DXVK_VULKAN("DXVK (DirectX 9 → Vulkan)"),
    WINED3D_OPENGL("WineD3D (DirectX 9 → OpenGL ES)")
}

@Serializable
enum class GraphicsDriverOption(val label: String, val isMaliCompatible: Boolean) {
    SYSTEM_MALI_VULKAN("System Mali Vulkan Driver", true),
    SYSTEM_GLES("System Mali OpenGL ES Driver", true),
    CUSTOM_MALI_WRAPPER("Custom Mali Vulkan Wrapper", true),
    ADRENO_TURNIP("Adreno Turnip (Incompatible on Mali)", false)
}

@Serializable
enum class AudioBackendMode(val label: String) {
    PULSEAUDIO("PulseAudio (Low Latency)"),
    ALSA("ALSA Direct"),
    DISABLED("Muted / Disabled")
}

@Serializable
enum class PerformancePreset(val label: String, val summary: String) {
    LOW("Low (854×480 • 30 FPS • WineD3D Safe)", "Maximizes thermal stability and RAM headroom."),
    BALANCED("Balanced (960×540 • 30 FPS • Helio G100)", "Tuned default for Infinix Hot 50 4G & Mali-G57 MC2."),
    HIGH("High (1280×720 • 45 FPS • DXVK)", "Experimental quality mode; higher GPU load."),
    CUSTOM("Custom", "User-customized runtime and graphics parameters.")
}

@Serializable
enum class LaunchProfile(val label: String) {
    NSUNSR_OPTIMIZED("NSUNSR_OPTIMIZED"),
    COMPATIBILITY_SAFE("COMPATIBILITY_SAFE"),
    EXPERIMENTAL_HD("EXPERIMENTAL_HD")
}

@Serializable
data class GameConfiguration(
    val launchProfile: LaunchProfile = LaunchProfile.NSUNSR_OPTIMIZED,
    val performancePreset: PerformancePreset = PerformancePreset.BALANCED,
    val architectureMode: RuntimeArchitectureMode = RuntimeArchitectureMode.WINE_WOW64,
    val resolution: ResolutionOption = ResolutionOption.R960X540,
    val targetFps: TargetFpsOption = TargetFpsOption.FPS_30,
    val graphicsBackend: GraphicsBackendMode = GraphicsBackendMode.AUTOMATIC,
    val graphicsDriver: GraphicsDriverOption = GraphicsDriverOption.SYSTEM_MALI_VULKAN,
    val audioBackend: AudioBackendMode = AudioBackendMode.PULSEAUDIO,
    val audioSampleRateHz: Int = 48000,
    val audioBufferMs: Int = 64,
    val vsyncEnabled: Boolean = false,
    val dxvkAsyncShaders: Boolean = true,
    val dxvkHudEnabled: Boolean = false,
    val csmtEnabled: Boolean = true,
    val esyncEnabled: Boolean = true,
    val box64DynarecFastNan: Boolean = true,
    val box64DynarecBigBlock: Int = 1,
    val box64DynarecStrongMem: Int = 1,
    val winePrefixName: String = "nsunsr_default",
    val cpuAffinityMask: String = "0xFF",
    val memoryCeilingMb: Int = 4096,
    val logVerboseWine: Boolean = false
)

@Serializable
data class PeInspectionResult(
    val isValidPe: Boolean = false,
    val machine: Int = 0,
    val architectureLabel: String = "Unknown",
    val is32Bit: Boolean = false,
    val is64Bit: Boolean = false,
    val subsystem: Int = 0,
    val sectionCount: Int = 0,
    val entryPointRva: Long = 0L,
    val imageBase: Long = 0L,
    val sizeOfImage: Long = 0L,
    val summary: String = "Not inspected"
)

@Serializable
data class GameLibraryState(
    val status: GameStatus = GameStatus.GAME_NOT_FOUND,
    val rootTreeUri: String = "",
    val rootDisplayPath: String = "Not selected",
    val exeFileName: String = "NSUNSR.exe",
    val exeSizeBytes: Long = 0L,
    val dataCpkFound: Boolean = false,
    val dataCpkSizeBytes: Long = 0L,
    val configIniFound: Boolean = false,
    val totalDirectorySizeBytes: Long = 0L,
    val fileCount: Int = 0,
    val peResult: PeInspectionResult = PeInspectionResult(),
    val copiedPrivateExePath: String = "",
    val isPrivateCopy: Boolean = false,
    val validationErrors: List<String> = emptyList(),
    val lastScannedAtMs: Long = 0L
)

@Serializable
data class DeviceSystemInfo(
    val deviceModel: String = "Unknown",
    val manufacturer: String = "Unknown",
    val socName: String = "Unknown",
    val gpuRenderer: String = "Unknown",
    val gpuVendor: String = "Unknown",
    val gpuDriverVersion: String = "Unknown",
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val cpuCores: Int = 0,
    val supportedAbis: List<String> = emptyList(),
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val vulkanSupported: Boolean = false,
    val vulkanVersionString: String = "Unavailable",
    val glesVersionString: String = "3.0",
    val isMaliGpu: Boolean = false,
    val isAdrenoGpu: Boolean = false,
    val isHelioG100Target: Boolean = false,
    val thermalCelsius: Float = -1f,
    val nativeBridgeLoaded: Boolean = false,
    val nativeBridgeVersion: String = "JVM POSIX Fallback"
)

@Serializable
data class GraphicsRuntimeDecision(
    val requestedBackend: GraphicsBackendMode = GraphicsBackendMode.AUTOMATIC,
    val effectiveBackend: GraphicsBackendMode = GraphicsBackendMode.WINED3D_OPENGL,
    val selectedDriver: GraphicsDriverOption = GraphicsDriverOption.SYSTEM_MALI_VULKAN,
    val fallbackActive: Boolean = false,
    val blockedReason: String? = null,
    val envVars: Map<String, String> = emptyMap()
)

@Serializable
enum class DiagnosticSeverity {
    PASS,
    INFO,
    WARNING,
    CRITICAL
}

@Serializable
data class DiagnosticItem(
    val id: String,
    val code: String,
    val title: String,
    val detail: String,
    val remediation: String,
    val severity: DiagnosticSeverity
)

@Serializable
data class DiagnosticReport(
    val timestampMs: Long = 0L,
    val items: List<DiagnosticItem> = emptyList(),
    val criticalCount: Int = 0,
    val warningCount: Int = 0,
    val passCount: Int = 0,
    val canLaunch: Boolean = false
)

@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

@Serializable
enum class LogSource {
    LAUNCHER,
    RUNTIME,
    WINE,
    DXVK,
    INPUT,
    DIAGNOSTIC
}

@Serializable
data class LogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: LogLevel,
    val source: LogSource,
    val message: String
)

@Serializable
enum class ProcessState(val label: String) {
    IDLE("Idle"),
    PREPARING_PREFIX("Preparing Wine Prefix"),
    LAUNCHING("Starting Compatibility Layer"),
    RUNNING("Running"),
    STOPPING("Stopping"),
    EXITED("Exited"),
    FAILED("Launch Failed")
}

@Serializable
data class LaunchSessionState(
    val state: ProcessState = ProcessState.IDLE,
    val pid: Int = -1,
    val exitCode: Int? = null,
    val statusDetail: String = "Ready when verified.",
    val activeCommandLine: String = "",
    val activeLogFilePath: String = "",
    val startedAtMs: Long = 0L,
    val uptimeSeconds: Long = 0L,
    val rssMemoryMb: Long = 0L
)

@Serializable
data class VirtualControlElement(
    val id: String,
    val label: String,
    val xFraction: Float,
    val yFraction: Float,
    val scale: Float = 1.0f,
    val xInputBit: Int = 0
)

@Serializable
data class ControlLayoutConfig(
    val opacity: Float = 0.72f,
    val deadzone: Float = 0.15f,
    val hapticFeedback: Boolean = true,
    val showVirtualOverlay: Boolean = true,
    val autoHideOnPhysicalGamepad: Boolean = true,
    val profileName: String = "Storm Arena Standard",
    val elements: List<VirtualControlElement> = defaultStormControlElements()
)

fun defaultStormControlElements(): List<VirtualControlElement> = listOf(
    VirtualControlElement("l_stick", "MOVE", 0.14f, 0.68f, 1.15f, 0),
    VirtualControlElement("dpad", "NINJA TOOLS", 0.13f, 0.30f, 0.95f, 0),
    VirtualControlElement("btn_y", "Y • CHAKRA", 0.79f, 0.48f, 1.0f, 0x8000),
    VirtualControlElement("btn_x", "X • SHURIKEN", 0.72f, 0.64f, 1.0f, 0x4000),
    VirtualControlElement("btn_b", "B • MELEE", 0.87f, 0.64f, 1.05f, 0x2000),
    VirtualControlElement("btn_a", "A • JUMP", 0.79f, 0.80f, 1.05f, 0x1000),
    VirtualControlElement("btn_lt", "LT • GUARD", 0.08f, 0.12f, 0.9f, 0),
    VirtualControlElement("btn_rt", "RT • SUB/DASH", 0.92f, 0.12f, 0.9f, 0),
    VirtualControlElement("btn_lb", "LB • SUP 1", 0.21f, 0.12f, 0.85f, 0x0100),
    VirtualControlElement("btn_rb", "RB • SUP 2", 0.79f, 0.12f, 0.85f, 0x0200),
    VirtualControlElement("btn_start", "START", 0.56f, 0.10f, 0.75f, 0x0010),
    VirtualControlElement("btn_back", "BACK", 0.44f, 0.10f, 0.75f, 0x0020)
)

@Serializable
data class GamepadDeviceInfo(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int
)

@Serializable
data class ControllerState(
    val buttonsMask: Int = 0,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val thumbLX: Short = 0,
    val thumbLY: Short = 0,
    val thumbRX: Short = 0,
    val thumbRY: Short = 0,
    val connectedGamepads: List<GamepadDeviceInfo> = emptyList(),
    val ipcSocketConnected: Boolean = false
)

@Serializable
data class PerformanceSnapshot(
    val estimatedFps: Int = 0,
    val targetFps: Int = 30,
    val totalRamMb: Long = 0L,
    val freeRamMb: Long = 0L,
    val processRssMb: Long = 0L,
    val thermalCelsius: Float = -1f,
    val thermalThrottleWarning: Boolean = false
)

@Serializable
data class WinePrefixSummary(
    val name: String,
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val windowsVersion: String,
    val hasSystemReg: Boolean,
    val hasUserReg: Boolean
)
