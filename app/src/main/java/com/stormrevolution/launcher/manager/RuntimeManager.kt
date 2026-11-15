package com.stormrevolution.launcher.manager

import android.content.Context
import android.net.Uri
import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.RuntimeComponent
import com.stormrevolution.launcher.model.RuntimeComponentId
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class RuntimeManager(
    private val context: Context,
    private val storageManager: StorageManager
) {

    fun extractBundledRuntimeIfNeeded(
        onProgress: (String) -> Unit = {}
    ): Pair<Boolean, Int> {
        val assetManager = context.assets
        val bundledManifest = runCatching {
            assetManager.open("runtime/bundled_manifest.txt").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return false to 0

        val installedMarker = File(storageManager.runtimeRootDir, ".bundled_manifest_installed")
        if (installedMarker.exists() && runCatching { installedMarker.readText() }.getOrNull() == bundledManifest) {
            return false to 0
        }

        var copiedCount = 0
        fun copyAssetTree(assetPath: String, destDir: File) {
            val entries = runCatching { assetManager.list(assetPath) }.getOrNull() ?: return
            if (entries.isEmpty()) {
                // Leaf asset file
                destDir.parentFile?.mkdirs()
                runCatching {
                    assetManager.open(assetPath).use { input ->
                        FileOutputStream(destDir).use { output ->
                            input.copyTo(output, bufferSize = 32 * 1024)
                        }
                    }
                    if (destDir.parentFile?.name == "bin" ||
                        destDir.name.endsWith(".sh") ||
                        !destDir.name.contains('.')
                    ) {
                        destDir.setExecutable(true, false)
                    }
                    copiedCount++
                    if (copiedCount % 25 == 0) {
                        onProgress("Unpacking built-in APK runtime ($copiedCount files)...")
                    }
                }
            } else {
                destDir.mkdirs()
                for (child in entries) {
                    copyAssetTree("$assetPath/$child", File(destDir, child))
                }
            }
        }

        onProgress("Preparing built-in Storm ARM64 runtime...")
        copyAssetTree("runtime", storageManager.runtimeRootDir)
        runCatching { installedMarker.writeText(bundledManifest) }
        return true to copiedCount
    }

    fun scanAllComponents(): Map<RuntimeComponentId, RuntimeComponent> {
        val result = linkedMapOf<RuntimeComponentId, RuntimeComponent>()
        for (id in RuntimeComponentId.entries) {
            result[id] = inspectComponent(id)
        }
        return result
    }

    fun resolveNativeLibFallbackFile(id: RuntimeComponentId): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir?.let { File(it) } ?: return null
        val soName = when (id) {
            RuntimeComponentId.BOX64 -> "libstorm_box64.so"
            RuntimeComponentId.ROOTFS -> "libstorm_busybox.so"
            RuntimeComponentId.VULKAN_BACKEND -> "libstorm_vulkan_wrapper.so"
            RuntimeComponentId.AUDIO_BACKEND -> "libstorm_audio_bridge.so"
            else -> return null
        }
        return File(nativeDir, soName).takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    fun inspectComponent(id: RuntimeComponentId): RuntimeComponent {
        val componentDir = File(storageManager.runtimeRootDir, id.relativeSubdir)
        val extractedFile = File(componentDir, id.expectedBinary)
        val nativeLibFile = resolveNativeLibFallbackFile(id)
        val binaryFile = when {
            nativeLibFile != null -> nativeLibFile
            extractedFile.exists() && extractedFile.isFile && extractedFile.length() > 0L -> extractedFile
            else -> extractedFile
        }

        if (!binaryFile.exists() || !binaryFile.isFile || binaryFile.length() == 0L) {
            return RuntimeComponent(
                id = id,
                status = ComponentStatus.NOT_INSTALLED,
                version = "Not installed",
                filePath = binaryFile.absolutePath,
                fileSizeBytes = 0L,
                expectedArch = expectedArchLabel(id),
                sha256Short = "—",
                detailMessage = if (id == RuntimeComponentId.BOX86) {
                    "Optional ARM32 fallback (WoW64 + Box64 is the primary mode)."
                } else {
                    "Expected '${id.expectedBinary}' in runtime/${id.relativeSubdir}."
                }
            )
        }

        val shaShort = computeSha256Short(binaryFile)
        val size = binaryFile.length()
        val sourceTag = if (binaryFile == nativeLibFile) "APK Native Lib" else "Private Runtime"

        return when (id) {
            RuntimeComponentId.WINE,
            RuntimeComponentId.BOX64,
            RuntimeComponentId.BOX86,
            RuntimeComponentId.AUDIO_BACKEND,
            RuntimeComponentId.ROOTFS -> {
                val elf = NativeBridge.verifyElfBinary(binaryFile)
                if (!elf.isValidElf) {
                    RuntimeComponent(
                        id = id,
                        status = ComponentStatus.CORRUPTED,
                        version = "Invalid ELF",
                        filePath = binaryFile.absolutePath,
                        fileSizeBytes = size,
                        expectedArch = expectedArchLabel(id),
                        sha256Short = shaShort,
                        detailMessage = "File exists ($size bytes) but failed ELF magic header verification."
                    )
                } else {
                    if (!elf.isExecutable) {
                        binaryFile.setExecutable(true, false)
                    }
                    val compatible = when (id) {
                        RuntimeComponentId.BOX86 -> elf.elfClass == 1 || elf.elfClass == 2
                        RuntimeComponentId.WINE -> elf.elfClass == 2 && (elf.machine == 0xB7 || elf.machine == 0x3E)
                        else -> elf.elfClass == 2 && elf.machine == 0xB7
                    }
                    RuntimeComponent(
                        id = id,
                        status = if (compatible) ComponentStatus.INSTALLED else ComponentStatus.INCOMPATIBLE,
                        version = if (compatible) "$sourceTag Verified" else "Arch Mismatch",
                        filePath = binaryFile.absolutePath,
                        fileSizeBytes = size,
                        expectedArch = elf.archDescription,
                        sha256Short = shaShort,
                        detailMessage = if (compatible) {
                            "${elf.archDescription} • ${storageManager.formatByteSize(size)} ($sourceTag)"
                        } else {
                            "Incompatible ELF binary: ${elf.archDescription}"
                        }
                    )
                }
            }

            RuntimeComponentId.WOW64,
            RuntimeComponentId.DXVK -> {
                val pe = binaryFile.inputStream().use { PeInspector.inspect(it) }
                RuntimeComponent(
                    id = id,
                    status = if (pe.isValidPe) ComponentStatus.INSTALLED else ComponentStatus.CORRUPTED,
                    version = if (pe.isValidPe) "PE Verified" else "Invalid DLL",
                    filePath = binaryFile.absolutePath,
                    fileSizeBytes = size,
                    expectedArch = if (pe.isValidPe) pe.architectureLabel else "Windows PE DLL",
                    sha256Short = shaShort,
                    detailMessage = pe.summary
                )
            }

            RuntimeComponentId.VULKAN_BACKEND,
            RuntimeComponentId.RUNTIME_LIBS -> {
                val elf = NativeBridge.verifyElfBinary(binaryFile)
                RuntimeComponent(
                    id = id,
                    status = if (elf.isValidElf) ComponentStatus.INSTALLED else ComponentStatus.CORRUPTED,
                    version = if (elf.isValidElf) "$sourceTag Ready" else "Corrupted .so",
                    filePath = binaryFile.absolutePath,
                    fileSizeBytes = size,
                    expectedArch = elf.archDescription,
                    sha256Short = shaShort,
                    detailMessage = if (elf.isValidElf) "${elf.archDescription} ($sourceTag)" else "Invalid shared library header."
                )
            }

            RuntimeComponentId.GRAPHICS_DRIVER -> {
                val content = runCatching { binaryFile.readText() }.getOrDefault("")
                val validScript = content.contains("MALI", ignoreCase = true) ||
                    content.contains("VK_ICD_FILENAMES") ||
                    content.contains("MESA")
                RuntimeComponent(
                    id = id,
                    status = if (validScript) ComponentStatus.INSTALLED else ComponentStatus.CORRUPTED,
                    version = if (validScript) "Mali Profile Ready" else "Malformed",
                    filePath = binaryFile.absolutePath,
                    fileSizeBytes = size,
                    expectedArch = "Shell Env",
                    sha256Short = shaShort,
                    detailMessage = if (validScript) "Mali-G57 graphics environment script bundled & active." else "Invalid graphics env script."
                )
            }
        }
    }

    fun importRuntimeZipPackage(uri: Uri, targetComponent: RuntimeComponentId? = null): Pair<Boolean, String> {
        val destBase = if (targetComponent != null) {
            File(storageManager.runtimeRootDir, targetComponent.name.lowercase())
        } else {
            storageManager.runtimeRootDir
        }
        destBase.mkdirs()

        return runCatching {
            val resolver = context.contentResolver
            val input = resolver.openInputStream(uri)
                ?: return false to "Unable to open selected package URI."
            var extractedCount = 0
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val normalizedName = entry.name.removePrefix("./").removePrefix("/")
                    if (normalizedName.isNotBlank() && !normalizedName.contains("..")) {
                        val outFile = File(storageManager.runtimeRootDir, normalizedName)
                        val canonicalRoot = storageManager.runtimeRootDir.canonicalPath
                        val canonicalOut = outFile.canonicalPath
                        if (!canonicalOut.startsWith(canonicalRoot)) {
                            throw SecurityException("Blocked unsafe ZIP path traversal: $normalizedName")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            if (outFile.parentFile?.name == "bin" ||
                                outFile.name.endsWith(".sh") ||
                                !outFile.name.contains('.')
                            ) {
                                outFile.setExecutable(true, false)
                            }
                            extractedCount++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (extractedCount == 0) {
                false to "Selected archive contained 0 extractable runtime files."
            } else {
                true to "Extracted $extractedCount runtime files into private runtime store."
            }
        }.getOrElse { err ->
            false to "Runtime import failed: ${err.message ?: err.javaClass.simpleName}"
        }
    }

    fun removeComponent(id: RuntimeComponentId): Boolean {
        val dir = File(storageManager.runtimeRootDir, id.relativeSubdir)
        val file = File(dir, id.expectedBinary)
        return if (file.exists()) file.delete() else false
    }

    fun clearAllRuntimes(): Boolean {
        return storageManager.runtimeRootDir.deleteRecursively().also {
            storageManager.runtimeRootDir.mkdirs()
        }
    }

    private fun expectedArchLabel(id: RuntimeComponentId): String = when (id) {
        RuntimeComponentId.BOX86 -> "ARM32/ARM64 ELF"
        RuntimeComponentId.WOW64, RuntimeComponentId.DXVK -> "Windows PE DLL"
        RuntimeComponentId.GRAPHICS_DRIVER -> "POSIX Env Script"
        RuntimeComponentId.WINE -> "ARM64 or x86_64 ELF (via Box64)"
        else -> "ARM64 (AArch64) ELF"
    }

    private fun computeSha256Short(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buf = ByteArray(16 * 1024)
                var read = fis.read(buf)
                while (read > 0) {
                    digest.update(buf, 0, read)
                    read = fis.read(buf)
                }
            }
            digest.digest().take(6).joinToString("") { "%02x".format(it) }
        }.getOrDefault("—")
    }
}
