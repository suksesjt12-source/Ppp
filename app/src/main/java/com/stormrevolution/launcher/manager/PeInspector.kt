package com.stormrevolution.launcher.manager

import com.stormrevolution.launcher.model.PeInspectionResult
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PeInspector {

    const val IMAGE_FILE_MACHINE_I386 = 0x014C
    const val IMAGE_FILE_MACHINE_AMD64 = 0x8664
    const val IMAGE_FILE_MACHINE_ARMNT = 0x01C4
    const val IMAGE_FILE_MACHINE_ARM64 = 0xAA64

    fun inspect(inputStream: InputStream): PeInspectionResult {
        val headerBuf = ByteArray(4096)
        val totalRead = readFullyUpTo(inputStream, headerBuf)
        if (totalRead < 64) {
            return PeInspectionResult(
                isValidPe = false,
                summary = "File too small ($totalRead bytes) to contain DOS/PE headers."
            )
        }
        return inspectBytes(headerBuf, totalRead)
    }

    fun inspectBytes(bytes: ByteArray, length: Int = bytes.size): PeInspectionResult {
        if (length < 64) {
            return PeInspectionResult(
                isValidPe = false,
                summary = "Truncated executable header ($length bytes)."
            )
        }

        val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)

        // DOS MZ magic
        val dosMagic = buf.getShort(0).toInt() and 0xFFFF
        if (dosMagic != 0x5A4D) {
            return PeInspectionResult(
                isValidPe = false,
                summary = "Invalid DOS MZ header (got 0x${dosMagic.toString(16)})."
            )
        }

        val peOffset = buf.getInt(0x3C)
        if (peOffset < 0x40 || peOffset + 24 > length) {
            return PeInspectionResult(
                isValidPe = false,
                summary = "Invalid or out-of-bounds PE header offset ($peOffset)."
            )
        }

        // PE\0\0 signature
        val peSig = buf.getInt(peOffset)
        if (peSig != 0x00004550) {
            return PeInspectionResult(
                isValidPe = false,
                summary = "Missing PE\\0\\0 signature at offset 0x${peOffset.toString(16)}."
            )
        }

        val coffOffset = peOffset + 4
        val machine = buf.getShort(coffOffset).toInt() and 0xFFFF
        val sectionCount = buf.getShort(coffOffset + 2).toInt() and 0xFFFF
        val sizeOfOptionalHeader = buf.getShort(coffOffset + 16).toInt() and 0xFFFF

        val optOffset = coffOffset + 20
        var optMagic = 0
        var entryPointRva = 0L
        var imageBase = 0L
        var sizeOfImage = 0L
        var subsystem = 0

        if (sizeOfOptionalHeader >= 24 && optOffset + 2 <= length) {
            optMagic = buf.getShort(optOffset).toInt() and 0xFFFF
            if (optOffset + 20 <= length) {
                entryPointRva = buf.getInt(optOffset + 16).toLong() and 0xFFFFFFFFL
            }
            if (optMagic == 0x010B && optOffset + 72 <= length) {
                // PE32 (32-bit)
                imageBase = buf.getInt(optOffset + 28).toLong() and 0xFFFFFFFFL
                sizeOfImage = buf.getInt(optOffset + 56).toLong() and 0xFFFFFFFFL
                subsystem = buf.getShort(optOffset + 68).toInt() and 0xFFFF
            } else if (optMagic == 0x020B && optOffset + 72 <= length) {
                // PE32+ (64-bit)
                imageBase = buf.getLong(optOffset + 24)
                sizeOfImage = buf.getInt(optOffset + 56).toLong() and 0xFFFFFFFFL
                subsystem = buf.getShort(optOffset + 68).toInt() and 0xFFFF
            }
        }

        val is32 = machine == IMAGE_FILE_MACHINE_I386 || optMagic == 0x010B
        val is64 = machine == IMAGE_FILE_MACHINE_AMD64 || machine == IMAGE_FILE_MACHINE_ARM64 || optMagic == 0x020B

        val archLabel = when (machine) {
            IMAGE_FILE_MACHINE_I386 -> "x86 (PE32 • 32-bit)"
            IMAGE_FILE_MACHINE_AMD64 -> "x86_64 (PE32+ • 64-bit)"
            IMAGE_FILE_MACHINE_ARM64 -> "ARM64 (PE32+ • 64-bit)"
            IMAGE_FILE_MACHINE_ARMNT -> "ARM Thumb-2 (PE32)"
            else -> "Unknown Machine (0x${machine.toString(16)})"
        }

        val subLabel = when (subsystem) {
            2 -> "Windows GUI"
            3 -> "Windows CUI"
            else -> "Subsystem($subsystem)"
        }

        return PeInspectionResult(
            isValidPe = true,
            machine = machine,
            architectureLabel = archLabel,
            is32Bit = is32,
            is64Bit = is64,
            subsystem = subsystem,
            sectionCount = sectionCount,
            entryPointRva = entryPointRva,
            imageBase = imageBase,
            sizeOfImage = sizeOfImage,
            summary = "$archLabel • $subLabel • $sectionCount sections • EP=0x${entryPointRva.toString(16)}"
        )
    }

    private fun readFullyUpTo(inputStream: InputStream, target: ByteArray): Int {
        var offset = 0
        while (offset < target.size) {
            val read = inputStream.read(target, offset, target.size - offset)
            if (read <= 0) break
            offset += read
        }
        return offset
    }
}
