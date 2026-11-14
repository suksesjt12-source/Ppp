package com.stormrevolution.launcher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ChakraAmber
import com.example.ui.theme.ChakraOrange
import com.example.ui.theme.RasenganCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StormObsidian
import com.stormrevolution.launcher.LauncherUiState
import com.stormrevolution.launcher.model.ComponentStatus
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.RuntimeComponentId
import com.stormrevolution.launcher.ui.components.KeyValueRow
import com.stormrevolution.launcher.ui.components.ScreenHeader
import com.stormrevolution.launcher.ui.components.StatusPill
import com.stormrevolution.launcher.ui.components.StatusTone
import com.stormrevolution.launcher.ui.components.StormActionTile
import com.stormrevolution.launcher.ui.components.StormSectionCard
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    uiState: LauncherUiState,
    onContinue: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(950L)
        onContinue()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1429), StormObsidian)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_storm_launcher_fg),
                contentDescription = stringResource(R.string.cd_launcher_emblem),
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(26.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = RasenganCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(220.dp),
                color = ChakraOrange,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Text(
                text = "Bridge: ${uiState.systemInfo.nativeBridgeVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DashboardScreen(
    uiState: LauncherUiState,
    onPlayClick: () -> Unit,
    onNavigateConfig: () -> Unit,
    onNavigateRuntime: () -> Unit,
    onNavigateGameFiles: () -> Unit,
    onNavigateDebug: () -> Unit,
    onNavigateGraphics: () -> Unit,
    onNavigateControls: () -> Unit,
    onNavigateSystemInfo: () -> Unit,
    onNavigateLogs: () -> Unit
) {
    val canPlay = uiState.effectiveStatus == GameStatus.READY_TO_LAUNCH
    val wineComp = uiState.runtimeComponents[RuntimeComponentId.WINE]
    val box64Comp = uiState.runtimeComponents[RuntimeComponentId.BOX64]
    val box86Comp = uiState.runtimeComponents[RuntimeComponentId.BOX86]
    val translatorLabel = when {
        box86Comp?.status == ComponentStatus.INSTALLED && box64Comp?.status == ComponentStatus.INSTALLED ->
            "Box86 + Box64 Verified"
        box64Comp?.status == ComponentStatus.INSTALLED -> "Box64 Verified"
        box86Comp?.status == ComponentStatus.INSTALLED -> "Box86 Verified"
        else -> "Not Installed"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Hero Banner Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isWide) 190.dp else 210.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_storm_banner),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        StormObsidian.copy(alpha = 0.94f),
                                        StormObsidian.copy(alpha = 0.65f),
                                        StormObsidian.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                label = uiState.effectiveStatus.label.uppercase(),
                                tone = statusToneForGame(uiState.effectiveStatus),
                                modifier = Modifier.testTag("dashboard_status_pill")
                            )
                            Text(
                                text = "${uiState.config.resolution.width}×${uiState.config.resolution.height} • ${uiState.config.targetFps.fps} FPS",
                                style = MaterialTheme.typography.labelMedium,
                                color = RasenganCyan
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.game_title_short),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.game_title_full),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onPlayClick,
                                enabled = canPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChakraOrange,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("dashboard_play_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_play),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!canPlay) {
                                Text(
                                    text = "Requires verified NSUNSR.exe & ARM64 runtime",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Status Overview Card
                StormSectionCard {
                    Text(
                        text = "Engine & Hardware Readiness",
                        style = MaterialTheme.typography.titleMedium,
                        color = RasenganCyan
                    )
                    KeyValueRow(
                        key = "Runtime Status",
                        value = if (uiState.effectiveStatus == GameStatus.RUNTIME_MISSING) "Runtime Missing" else uiState.effectiveStatus.label,
                        valueColor = if (canPlay) StatusVerified else StatusWarning
                    )
                    KeyValueRow(
                        key = "Game Installation",
                        value = uiState.gameLibrary.status.label,
                        valueColor = if (uiState.gameLibrary.peResult.isValidPe) StatusVerified else StatusError
                    )
                    KeyValueRow(
                        key = "Game Location",
                        value = uiState.gameLibrary.rootDisplayPath
                    )
                    KeyValueRow(
                        key = "Wine Compatibility",
                        value = wineComp?.status?.label ?: "Not Installed",
                        valueColor = componentColor(wineComp?.status)
                    )
                    KeyValueRow(
                        key = "Box86 / Box64",
                        value = translatorLabel,
                        valueColor = if (translatorLabel == "Not Installed") StatusWarning else StatusVerified
                    )
                    KeyValueRow(
                        key = "Graphics Driver",
                        value = "${uiState.graphicsDecision.selectedDriver.label} (${uiState.graphicsDecision.effectiveBackend.label})",
                        valueColor = if (uiState.graphicsDecision.blockedReason != null) StatusWarning else StatusInfo
                    )
                }
            }

            item {
                // Primary Launcher Actions Grid
                val actions = listOf(
                    Triple(stringResource(R.string.action_configuration), "Profile ${uiState.config.launchProfile.label} • Prefix", Icons.Default.Settings to onNavigateConfig),
                    Triple(stringResource(R.string.action_runtime_manager), "Wine, Box86/64, WoW64, DXVK & RootFS", Icons.Default.Build to onNavigateRuntime),
                    Triple(stringResource(R.string.action_game_files), "SAF Scanner & PE32 Header Inspector", Icons.Default.FolderOpen to onNavigateGameFiles),
                    Triple(stringResource(R.string.action_debug), "${uiState.diagnosticReport.criticalCount} critical • ${uiState.diagnosticReport.warningCount} warn", Icons.Default.BugReport to onNavigateDebug),
                    Triple("GRAPHICS SETTINGS", "DXVK vs WineD3D • Mali-G57 Guard", Icons.Default.Tune to onNavigateGraphics),
                    Triple("CONTROLS SETTINGS", "Virtual Pad Editor & XInput IPC", Icons.Default.Gamepad to onNavigateControls),
                    Triple("SYSTEM INFORMATION", "${uiState.systemInfo.deviceModel} • ${uiState.systemInfo.gpuRenderer}", Icons.Default.Memory to onNavigateSystemInfo),
                    Triple("LOG VIEWER", "Live Wine, DXVK & Diagnostic Stream", Icons.Default.Terminal to onNavigateLogs)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val pairs = if (isWide) actions.chunked(2) else actions.map { listOf(it) }
                    for ((rowIdx, rowItems) in pairs.withIndex()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEachIndexed { colIdx, item ->
                                val tag = "dashboard_tile_${rowIdx}_$colIdx"
                                StormActionTile(
                                    title = item.first,
                                    subtitle = item.second,
                                    icon = item.third.first,
                                    accent = if ((rowIdx + colIdx) % 2 == 0) ChakraOrange else RasenganCyan,
                                    testTag = tag,
                                    onClick = item.third.second,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (isWide && rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameLibraryScreen(
    uiState: LauncherUiState,
    onDirectoryPicked: (Uri) -> Unit,
    onCopyToPrivateStorage: () -> Unit,
    onClearSelection: () -> Unit,
    onBack: () -> Unit
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onDirectoryPicked(uri)
        }
    }

    val game = uiState.gameLibrary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Game Library & File Manager",
                subtitle = "Zero-piracy SAF scanner for user-owned NSUNSR.exe PC installation",
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
                    Text("Installation Verification", style = MaterialTheme.typography.titleMedium)
                    StatusPill(
                        label = game.status.label,
                        tone = statusToneForGame(game.status),
                        modifier = Modifier.testTag("game_library_status_pill")
                    )
                }

                KeyValueRow("Selected Location", game.rootDisplayPath)
                KeyValueRow("Executable", if (game.exeSizeBytes > 0) "${game.exeFileName} (${formatMb(game.exeSizeBytes)})" else "Not found")
                KeyValueRow("PE Architecture", game.peResult.architectureLabel)
                KeyValueRow("PE Summary", game.peResult.summary)
                KeyValueRow("Data Archives (.cpk)", if (game.dataCpkFound) "Detected (${formatMb(game.dataCpkSizeBytes)})" else "Missing")
                KeyValueRow("Config (config.ini)", if (game.configIniFound) "Present" else "Not present (optional)")
                KeyValueRow("Scanned Files", "${game.fileCount} files (${formatMb(game.totalDirectorySizeBytes)})")
                KeyValueRow("App Private Storage", if (game.isPrivateCopy) "Copied & Ready (${game.copiedPrivateExePath})" else "SAF Reference Only")
                KeyValueRow("Internal Free Space", uiState.availableStorageText)

                if (game.validationErrors.isNotEmpty()) {
                    Surface(
                        color = StatusError.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            game.validationErrors.forEach { err ->
                                Text("• $err", style = MaterialTheme.typography.bodySmall, color = StatusError)
                            }
                        }
                    }
                }

                if (uiState.copyProgressText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ChakraOrange)
                        Text(uiState.copyProgressText, style = MaterialTheme.typography.labelMedium, color = ChakraAmber)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("pick_game_folder_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select PC Game Folder")
                    }

                    if (game.peResult.isValidPe && !game.isPrivateCopy && game.rootTreeUri.isNotBlank()) {
                        OutlinedButton(
                            onClick = onCopyToPrivateStorage,
                            enabled = uiState.copyProgressText == null,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("copy_game_private_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy to Private POSIX")
                        }
                    }
                }

                if (game.rootTreeUri.isNotBlank() || game.isPrivateCopy) {
                    OutlinedButton(
                        onClick = onClearSelection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("clear_game_link_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unlink Game Directory")
                    }
                }
            }
        }
    }
}

private fun statusToneForGame(status: GameStatus): StatusTone = when (status) {
    GameStatus.READY_TO_LAUNCH, GameStatus.GAME_READY -> StatusTone.VERIFIED
    GameStatus.GAME_FOUND, GameStatus.RUNTIME_MISSING -> StatusTone.WARNING
    GameStatus.GAME_NOT_FOUND, GameStatus.GAME_VALIDATION_FAILED -> StatusTone.ERROR
}

private fun componentColor(status: ComponentStatus?): Color = when (status) {
    ComponentStatus.INSTALLED -> StatusVerified
    ComponentStatus.VERIFYING, ComponentStatus.DOWNLOADING -> StatusInfo
    ComponentStatus.CORRUPTED, ComponentStatus.INCOMPATIBLE -> StatusError
    else -> StatusWarning
}

private fun formatMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
}
