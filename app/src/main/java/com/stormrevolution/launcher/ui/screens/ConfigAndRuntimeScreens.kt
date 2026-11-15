package com.stormrevolution.launcher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ChakraOrange
import com.example.ui.theme.RasenganCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.StatusWarning
import com.stormrevolution.launcher.LauncherUiState
import com.stormrevolution.launcher.model.AudioBackendMode
import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GraphicsBackendMode
import com.stormrevolution.launcher.model.GraphicsDriverOption
import com.stormrevolution.launcher.model.PerformancePreset
import com.stormrevolution.launcher.model.ResolutionOption
import com.stormrevolution.launcher.model.RuntimeArchitectureMode
import com.stormrevolution.launcher.model.RuntimeComponentId
import com.stormrevolution.launcher.model.TargetFpsOption
import com.stormrevolution.launcher.ui.components.KeyValueRow
import com.stormrevolution.launcher.ui.components.ScreenHeader
import com.stormrevolution.launcher.ui.components.StatusPill
import com.stormrevolution.launcher.ui.components.StatusTone
import com.stormrevolution.launcher.ui.components.StormSectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameConfigScreen(
    uiState: LauncherUiState,
    onApplyPreset: (PerformancePreset) -> Unit,
    onUpdateConfig: ((GameConfiguration) -> GameConfiguration) -> Unit,
    onInitializePrefix: () -> Unit,
    onResetPrefix: () -> Unit,
    onBack: () -> Unit
) {
    val cfg = uiState.config
    val prefix = uiState.prefixSummary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Game Configuration",
                subtitle = "NSUNSR_OPTIMIZED defaults for Helio G100 + Mali-G57 MC2 + 8 GB RAM",
                onBack = onBack
            )
        }

        item {
            StormSectionCard {
                Text("Performance Preset", style = MaterialTheme.typography.titleMedium, color = ChakraOrange)
                Text(cfg.performancePreset.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PerformancePreset.LOW, PerformancePreset.BALANCED, PerformancePreset.HIGH).forEach { preset ->
                        FilterChip(
                            selected = cfg.performancePreset == preset,
                            onClick = { onApplyPreset(preset) },
                            label = { Text(preset.name) },
                            modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
                        )
                    }
                }
            }
        }

        item {
            StormSectionCard {
                Text("Windows Translation Architecture", style = MaterialTheme.typography.titleMedium, color = RasenganCyan)
                RuntimeArchitectureMode.entries.forEach { mode ->
                    val isBlocked = mode == RuntimeArchitectureMode.WINE_BOX64_ONLY
                    FilterChip(
                        selected = cfg.architectureMode == mode,
                        onClick = {
                            onUpdateConfig {
                                it.copy(
                                    architectureMode = mode,
                                    performancePreset = PerformancePreset.CUSTOM
                                )
                            }
                        },
                        label = {
                            Text(
                                text = mode.label,
                                color = if (isBlocked) StatusWarning else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        modifier = Modifier.testTag("arch_mode_${mode.name.lowercase()}")
                    )
                    Text(
                        text = mode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            StormSectionCard {
                Text("Display Resolution & Frame Pacing", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResolutionOption.entries.forEach { res ->
                        FilterChip(
                            selected = cfg.resolution == res,
                            onClick = {
                                onUpdateConfig {
                                    it.copy(resolution = res, performancePreset = PerformancePreset.CUSTOM)
                                }
                            },
                            label = { Text(res.label) },
                            modifier = Modifier.testTag("res_${res.width}x${res.height}")
                        )
                    }
                }

                Text("Target Frame Rate", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetFpsOption.entries.forEach { fps ->
                        FilterChip(
                            selected = cfg.targetFps == fps,
                            onClick = {
                                onUpdateConfig {
                                    it.copy(targetFps = fps, performancePreset = PerformancePreset.CUSTOM)
                                }
                            },
                            label = { Text(fps.label) },
                            modifier = Modifier.testTag("fps_${fps.fps}")
                        )
                    }
                }
            }
        }

        item {
            StormSectionCard {
                Text("Audio & Dynarec Tuning", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioBackendMode.entries.forEach { audio ->
                        FilterChip(
                            selected = cfg.audioBackend == audio,
                            onClick = {
                                onUpdateConfig { it.copy(audioBackend = audio) }
                            },
                            label = { Text(audio.label) },
                            modifier = Modifier.testTag("audio_${audio.name.lowercase()}")
                        )
                    }
                }

                ToggleSettingRow(
                    title = "Wine ESYNC (Eventfd Synchronization)",
                    checked = cfg.esyncEnabled,
                    tag = "toggle_esync",
                    onCheckedChange = { v -> onUpdateConfig { it.copy(esyncEnabled = v) } }
                )
                ToggleSettingRow(
                    title = "Box64 Dynarec FastNaN",
                    checked = cfg.box64DynarecFastNan,
                    tag = "toggle_fastnan",
                    onCheckedChange = { v -> onUpdateConfig { it.copy(box64DynarecFastNan = v) } }
                )
                ToggleSettingRow(
                    title = "Verbose Wine WINEDEBUG Output",
                    checked = cfg.logVerboseWine,
                    tag = "toggle_winedebug",
                    onCheckedChange = { v -> onUpdateConfig { it.copy(logVerboseWine = v) } }
                )
            }
        }

        item {
            StormSectionCard {
                Text("Wine Prefix Manager", style = MaterialTheme.typography.titleMedium, color = ChakraOrange)
                KeyValueRow("Active Prefix", cfg.winePrefixName)
                KeyValueRow("Prefix Exists", if (prefix?.exists == true) "Yes (${prefix.windowsVersion})" else "Not Initialized")
                KeyValueRow("Registry Files", "system=${prefix?.hasSystemReg == true}, user=${prefix?.hasUserReg == true}")

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onInitializePrefix,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("init_prefix_button")
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Initialize / Repair")
                    }
                    OutlinedButton(
                        onClick = onResetPrefix,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("reset_prefix_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clean Reset")
                    }
                }
            }
        }
    }
}

@Composable
fun RuntimeManagerScreen(
    uiState: LauncherUiState,
    onImportPackage: (Uri, RuntimeComponentId?) -> Unit,
    onRemoveComponent: (RuntimeComponentId) -> Unit,
    onReverifyAll: () -> Unit,
    onBack: () -> Unit
) {
    var pendingTarget by remember { mutableStateOf<RuntimeComponentId?>(null) }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportPackage(uri, pendingTarget)
        }
        pendingTarget = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Windows ARM64 Runtime Manager",
                subtitle = "Auto-unpacks built-in APK runtime & verifies real ELF64/PE binaries on disk",
                onBack = onBack,
                trailing = {
                    OutlinedButton(
                        onClick = onReverifyAll,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("reverify_runtimes_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verify")
                    }
                }
            )
        }

        item {
            StormSectionCard(borderColor = RasenganCyan.copy(alpha = 0.55f)) {
                Text("Built-In APK Runtime & Optional Override", style = MaterialTheme.typography.titleMedium, color = RasenganCyan)
                Text(
                    text = "Runtime ARM64 (Box64, Wine WoW64, DXVK, RootFS, Mali Profile) diekstrak otomatis saat aplikasi dibuka dari build GitHub Actions. Impor ZIP manual di bawah bersifat opsional untuk update kustom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        pendingTarget = null
                        zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("import_runtime_bundle_button")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Custom Runtime ZIP (Opsional)")
                }
            }
        }

        items(RuntimeComponentId.entries) { id ->
            val comp = uiState.runtimeComponents[id]
            val status = comp?.status ?: ComponentStatus.NOT_INSTALLED
            val tone = when (status) {
                ComponentStatus.INSTALLED -> StatusTone.VERIFIED
                ComponentStatus.VERIFYING, ComponentStatus.DOWNLOADING -> StatusTone.INFO
                ComponentStatus.CORRUPTED, ComponentStatus.INCOMPATIBLE -> StatusTone.ERROR
                ComponentStatus.NOT_INSTALLED -> StatusTone.WARNING
            }

            StormSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(id.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Expected: ${id.relativeSubdir}/${id.expectedBinary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        label = status.label,
                        tone = tone,
                        modifier = Modifier.testTag("runtime_status_${id.name.lowercase()}")
                    )
                }

                if (comp != null) {
                    KeyValueRow("Binary Arch", comp.expectedArch)
                    KeyValueRow("SHA-256 (head)", comp.sha256Short)
                    Text(
                        text = comp.detailMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pendingTarget = id
                            zipPicker.launch(arrayOf("application/zip", "*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("import_component_${id.name.lowercase()}")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import ZIP")
                    }
                    if (status != ComponentStatus.NOT_INSTALLED) {
                        OutlinedButton(
                            onClick = { onRemoveComponent(id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("remove_component_${id.name.lowercase()}")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove component")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraphicsSettingsScreen(
    uiState: LauncherUiState,
    onUpdateConfig: ((GameConfiguration) -> GameConfiguration) -> Unit,
    onBack: () -> Unit
) {
    val cfg = uiState.config
    val decision = uiState.graphicsDecision
    val sys = uiState.systemInfo

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Graphics & Mali Compatibility",
                subtitle = "DirectX 9 translation pipeline tuned for ARM Mali-G57 MC2",
                onBack = onBack
            )
        }

        item {
            StormSectionCard {
                Text("Detected GPU & Active Decision", style = MaterialTheme.typography.titleMedium, color = RasenganCyan)
                KeyValueRow("GPU Renderer", sys.gpuRenderer)
                KeyValueRow("Vulkan Hardware", sys.vulkanVersionString)
                KeyValueRow("Requested Backend", cfg.graphicsBackend.label)
                KeyValueRow(
                    "Effective Backend",
                    decision.effectiveBackend.label,
                    valueColor = if (decision.fallbackActive) StatusWarning else StatusVerified
                )
                KeyValueRow("Active Driver", decision.selectedDriver.label)

                if (decision.blockedReason != null) {
                    Surface(
                        color = StatusWarning.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = decision.blockedReason,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusWarning
                        )
                    }
                }
            }
        }

        item {
            StormSectionCard {
                Text("DirectX 9 Translation Backend", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GraphicsBackendMode.entries.forEach { mode ->
                        FilterChip(
                            selected = cfg.graphicsBackend == mode,
                            onClick = { onUpdateConfig { it.copy(graphicsBackend = mode) } },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("gfx_backend_${mode.name.lowercase()}")
                        )
                    }
                }

                Text("Graphics Driver Selection", style = MaterialTheme.typography.titleSmall)
                GraphicsDriverOption.entries.forEach { drv ->
                    val blockedOnMali = !drv.isMaliCompatible && !sys.isAdrenoGpu
                    FilterChip(
                        selected = cfg.graphicsDriver == drv,
                        enabled = !blockedOnMali,
                        onClick = { onUpdateConfig { it.copy(graphicsDriver = drv) } },
                        label = { Text(drv.label) },
                        modifier = Modifier.testTag("gfx_driver_${drv.name.lowercase()}")
                    )
                }

                ToggleSettingRow(
                    title = "DXVK Async Shader Compilation (Reduces Stutter)",
                    checked = cfg.dxvkAsyncShaders,
                    tag = "toggle_dxvk_async",
                    onCheckedChange = { v -> onUpdateConfig { it.copy(dxvkAsyncShaders = v) } }
                )
                ToggleSettingRow(
                    title = "DXVK On-Screen HUD",
                    checked = cfg.dxvkHudEnabled,
                    tag = "toggle_dxvk_hud",
                    onCheckedChange = { v -> onUpdateConfig { it.copy(dxvkHudEnabled = v) } }
                )
                ToggleSettingRow(
                    title = "WineD3D Multi-Threaded CSMT",
                    checked = cfg.csmtEnabled,
                    tag = "toggle_csmt",
                    onCheckedChange = { v -> onUpdateConfig { it.copy(csmtEnabled = v) } }
                )
                ToggleSettingRow(
                    title = "VSync Lock",
                    checked = cfg.vsyncEnabled,
                    tag = "toggle_vsync",
                    onCheckedChange = { v -> onUpdateConfig { it.copy(vsyncEnabled = v) } }
                )
            }
        }

        item {
            StormSectionCard {
                Text("Generated Graphics Environment Variables", style = MaterialTheme.typography.titleMedium)
                decision.envVars.forEach { (k, v) ->
                    KeyValueRow(key = k, value = v.ifEmpty { "<unset>" })
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
