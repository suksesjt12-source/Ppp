package com.stormrevolution.launcher.manager

import android.content.Context
import android.os.StatFs
import java.io.File

class StorageManager(private val context: Context) {

    val rootStorageDir: File
        get() = File(context.filesDir, "storm_launcher").apply { mkdirs() }

    val runtimeRootDir: File
        get() = File(rootStorageDir, "runtime").apply { mkdirs() }

    val winePrefixesDir: File
        get() = File(rootStorageDir, "prefixes").apply { mkdirs() }

    val privateGamesDir: File
        get() = File(rootStorageDir, "games").apply { mkdirs() }

    val logsDir: File
        get() = File(rootStorageDir, "logs").apply { mkdirs() }

    val stagingDir: File
        get() = File(context.cacheDir, "staging").apply { mkdirs() }

    fun getPrefixDir(prefixName: String): File {
        val safeName = prefixName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(winePrefixesDir, safeName)
    }

    fun getAvailableInternalBytes(): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    fun hasSufficientSpace(requiredBytes: Long, safetyMarginBytes: Long = 256L * 1024L * 1024L): Boolean {
        val available = getAvailableInternalBytes()
        return available >= (requiredBytes + safetyMarginBytes)
    }

    fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
