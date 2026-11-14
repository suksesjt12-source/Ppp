package com.stormrevolution.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stormrevolution.launcher.manager.DiagnosticManager
import com.stormrevolution.launcher.manager.GraphicsManager
import com.stormrevolution.launcher.manager.PeInspector
import com.stormrevolution.launcher.manager.RuntimeManager
import com.stormrevolution.launcher.manager.StorageManager
import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.DeviceSystemInfo
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GameLibraryState
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.GraphicsBackendMode
import com.stormrevolution.launcher.model.GraphicsDriverOption
import com.stormrevolution.launcher.model.RuntimeArchitectureMode
import com.stormrevolution.launcher.model.RuntimeComponentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeAndRuntimeVerificationTest {

    @Test
    fun `PeInspector validates PE32 x86 header matching NSUNSR_exe`() {
        val buf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0, 0x5A4D.toShort()) // MZ
        buf.putInt(0x3C, 0x80)            // e_lfanew = 0x80
        buf.putInt(0x80, 0x00004550)      // PE\0\0
        buf.putShort(0x84, PeInspector.IMAGE_FILE_MACHINE_I386.toShort())
        buf.putShort(0x86, 6.toShort())   // 6 sections
        buf.putShort(0x94, 224.toShort()) // SizeOfOptionalHeader
        buf.putShort(0x98, 0x010B.toShort()) // PE32
        buf.putInt(0x98 + 16, 0x12345)    // EntryPoint
        buf.putInt(0x98 + 28, 0x00400000) // ImageBase
        buf.putShort(0x98 + 68, 2.toShort()) // Windows GUI

        val result = PeInspector.inspectBytes(buf.array())
        assertTrue(result.isValidPe)
        assertTrue(result.is32Bit)
        assertFalse(result.is64Bit)
        assertEquals(PeInspector.IMAGE_FILE_MACHINE_I386, result.machine)
        assertEquals(6, result.sectionCount)
    }

    @Test
    fun `Mali GPU blocks Adreno Turnip driver automatically`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gfx = GraphicsManager(context)
        val maliSys = DeviceSystemInfo(
            gpuRenderer = "Mali-G57 MC2",
            gpuVendor = "ARM",
            isMaliGpu = true,
            isAdrenoGpu = false,
            vulkanSupported = true
        )
        val cfg = GameConfiguration(
            graphicsDriver = GraphicsDriverOption.ADRENO_TURNIP,
            graphicsBackend = GraphicsBackendMode.AUTOMATIC
        )
        val decision = gfx.resolveGraphicsDecision(cfg, maliSys, emptyMap())
        assertNotEquals(GraphicsDriverOption.ADRENO_TURNIP, decision.selectedDriver)
        assertTrue(decision.blockedReason?.contains("Turnip", ignoreCase = true) == true)
        assertEquals(GraphicsBackendMode.WINED3D_OPENGL, decision.effectiveBackend)
    }

    @Test
    fun `RuntimeManager never reports empty runtime dir as installed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = StorageManager(context)
        storage.runtimeRootDir.deleteRecursively()
        val rt = RuntimeManager(context, storage)
        val components = rt.scanAllComponents()
        assertEquals(RuntimeComponentId.entries.size, components.size)
        assertTrue(components.values.all { it.status == ComponentStatus.NOT_INSTALLED })
    }

    @Test
    fun `DiagnosticEngine blocks pure Box64 on 32-bit PE32 executable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gfx = GraphicsManager(context)
        val diag = DiagnosticManager()
        val pe = PeInspector.inspectBytes(ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0, 0x5A4D.toShort())
            putInt(0x3C, 0x80)
            putInt(0x80, 0x00004550)
            putShort(0x84, PeInspector.IMAGE_FILE_MACHINE_I386.toShort())
            putShort(0x94, 224.toShort())
            putShort(0x98, 0x010B.toShort())
        }.array())

        val cfg = GameConfiguration(architectureMode = RuntimeArchitectureMode.WINE_BOX64_ONLY)
        val sys = DeviceSystemInfo(supportedAbis = listOf("arm64-v8a"), gpuRenderer = "Mali-G57 MC2", isMaliGpu = true)
        val report = diag.runDiagnostics(
            gameState = GameLibraryState(status = GameStatus.GAME_READY, peResult = pe, dataCpkFound = true),
            runtimeComponents = emptyMap(),
            config = cfg,
            systemInfo = sys,
            graphicsDecision = gfx.resolveGraphicsDecision(cfg, sys, emptyMap()),
            prefixSummary = null,
            availableStorageBytes = 4L * 1024L * 1024L * 1024L
        )
        assertFalse(report.canLaunch)
        assertTrue(report.items.any { it.id == "arch_mismatch" && it.code == "ARCH-01" })
    }
}
