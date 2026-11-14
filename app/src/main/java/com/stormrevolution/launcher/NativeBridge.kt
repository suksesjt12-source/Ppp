package com.stormrevolution.launcher

import android.os.Build
import java.io.File
import java.io.RandomAccessFile

data class ElfVerificationResult(
    val isValidElf: Boolean,
    val elfClass: Int,
    val machine: Int,
    val isExecutable: Boolean
) {
    val archDescription: String
        get() = when {
            !isValidElf -> "Invalid / Non-ELF"
            elfClass == 2 && machine == 0xB7 -> "ELF64 ARM64 (AArch64)"
            elfClass == 1 && machine == 0x28 -> "ELF32 ARM (armeabi-v7a)"
            elfClass == 2 && machine == 0x3E -> "ELF64 x86_64"
            elfClass == 1 && machine == 0x03 -> "ELF32 x86"
            else -> "ELF class=$elfClass machine=0x${machine.toString(16)}"
        }
}

object NativeBridge {

    val isNativeLoaded: Boolean by lazy {
        try {
            System.loadLibrary("storm_runtime_bridge")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private external fun nativeGetBridgeVersion(): String
    private external fun nativeVerifyElfBinary(path: String): IntArray
    private external fun nativeReadProcMemoryStats(): LongArray
    private external fun nativeReadThermalCelsius(): Float
    private external fun nativeReadCpuCount(): Int
    private external fun nativeCheckVulkanDriverNode(): Boolean

    private external fun nativeSpawnProcess(
        executable: String,
        argv: Array<String>,
        envp: Array<String>,
        workingDir: String,
        logFilePath: String
    ): Int

    private external fun nativePollProcessStatus(pid: Int): IntArray
    private external fun nativeTerminateProcess(pid: Int, forceKill: Boolean): Boolean
    private external fun nativeReadProcessRssKb(pid: Int): Long

    private external fun nativeOpenInputSocket(socketPath: String): Int
    private external fun nativeSendXInputState(
        fd: Int,
        socketPath: String,
        buttonsMask: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        thumbLX: Short,
        thumbLY: Short,
        thumbRX: Short,
        thumbRY: Short,
        sequence: Int
    ): Boolean

    private external fun nativeCloseInputSocket(fd: Int)

    fun getBridgeVersion(): String {
        return if (isNativeLoaded) {
            runCatching { nativeGetBridgeVersion() }.getOrDefault("storm_runtime_bridge/unknown")
        } else {
            "JVM-POSIX-Fallback (${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"})"
        }
    }

    fun verifyElfBinary(file: File): ElfVerificationResult {
        if (!file.exists() || !file.isFile) {
            return ElfVerificationResult(false, 0, 0, false)
        }
        if (isNativeLoaded) {
            val raw = runCatching { nativeVerifyElfBinary(file.absolutePath) }.getOrNull()
            if (raw != null && raw.size >= 4) {
                return ElfVerificationResult(
                    isValidElf = raw[0] == 1,
                    elfClass = raw[1],
                    machine = raw[2],
                    isExecutable = raw[3] == 1
                )
            }
        }
        return verifyElfFallback(file)
    }

    private fun verifyElfFallback(file: File): ElfVerificationResult {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 20) return@use ElfVerificationResult(false, 0, 0, file.canExecute())
                val header = ByteArray(20)
                raf.readFully(header)
                val isElf = header[0] == 0x7F.toByte() &&
                    header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
                if (!isElf) return@use ElfVerificationResult(false, 0, 0, file.canExecute())
                val elfClass = header[4].toInt() and 0xFF
                val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
                ElfVerificationResult(true, elfClass, machine, file.canExecute())
            }
        }.getOrDefault(ElfVerificationResult(false, 0, 0, false))
    }

    fun readProcMemoryKb(): Triple<Long, Long, Long> {
        if (isNativeLoaded) {
            val arr = runCatching { nativeReadProcMemoryStats() }.getOrNull()
            if (arr != null && arr.size >= 3 && arr[0] > 0L) {
                return Triple(arr[0], arr[1], arr[2])
            }
        }
        return readProcMemInfoFallback()
    }

    private fun readProcMemInfoFallback(): Triple<Long, Long, Long> {
        return runCatching {
            val file = File("/proc/meminfo")
            if (!file.exists()) return@runCatching Triple(0L, 0L, 0L)
            var total = 0L
            var avail = 0L
            var free = 0L
            file.forEachLine { line ->
                when {
                    line.startsWith("MemTotal:") -> total = extractKbValue(line)
                    line.startsWith("MemAvailable:") -> avail = extractKbValue(line)
                    line.startsWith("MemFree:") -> free = extractKbValue(line)
                }
            }
            Triple(total, avail, free)
        }.getOrDefault(Triple(0L, 0L, 0L))
    }

    private fun extractKbValue(line: String): Long {
        val parts = line.trim().split(Regex("\\s+"))
        return parts.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    fun readThermalCelsius(): Float {
        if (isNativeLoaded) {
            val t = runCatching { nativeReadThermalCelsius() }.getOrDefault(-1f)
            if (t > 0f) return t
        }
        return runCatching {
            val root = File("/sys/class/thermal")
            if (!root.exists()) return@runCatching -1f
            var maxTemp = -1f
            root.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { zone ->
                val raw = File(zone, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
                if (raw != null && raw > 0) {
                    val c = if (raw > 1000L) raw / 1000f else raw.toFloat()
                    if (c in 5f..130f && c > maxTemp) maxTemp = c
                }
            }
            maxTemp
        }.getOrDefault(-1f)
    }

    fun readCpuCount(): Int {
        if (isNativeLoaded) {
            val c = runCatching { nativeReadCpuCount() }.getOrDefault(0)
            if (c > 0) return c
        }
        return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }

    fun checkVulkanDriverNode(): Boolean {
        if (isNativeLoaded) {
            val ok = runCatching { nativeCheckVulkanDriverNode() }.getOrDefault(false)
            if (ok) return true
        }
        val candidates = listOf(
            "/vendor/lib64/hw/vulkan.mt6789.so",
            "/vendor/lib64/hw/vulkan.mali.so",
            "/vendor/lib64/libvulkan.so",
            "/system/lib64/libvulkan.so"
        )
        return candidates.any { File(it).canRead() }
    }

    fun spawnProcess(
        executable: String,
        argv: Array<String>,
        envp: Array<String>,
        workingDir: String,
        logFilePath: String
    ): Int {
        if (!isNativeLoaded) {
            return -95 // ENOTSUP when JNI shared lib wasn't packaged in APK
        }
        return runCatching {
            nativeSpawnProcess(executable, argv, envp, workingDir, logFilePath)
        }.getOrDefault(-1)
    }

    fun pollProcessStatus(pid: Int): Pair<Int, Int> {
        if (!isNativeLoaded || pid <= 0) return -1 to 0
        val res = runCatching { nativePollProcessStatus(pid) }.getOrNull()
        return if (res != null && res.size >= 2) res[0] to res[1] else -1 to 0
    }

    fun terminateProcess(pid: Int, forceKill: Boolean): Boolean {
        if (!isNativeLoaded || pid <= 0) return false
        return runCatching { nativeTerminateProcess(pid, forceKill) }.getOrDefault(false)
    }

    fun readProcessRssKb(pid: Int): Long {
        if (!isNativeLoaded || pid <= 0) return 0L
        return runCatching { nativeReadProcessRssKb(pid) }.getOrDefault(0L)
    }

    fun openInputSocket(socketPath: String): Int {
        if (!isNativeLoaded) return -1
        return runCatching { nativeOpenInputSocket(socketPath) }.getOrDefault(-1)
    }

    fun sendXInputState(
        fd: Int,
        socketPath: String,
        buttonsMask: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        thumbLX: Short,
        thumbLY: Short,
        thumbRX: Short,
        thumbRY: Short,
        sequence: Int
    ): Boolean {
        if (!isNativeLoaded || fd < 0) return false
        return runCatching {
            nativeSendXInputState(
                fd,
                socketPath,
                buttonsMask,
                leftTrigger,
                rightTrigger,
                thumbLX,
                thumbLY,
                thumbRX,
                thumbRY,
                sequence
            )
        }.getOrDefault(false)
    }

    fun closeInputSocket(fd: Int) {
        if (isNativeLoaded && fd >= 0) {
            runCatching { nativeCloseInputSocket(fd) }
        }
    }
}
