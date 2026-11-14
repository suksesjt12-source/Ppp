package com.stormrevolution.launcher.manager

import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GraphicsRuntimeDecision
import com.stormrevolution.launcher.model.RuntimeArchitectureMode
import com.stormrevolution.launcher.model.WinePrefixSummary
import java.io.File

class WineManager(
    private val storageManager: StorageManager
) {

    fun listPrefixes(): List<WinePrefixSummary> {
        val dir = storageManager.winePrefixesDir
        val subdirs = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        return subdirs.map { prefixDir ->
            val sysReg = File(prefixDir, "system.reg").exists()
            val userReg = File(prefixDir, "user.reg").exists()
            WinePrefixSummary(
                name = prefixDir.name,
                path = prefixDir.absolutePath,
                exists = true,
                sizeBytes = storageManager.calculateDirectorySize(prefixDir),
                windowsVersion = readWindowsVersionFromReg(File(prefixDir, "system.reg")),
                hasSystemReg = sysReg,
                hasUserReg = userReg
            )
        }.sortedBy { it.name }
    }

    fun inspectPrefix(prefixName: String): WinePrefixSummary {
        val prefixDir = storageManager.getPrefixDir(prefixName)
        if (!prefixDir.exists()) {
            return WinePrefixSummary(
                name = prefixName,
                path = prefixDir.absolutePath,
                exists = false,
                sizeBytes = 0L,
                windowsVersion = "win10",
                hasSystemReg = false,
                hasUserReg = false
            )
        }
        val sysReg = File(prefixDir, "system.reg")
        val userReg = File(prefixDir, "user.reg")
        return WinePrefixSummary(
            name = prefixName,
            path = prefixDir.absolutePath,
            exists = true,
            sizeBytes = storageManager.calculateDirectorySize(prefixDir),
            windowsVersion = readWindowsVersionFromReg(sysReg),
            hasSystemReg = sysReg.exists(),
            hasUserReg = userReg.exists()
        )
    }

    fun initializePrefixStructure(prefixName: String, config: GameConfiguration): WinePrefixSummary {
        val prefixDir = storageManager.getPrefixDir(prefixName)
        val driveC = File(prefixDir, "drive_c")
        val windowsSys32 = File(driveC, "windows/system32")
        val windowsSysWow = File(driveC, "windows/syswow64")
        val userAppData = File(driveC, "users/storm/AppData/Roaming")
        val dxvkCacheDir = File(prefixDir, "dxvk_cache")

        windowsSys32.mkdirs()
        windowsSysWow.mkdirs()
        userAppData.mkdirs()
        dxvkCacheDir.mkdirs()

        // Generate NSUNSR-tuned Direct3D / Wine registry overrides
        val userRegFile = File(prefixDir, "user.reg")
        if (!userRegFile.exists()) {
            userRegFile.writeText(buildDefaultUserReg(config))
        }

        val sysRegFile = File(prefixDir, "system.reg")
        if (!sysRegFile.exists()) {
            sysRegFile.writeText(buildDefaultSystemReg())
        }

        val dxvkConf = File(prefixDir, "dxvk_mali.conf")
        dxvkConf.writeText(
            """
            # Auto-generated Mali-G57 MC2 tuned DXVK config for NSUNSR.exe
            d3d9.maxFrameLatency = 1
            d3d9.presentInterval = ${if (config.vsyncEnabled) 1 else 0}
            d3d9.floatEmulation = Auto
            d3d9.deferSurfaceCreation = True
            dxvk.numCompilerThreads = 2
            dxvk.enableAsync = ${if (config.dxvkAsyncShaders) "True" else "False"}
            """.trimIndent() + "\n"
        )

        return inspectPrefix(prefixName)
    }

    fun resetPrefix(prefixName: String, config: GameConfiguration): WinePrefixSummary {
        val prefixDir = storageManager.getPrefixDir(prefixName)
        if (prefixDir.exists()) {
            prefixDir.deleteRecursively()
        }
        return initializePrefixStructure(prefixName, config)
    }

    fun buildRuntimeEnvironment(
        config: GameConfiguration,
        graphicsDecision: GraphicsRuntimeDecision,
        audioEnv: Map<String, String>
    ): Map<String, String> {
        val prefixDir = storageManager.getPrefixDir(config.winePrefixName)
        val runtimeRoot = storageManager.runtimeRootDir
        val libsDir = File(runtimeRoot, "libs/arm64")
        val wineBinDir = File(runtimeRoot, "wine/bin")
        val box64BinDir = File(runtimeRoot, "box64/bin")
        val box86BinDir = File(runtimeRoot, "box86/bin")
        val rootfsDir = File(runtimeRoot, "rootfs")

        val env = linkedMapOf<String, String>()
        env["HOME"] = prefixDir.absolutePath
        env["TMPDIR"] = storageManager.stagingDir.absolutePath
        env["WINEPREFIX"] = prefixDir.absolutePath
        env["WINEARCH"] = when (config.architectureMode) {
            RuntimeArchitectureMode.WINE_BOX86_ARM32 -> "win32"
            else -> "win64"
        }
        env["WINEDEBUG"] = if (config.logVerboseWine) "+err,+warn,+d3d,+seh" else "-all,err+all"
        env["WINEESYNC"] = if (config.esyncEnabled) "1" else "0"
        env["WINEFSYNC"] = "0"
        env["WINE_LARGE_ADDRESS_AWARE"] = "1"
        env["PATH"] = "${wineBinDir.absolutePath}:${box64BinDir.absolutePath}:${box86BinDir.absolutePath}:${rootfsDir.absolutePath}/bin:/system/bin"
        env["LD_LIBRARY_PATH"] = "${libsDir.absolutePath}:${runtimeRoot.absolutePath}/wine/lib:${runtimeRoot.absolutePath}/graphics"
        env["STORM_XINPUT_SOCKET"] = File(prefixDir, "storm_xinput.sock").absolutePath

        // Box64 / Box86 Dynarec settings tuned for Helio G100 (Cortex-A76 + A55)
        env["BOX64_DYNAREC"] = "1"
        env["BOX64_DYNAREC_FASTNAN"] = if (config.box64DynarecFastNan) "1" else "0"
        env["BOX64_DYNAREC_BIGBLOCK"] = config.box64DynarecBigBlock.toString()
        env["BOX64_DYNAREC_STRONGMEM"] = config.box64DynarecStrongMem.toString()
        env["BOX64_DYNAREC_SAFEFLAGS"] = "1"
        env["BOX64_DYNAREC_WAIT"] = "1"
        env["BOX86_DYNAREC"] = "1"
        env["BOX86_DYNAREC_FASTNAN"] = if (config.box64DynarecFastNan) "1" else "0"

        env.putAll(graphicsDecision.envVars)
        env.putAll(audioEnv)
        return env
    }

    private fun readWindowsVersionFromReg(systemReg: File): String {
        if (!systemReg.exists()) return "win10"
        val text = runCatching { systemReg.readText() }.getOrDefault("")
        return when {
            text.contains("Windows 7", ignoreCase = true) -> "win7"
            text.contains("Windows 10", ignoreCase = true) -> "win10"
            else -> "win10"
        }
    }

    private fun buildDefaultSystemReg(): String = """
        WINE REGISTRY Version 2
        ;; Storm Launcher System Prefix Registry

        [Software\\Microsoft\\Windows NT\\CurrentVersion]
        "CurrentBuild"="19045"
        "ProductName"="Microsoft Windows 10"
    """.trimIndent() + "\n"

    private fun buildDefaultUserReg(config: GameConfiguration): String = """
        WINE REGISTRY Version 2
        ;; Storm Launcher User Prefix Registry (${config.winePrefixName})

        [Software\\Wine\\Direct3D]
        "csmt"=dword:${if (config.csmtEnabled) "00000001" else "00000000"}
        "VideoMemorySize"="2048"
        "DirectDrawRenderer"="opengl"
        "OffscreenRenderingMode"="fbo"
        "AlwaysOffscreen"="enabled"

        [Software\\Wine\\DirectInput]
        "MouseWarpOverride"="disable"
    """.trimIndent() + "\n"
}
