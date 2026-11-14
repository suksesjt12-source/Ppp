package com.stormrevolution.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ChakraOrange
import com.example.ui.theme.RasenganCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StormObsidian
import com.example.ui.theme.StormSurface
import com.stormrevolution.launcher.LauncherUiState
import com.stormrevolution.launcher.model.ControlLayoutConfig
import com.stormrevolution.launcher.model.ControllerState
import com.stormrevolution.launcher.model.DiagnosticSeverity
import com.stormrevolution.launcher.model.LogEntry
import com.stormrevolution.launcher.model.LogLevel
import com.stormrevolution.launcher.ui.components.KeyValueRow
import com.stormrevolution.launcher.ui.components.ScreenHeader
import com.stormrevolution.launcher.ui.components.StatusPill
import com.stormrevolution.launcher.ui.components.StatusTone
import com.stormrevolution.launcher.ui.components.StormSectionCard
import kotlin.math.roundToInt

@Composable
fun ControlsSettingsScreen(
    uiState: LauncherUiState,
    controllerState: ControllerState,
    onUpdateControls: ((ControlLayoutConfig) -> ControlLayoutConfig) -> Unit,
    onResetControls: () -> Unit,
    onRefreshGamepads: () -> Unit,
    onBack: () -> Unit
) {
    val cfg = uiState.controlLayout

    LaunchedEffect(Unit) {
        onRefreshGamepads()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Controls & Virtual Pad Editor",
                subtitle = "Drag buttons below to customize Storm Arena overlay • Sends binary XInput over AF_UNIX IPC",
                onBack = onBack
            )
        }

        item {
            StormSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Interactive Overlay Layout Canvas", style = MaterialTheme.typography.titleMedium, color = ChakraOrange)
                    OutlinedButton(
                        onClick = onResetControls,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("reset_controls_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset")
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StormObsidian)
                        .border(1.dp, RasenganCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    val density = LocalDensity.current
                    val wPx = with(density) { maxWidth.toPx() }
                    val hPx = with(density) { maxHeight.toPx() }

                    cfg.elements.forEach { elem ->
                        val sizeDp = (46f * elem.scale).dp
                        val halfPx = with(density) { (sizeDp / 2).toPx() }
                        val xPx = (elem.xFraction * wPx - halfPx).roundToInt()
                        val yPx = (elem.yFraction * hPx - halfPx).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(xPx, yPx) }
                                .size(sizeDp)
                                .alpha(cfg.opacity)
                                .clip(CircleShape)
                                .background(StormSurface)
                                .border(1.5.dp, ChakraOrange, CircleShape)
                                .pointerInput(elem.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onUpdateControls { cur ->
                                            val updated = cur.elements.map { item ->
                                                if (item.id == elem.id && wPx > 0f && hPx > 0f) {
                                                    item.copy(
                                                        xFraction = (item.xFraction + dragAmount.x / wPx).coerceIn(0.05f, 0.95f),
                                                        yFraction = (item.yFraction + dragAmount.y / hPx).coerceIn(0.08f, 0.92f)
                                                    )
                                                } else item
                                            }
                                            cur.copy(elements = updated)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = elem.label.substringBefore(" •"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        item {
            StormSectionCard {
                Text("Touch & Haptic Calibration", style = MaterialTheme.typography.titleMedium)

                Text("Overlay Opacity: ${(cfg.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = cfg.opacity,
                    onValueChange = { v -> onUpdateControls { it.copy(opacity = v) } },
                    valueRange = 0.25f..1.0f,
                    modifier = Modifier.testTag("slider_opacity")
                )

                Text("Analog Stick Deadzone: ${(cfg.deadzone * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = cfg.deadzone,
                    onValueChange = { v -> onUpdateControls { it.copy(deadzone = v) } },
                    valueRange = 0.05f..0.40f,
                    modifier = Modifier.testTag("slider_deadzone")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Virtual Touch Gamepad", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = cfg.showVirtualOverlay,
                        onCheckedChange = { v -> onUpdateControls { it.copy(showVirtualOverlay = v) } },
                        modifier = Modifier.testTag("toggle_virtual_overlay")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Touch Feedback", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = cfg.hapticFeedback,
                        onCheckedChange = { v -> onUpdateControls { it.copy(hapticFeedback = v) } },
                        modifier = Modifier.testTag("toggle_haptic")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Hide Overlay When Physical Gamepad Active", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = cfg.autoHideOnPhysicalGamepad,
                        onCheckedChange = { v -> onUpdateControls { it.copy(autoHideOnPhysicalGamepad = v) } },
                        modifier = Modifier.testTag("toggle_autohide_pad")
                    )
                }
            }
        }

        item {
            StormSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Detected Hardware Controllers", style = MaterialTheme.typography.titleMedium, color = RasenganCyan)
                    OutlinedButton(
                        onClick = onRefreshGamepads,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan")
                    }
                }
                if (controllerState.connectedGamepads.isEmpty()) {
                    Text(
                        text = "No Bluetooth/USB gamepads detected. Virtual touch overlay & keyboard mapping (WASD / IJKL / Space) remain ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    controllerState.connectedGamepads.forEach { pad ->
                        KeyValueRow(
                            key = pad.name,
                            value = "VID=0x${pad.vendorId.toString(16)} PID=0x${pad.productId.toString(16)}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SystemInfoScreen(
    uiState: LauncherUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val sys = uiState.systemInfo
    val perf = uiState.performance

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "System & Hardware Telemetry",
                subtitle = "Live ARM64 SoC, Mali-G57 GPU, Memory, and Native JNI Bridge probe",
                onBack = onBack,
                trailing = {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probe")
                    }
                }
            )
        }

        item {
            StormSectionCard {
                Text("Device & SoC Profile", style = MaterialTheme.typography.titleMedium, color = ChakraOrange)
                KeyValueRow("Model", sys.deviceModel)
                KeyValueRow("SoC / Hardware", sys.socName)
                KeyValueRow("Helio G100 / Mali Profile", if (sys.isHelioG100Target || sys.isMaliGpu) "Active Optimization Target" else "Generic ARM64 Profile")
                KeyValueRow("Android OS", "Android ${sys.androidVersion} (API ${sys.sdkInt})")
                KeyValueRow("Supported ABIs", sys.supportedAbis.joinToString(", "))
                KeyValueRow("CPU Cores Online", "${sys.cpuCores} cores")
            }
        }

        item {
            StormSectionCard {
                Text("Graphics & Native Runtime Bridge", style = MaterialTheme.typography.titleMedium, color = RasenganCyan)
                KeyValueRow("GPU Renderer", sys.gpuRenderer)
                KeyValueRow("GPU Vendor", sys.gpuVendor)
                KeyValueRow("OpenGL ES", sys.glesVersionString)
                KeyValueRow("Vulkan Support", sys.vulkanVersionString)
                KeyValueRow("Native C++20 Bridge", sys.nativeBridgeVersion, valueColor = if (sys.nativeBridgeLoaded) StatusVerified else StatusWarning)
            }
        }

        item {
            StormSectionCard {
                Text("Memory, Storage & Thermal", style = MaterialTheme.typography.titleMedium)
                KeyValueRow("Total RAM", "${sys.totalRamMb} MB")
                KeyValueRow("Available RAM", "${perf.freeRamMb.takeIf { it > 0 } ?: sys.availableRamMb} MB")
                KeyValueRow("Free Internal Storage", uiState.availableStorageText)
                KeyValueRow("Thermal Sensor", if (perf.thermalCelsius > 0f) "${perf.thermalCelsius}°C" else "Nominal / Container Stub")
                KeyValueRow("Audio Output Sinks", uiState.audioOutputs.joinToString(" • "))
            }
        }
    }
}

@Composable
fun DebugConsoleScreen(
    uiState: LauncherUiState,
    onRunDiagnostics: () -> Unit,
    onBuildSanitizedReport: () -> String,
    onBack: () -> Unit
) {
    val report = uiState.diagnosticReport
    val clipboard = LocalClipboardManager.current
    var copiedBanner by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Pre-Flight Diagnostic Engine",
                subtitle = "13-check Windows compatibility, PE32 architecture, and Mali driver validator",
                onBack = onBack
            )
        }

        item {
            StormSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${report.passCount} Passed • ${report.warningCount} Warn • ${report.criticalCount} Critical",
                        style = MaterialTheme.typography.titleMedium
                    )
                    StatusPill(
                        label = if (report.canLaunch) "CHECK PASS" else "BLOCKED",
                        tone = if (report.canLaunch) StatusTone.VERIFIED else StatusTone.ERROR,
                        modifier = Modifier.testTag("diagnostic_summary_pill")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            copiedBanner = false
                            onRunDiagnostics()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("run_diagnostics_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Checks")
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(onBuildSanitizedReport()))
                            copiedBanner = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("export_diagnostics_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedBanner) "Copied!" else "Copy Sanitized")
                    }
                }
            }
        }

        items(report.items, key = { it.id }) { item ->
            val tone = when (item.severity) {
                DiagnosticSeverity.PASS -> StatusTone.VERIFIED
                DiagnosticSeverity.INFO -> StatusTone.INFO
                DiagnosticSeverity.WARNING -> StatusTone.WARNING
                DiagnosticSeverity.CRITICAL -> StatusTone.ERROR
            }
            val border = when (item.severity) {
                DiagnosticSeverity.CRITICAL -> StatusError.copy(alpha = 0.6f)
                DiagnosticSeverity.WARNING -> StatusWarning.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outline
            }

            StormSectionCard(borderColor = border) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("[${item.code}] ${item.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    StatusPill(label = item.severity.name, tone = tone)
                }
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.severity != DiagnosticSeverity.PASS) {
                    Text(
                        text = "Fix: ${item.remediation}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RasenganCyan
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogViewerScreen(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit,
    onBuildSanitizedLogs: () -> String,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var search by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var copied by remember { mutableStateOf(false) }

    val filtered = remember(logs, search, selectedLevel) {
        logs.filter { entry ->
            (selectedLevel == null || entry.level == selectedLevel) &&
                (search.isBlank() || entry.message.contains(search, ignoreCase = true) || entry.source.name.contains(search, ignoreCase = true))
        }.reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            title = "Runtime & Wine Log Viewer",
            subtitle = "${logs.size} buffered events • Auto-sanitizes PII before export",
            onBack = onBack
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Filter logs (Wine, DXVK, PE, Mali...)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("log_search_input")
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedLevel == null,
                onClick = { selectedLevel = null },
                label = { Text("ALL") }
            )
            LogLevel.entries.forEach { lvl ->
                FilterChip(
                    selected = selectedLevel == lvl,
                    onClick = { selectedLevel = lvl },
                    label = { Text(lvl.name) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(onBuildSanitizedLogs()))
                    copied = true
                },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("copy_logs_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (copied) "Copied!" else "Copy Sanitized")
            }
            OutlinedButton(
                onClick = {
                    copied = false
                    onClearLogs()
                },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("clear_logs_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear")
            }
        }

        Surface(
            color = StormObsidian,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No matching runtime logs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        val color = when (entry.level) {
                            LogLevel.ERROR -> StatusError
                            LogLevel.WARN -> StatusWarning
                            LogLevel.INFO -> RasenganCyan
                            LogLevel.DEBUG -> StatusInfo
                        }
                        Text(
                            text = "[${entry.level}] [${entry.source}] ${entry.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                }
            }
        }
    }
}
