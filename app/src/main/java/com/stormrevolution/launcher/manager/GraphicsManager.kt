package com.stormrevolution.launcher.manager

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.DeviceSystemInfo
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GraphicsBackendMode
import com.stormrevolution.launcher.model.GraphicsDriverOption
import com.stormrevolution.launcher.model.GraphicsRuntimeDecision
import com.stormrevolution.launcher.model.RuntimeComponent
import com.stormrevolution.launcher.model.RuntimeComponentId

class GraphicsManager(private val context: Context) {

    fun collectDeviceSystemInfo(): DeviceSystemInfo {
        val eglInfo = queryEglRendererInfo()
        val pm = context.packageManager
        val features = runCatching { pm?.systemAvailableFeatures }.getOrNull()
        val vulkanFeature = features?.firstOrNull {
            it?.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
        }
        val vulkanLevelFeature = features?.firstOrNull {
            it?.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL
        }

        val nativeVulkanNode = NativeBridge.checkVulkanDriverNode()
        val vulkanSupported = vulkanFeature != null || vulkanLevelFeature != null || nativeVulkanNode
        val vulkanVersionStr = when {
            vulkanFeature != null -> {
                val ver = vulkanFeature.version
                val major = (ver shr 22) and 0x3FF
                val minor = (ver shr 12) and 0x3FF
                val patch = ver and 0xFFF
                "Vulkan $major.$minor.$patch"
            }
            nativeVulkanNode -> "Vulkan 1.x (Hardware Node Detected)"
            else -> "Unavailable"
        }

        val rendererLower = eglInfo.renderer.lowercase()
        val vendorLower = eglInfo.vendor.lowercase()
        val boardLower = (Build.HARDWARE + " " + Build.BOARD + " " + if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "").lowercase()

        val isMali = rendererLower.contains("mali") || vendorLower.contains("arm")
        val isAdreno = rendererLower.contains("adreno") || vendorLower.contains("qualcomm")
        val isHelio = boardLower.contains("mt6789") ||
            boardLower.contains("helio") ||
            boardLower.contains("g99") ||
            boardLower.contains("g100") ||
            Build.MODEL.lowercase().contains("hot 50")

        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(memInfo)

        val procMem = NativeBridge.readProcMemoryKb()
        val totalRamMb = if (procMem.first > 0L) procMem.first / 1024L else memInfo.totalMem / (1024L * 1024L)
        val availRamMb = if (procMem.second > 0L) procMem.second / 1024L else memInfo.availMem / (1024L * 1024L)

        val socLabel = buildString {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL.isNotBlank()) {
                append(Build.SOC_MODEL)
            } else {
                append(Build.HARDWARE)
            }
            if (isHelio && !toString().contains("G100", ignoreCase = true)) {
                append(" (Helio G99/G100 Class)")
            }
        }

        return DeviceSystemInfo(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            socName = socLabel,
            gpuRenderer = eglInfo.renderer,
            gpuVendor = eglInfo.vendor,
            gpuDriverVersion = eglInfo.version,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            cpuCores = NativeBridge.readCpuCount(),
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: listOf("arm64-v8a"),
            androidVersion = Build.VERSION.RELEASE ?: "14",
            sdkInt = Build.VERSION.SDK_INT,
            vulkanSupported = vulkanSupported,
            vulkanVersionString = vulkanVersionStr,
            glesVersionString = eglInfo.version,
            isMaliGpu = isMali,
            isAdrenoGpu = isAdreno,
            isHelioG100Target = isHelio,
            thermalCelsius = NativeBridge.readThermalCelsius(),
            nativeBridgeLoaded = NativeBridge.isNativeLoaded,
            nativeBridgeVersion = NativeBridge.getBridgeVersion()
        )
    }

    fun resolveGraphicsDecision(
        config: GameConfiguration,
        systemInfo: DeviceSystemInfo,
        runtimeComponents: Map<RuntimeComponentId, RuntimeComponent>
    ): GraphicsRuntimeDecision {
        var selectedDriver = config.graphicsDriver
        var blockedReason: String? = null

        // Enforce Mali guard: never allow Adreno Turnip on Mali or non-Adreno GPUs
        if (selectedDriver == GraphicsDriverOption.ADRENO_TURNIP && !systemInfo.isAdrenoGpu) {
            selectedDriver = if (systemInfo.vulkanSupported) {
                GraphicsDriverOption.SYSTEM_MALI_VULKAN
            } else {
                GraphicsDriverOption.SYSTEM_GLES
            }
            blockedReason = "Turnip driver blocked: detected GPU '${systemInfo.gpuRenderer}' is not Adreno."
        }

        val dxvkReady = runtimeComponents[RuntimeComponentId.DXVK]?.status == ComponentStatus.INSTALLED
        val effectiveBackend: GraphicsBackendMode
        val fallbackActive: Boolean

        when (config.graphicsBackend) {
            GraphicsBackendMode.AUTOMATIC -> {
                if (systemInfo.vulkanSupported && dxvkReady && selectedDriver != GraphicsDriverOption.SYSTEM_GLES) {
                    effectiveBackend = GraphicsBackendMode.DXVK_VULKAN
                    fallbackActive = false
                } else {
                    effectiveBackend = GraphicsBackendMode.WINED3D_OPENGL
                    fallbackActive = true
                }
            }
            GraphicsBackendMode.DXVK_VULKAN -> {
                if (systemInfo.vulkanSupported && dxvkReady) {
                    effectiveBackend = GraphicsBackendMode.DXVK_VULKAN
                    fallbackActive = false
                } else {
                    effectiveBackend = GraphicsBackendMode.WINED3D_OPENGL
                    fallbackActive = true
                    if (blockedReason == null) {
                        blockedReason = "DXVK requested, falling back to WineD3D OpenGL ES because Vulkan/DXVK DLLs are incomplete."
                    }
                }
            }
            GraphicsBackendMode.WINED3D_OPENGL -> {
                effectiveBackend = GraphicsBackendMode.WINED3D_OPENGL
                fallbackActive = false
            }
        }

        val env = linkedMapOf<String, String>()
        env["STORM_RES_WIDTH"] = config.resolution.width.toString()
        env["STORM_RES_HEIGHT"] = config.resolution.height.toString()
        env["STORM_TARGET_FPS"] = config.targetFps.fps.toString()
        env["MESA_GL_VERSION_OVERRIDE"] = "3.2"
        env["vblank_mode"] = if (config.vsyncEnabled) "1" else "0"

        if (effectiveBackend == GraphicsBackendMode.DXVK_VULKAN) {
            env["WINEDLLOVERRIDES"] = "d3d9=n,b;dxgi=n,b"
            env["DXVK_ASYNC"] = if (config.dxvkAsyncShaders) "1" else "0"
            env["DXVK_FRAME_RATE"] = config.targetFps.fps.toString()
            env["DXVK_HUD"] = if (config.dxvkHudEnabled) "fps,devinfo,memory" else "0"
            env["DXVK_CONFIG_FILE"] = "dxvk_mali.conf"
            // Mali-G57 MC2 Vulkan guard: disable Turnip-only flags
            env["TU_DEBUG"] = ""
        } else {
            env["WINEDLLOVERRIDES"] = "d3d9=b"
            env["WINE_D3D_CONFIG"] = "renderer=gl;csmt=${if (config.csmtEnabled) 1 else 0};VideoMemorySize=2048"
        }

        return GraphicsRuntimeDecision(
            requestedBackend = config.graphicsBackend,
            effectiveBackend = effectiveBackend,
            selectedDriver = selectedDriver,
            fallbackActive = fallbackActive,
            blockedReason = blockedReason,
            envVars = env
        )
    }

    private data class EglRendererInfo(
        val renderer: String,
        val vendor: String,
        val version: String
    )

    private fun queryEglRendererInfo(): EglRendererInfo {
        var display: EGLDisplay? = null
        var contextEgl: EGLContext? = null
        var surface: EGLSurface? = null
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) {
                return EglRendererInfo("OpenGL ES Renderer (Headless)", "Unknown", "OpenGL ES 3.0")
            }
            val versionArr = IntArray(2)
            if (!EGL14.eglInitialize(display, versionArr, 0, versionArr, 1)) {
                return EglRendererInfo("OpenGL ES Renderer (No EGL)", "Unknown", "OpenGL ES 3.0")
            }
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)
            val cfg = configs[0]
            if (cfg == null) {
                return EglRendererInfo("OpenGL ES Renderer", "Unknown", "OpenGL ES 3.0")
            }
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            contextEgl = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, cfg, surfAttribs, 0)
            if (contextEgl != null && surface != null &&
                contextEgl != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE &&
                EGL14.eglMakeCurrent(display, surface, surface, contextEgl)
            ) {
                val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Mali/OpenGL ES"
                val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "ARM"
                val ver = GLES20.glGetString(GLES20.GL_VERSION) ?: "OpenGL ES 3.2"
                EglRendererInfo(renderer, vendor, ver)
            } else {
                EglRendererInfo("OpenGL ES 3.x", "Unknown", "OpenGL ES 3.0")
            }
        } catch (_: Throwable) {
            EglRendererInfo("OpenGL ES (Host Stub)", "ARM/System", "OpenGL ES 3.0")
        } finally {
            runCatching {
                if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                    val noSurface = EGL14.EGL_NO_SURFACE
                    val noContext = EGL14.EGL_NO_CONTEXT
                    if (noSurface != null && noContext != null) {
                        EGL14.eglMakeCurrent(display, noSurface, noSurface, noContext)
                    }
                    if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(display, surface)
                    }
                    if (contextEgl != null && contextEgl != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(display, contextEgl)
                    }
                    EGL14.eglTerminate(display)
                }
            }
        }
    }
}
