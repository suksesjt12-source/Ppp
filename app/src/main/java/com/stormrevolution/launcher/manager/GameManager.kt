package com.stormrevolution.launcher.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.stormrevolution.launcher.model.GameLibraryState
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.PeInspectionResult
import java.io.File
import java.io.FileOutputStream

class GameManager(
    private val context: Context,
    private val storageManager: StorageManager
) {

    fun takePersistablePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    fun scanGameDirectory(treeUri: Uri, preferExistingPrivateCopy: Boolean = false): GameLibraryState {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return GameLibraryState(
                status = GameStatus.GAME_VALIDATION_FAILED,
                rootTreeUri = treeUri.toString(),
                rootDisplayPath = treeUri.lastPathSegment ?: treeUri.toString(),
                validationErrors = listOf("Unable to open selected SAF directory URI."),
                lastScannedAtMs = System.currentTimeMillis()
            )

        var exeDoc: DocumentFile? = null
        var dataCpkDoc: DocumentFile? = null
        var configIniDoc: DocumentFile? = null
        var totalSize = 0L
        var fileCount = 0

        fun walk(doc: DocumentFile, depth: Int) {
            if (depth > 4) return
            val children = doc.listFiles()
            for (child in children) {
                if (child.isDirectory) {
                    walk(child, depth + 1)
                } else if (child.isFile) {
                    fileCount++
                    val len = child.length().coerceAtLeast(0L)
                    totalSize += len
                    val name = child.name ?: continue
                    when {
                        name.equals("NSUNSR.exe", ignoreCase = true) -> exeDoc = child
                        name.equals("config.ini", ignoreCase = true) -> configIniDoc = child
                        name.endsWith(".cpk", ignoreCase = true) && len > 0L -> {
                            if (dataCpkDoc == null || len > (dataCpkDoc?.length() ?: 0L)) {
                                dataCpkDoc = child
                            }
                        }
                    }
                }
            }
        }

        walk(rootDoc, 0)

        val displayPath = rootDoc.name ?: treeUri.lastPathSegment ?: "Selected Directory"

        if (exeDoc == null) {
            return GameLibraryState(
                status = GameStatus.GAME_NOT_FOUND,
                rootTreeUri = treeUri.toString(),
                rootDisplayPath = displayPath,
                totalDirectorySizeBytes = totalSize,
                fileCount = fileCount,
                validationErrors = listOf("NSUNSR.exe not found inside '$displayPath'."),
                lastScannedAtMs = System.currentTimeMillis()
            )
        }

        val targetExe = exeDoc!!
        val exeSize = targetExe.length().coerceAtLeast(0L)
        val errors = mutableListOf<String>()

        if (exeSize < 1024L) {
            errors += "NSUNSR.exe file size ($exeSize bytes) is too small to be a valid game binary."
        }

        val peResult = runCatching {
            context.contentResolver.openInputStream(targetExe.uri)?.use { stream ->
                PeInspector.inspect(stream)
            } ?: PeInspectionResult(isValidPe = false, summary = "Cannot open input stream for NSUNSR.exe.")
        }.getOrElse {
            PeInspectionResult(isValidPe = false, summary = "Read error: ${it.message}")
        }

        if (!peResult.isValidPe) {
            errors += peResult.summary
        }

        val privateGameDir = File(storageManager.privateGamesDir, "NSUNSR")
        val privateExe = File(privateGameDir, "NSUNSR.exe")
        val hasPrivateCopy = preferExistingPrivateCopy && privateExe.exists() && privateExe.length() > 1024L

        val computedStatus = when {
            errors.isNotEmpty() -> GameStatus.GAME_VALIDATION_FAILED
            peResult.isValidPe -> GameStatus.GAME_READY
            else -> GameStatus.GAME_FOUND
        }

        return GameLibraryState(
            status = computedStatus,
            rootTreeUri = treeUri.toString(),
            rootDisplayPath = displayPath,
            exeFileName = targetExe.name ?: "NSUNSR.exe",
            exeSizeBytes = exeSize,
            dataCpkFound = dataCpkDoc != null,
            dataCpkSizeBytes = dataCpkDoc?.length() ?: 0L,
            configIniFound = configIniDoc != null,
            totalDirectorySizeBytes = totalSize,
            fileCount = fileCount,
            peResult = peResult,
            copiedPrivateExePath = if (hasPrivateCopy) privateExe.absolutePath else "",
            isPrivateCopy = hasPrivateCopy,
            validationErrors = errors,
            lastScannedAtMs = System.currentTimeMillis()
        )
    }

    fun copyDirectoryToPrivateStorage(
        treeUri: Uri,
        onProgress: (copiedFiles: Int, totalFiles: Int, currentName: String) -> Unit = { _, _, _ -> }
    ): Pair<Boolean, String> {
        val currentScan = scanGameDirectory(treeUri)
        if (!currentScan.peResult.isValidPe) {
            return false to "Cannot import: NSUNSR.exe validation failed."
        }

        if (!storageManager.hasSufficientSpace(currentScan.totalDirectorySizeBytes)) {
            val need = storageManager.formatByteSize(currentScan.totalDirectorySizeBytes)
            val free = storageManager.formatByteSize(storageManager.getAvailableInternalBytes())
            return false to "Insufficient internal storage: need $need (+256 MB buffer), available $free."
        }

        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return false to "Unable to access source SAF directory."

        val destDir = File(storageManager.privateGamesDir, "NSUNSR").apply { mkdirs() }
        var copied = 0
        val total = currentScan.fileCount.coerceAtLeast(1)

        return runCatching {
            fun copyRec(srcDir: DocumentFile, dstDir: File, depth: Int) {
                if (depth > 5) return
                dstDir.mkdirs()
                for (child in srcDir.listFiles()) {
                    val name = child.name ?: continue
                    if (child.isDirectory) {
                        copyRec(child, File(dstDir, name), depth + 1)
                    } else if (child.isFile) {
                        val outFile = File(dstDir, name)
                        context.contentResolver.openInputStream(child.uri)?.use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output, bufferSize = 64 * 1024)
                            }
                        }
                        copied++
                        onProgress(copied, total, name)
                    }
                }
            }
            copyRec(rootDoc, destDir, 0)
            val exeFile = File(destDir, "NSUNSR.exe")
            if (!exeFile.exists()) {
                false to "Import completed ($copied files), but NSUNSR.exe was missing in destination."
            } else {
                true to "Copied $copied files to private storage without modifying originals."
            }
        }.getOrElse { err ->
            false to "Import failed: ${err.message}"
        }
    }

    fun inspectPrivateGameCopy(): GameLibraryState? {
        val destDir = File(storageManager.privateGamesDir, "NSUNSR")
        val exeFile = File(destDir, "NSUNSR.exe")
        if (!exeFile.exists() || !exeFile.isFile) return null

        val pe = runCatching { exeFile.inputStream().use { PeInspector.inspect(it) } }
            .getOrDefault(PeInspectionResult(isValidPe = false, summary = "Read error"))

        val files = destDir.walkTopDown().filter { it.isFile }.toList()
        val cpk = files.firstOrNull { it.name.endsWith(".cpk", ignoreCase = true) }
        val cfg = files.any { it.name.equals("config.ini", ignoreCase = true) }
        val totalSize = files.sumOf { it.length() }

        return GameLibraryState(
            status = if (pe.isValidPe) GameStatus.GAME_READY else GameStatus.GAME_VALIDATION_FAILED,
            rootTreeUri = "private://NSUNSR",
            rootDisplayPath = destDir.absolutePath,
            exeFileName = exeFile.name,
            exeSizeBytes = exeFile.length(),
            dataCpkFound = cpk != null,
            dataCpkSizeBytes = cpk?.length() ?: 0L,
            configIniFound = cfg,
            totalDirectorySizeBytes = totalSize,
            fileCount = files.size,
            peResult = pe,
            copiedPrivateExePath = exeFile.absolutePath,
            isPrivateCopy = true,
            validationErrors = if (pe.isValidPe) emptyList() else listOf(pe.summary),
            lastScannedAtMs = System.currentTimeMillis()
        )
    }

    fun clearLinkedGame(): GameLibraryState = GameLibraryState(status = GameStatus.GAME_NOT_FOUND)
}
