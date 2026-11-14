package com.stormrevolution.launcher.ui

import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stormrevolution.launcher.GameViewportActivity
import com.stormrevolution.launcher.LauncherViewModel
import com.stormrevolution.launcher.ui.screens.ControlsSettingsScreen
import com.stormrevolution.launcher.ui.screens.DashboardScreen
import com.stormrevolution.launcher.ui.screens.DebugConsoleScreen
import com.stormrevolution.launcher.ui.screens.GameConfigScreen
import com.stormrevolution.launcher.ui.screens.GameLibraryScreen
import com.stormrevolution.launcher.ui.screens.GraphicsSettingsScreen
import com.stormrevolution.launcher.ui.screens.LogViewerScreen
import com.stormrevolution.launcher.ui.screens.RuntimeManagerScreen
import com.stormrevolution.launcher.ui.screens.SplashScreen
import com.stormrevolution.launcher.ui.screens.SystemInfoScreen
import kotlinx.serialization.Serializable

@Serializable object SplashRoute
@Serializable object DashboardRoute
@Serializable object GameLibraryRoute
@Serializable object GameConfigRoute
@Serializable object RuntimeManagerRoute
@Serializable object GraphicsSettingsRoute
@Serializable object ControlsSettingsRoute
@Serializable object SystemInfoRoute
@Serializable object DebugConsoleRoute
@Serializable object LogViewerRoute

private data class TopNavItem(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun StormLauncherRoot(viewModel: LauncherViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val controllerState by viewModel.controllerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.bannerFeedback) {
        val msg = uiState.bannerFeedback
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBannerFeedback()
        }
    }

    val isSplash = currentDest?.hasRoute(SplashRoute::class) == true

    val navItems = remember {
        listOf(
            TopNavItem(DashboardRoute, "Home", Icons.Default.Dashboard, "nav_dashboard"),
            TopNavItem(GameLibraryRoute, "Files", Icons.Default.FolderOpen, "nav_files"),
            TopNavItem(RuntimeManagerRoute, "Runtime", Icons.Default.Build, "nav_runtime"),
            TopNavItem(GameConfigRoute, "Config", Icons.Default.Settings, "nav_config"),
            TopNavItem(DebugConsoleRoute, "Debug", Icons.Default.BugReport, "nav_debug")
        )
    }

    val navigateTopLevel: (Any) -> Unit = { target ->
        navController.navigate(target) {
            popUpTo<DashboardRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 840.dp && !isSplash

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isSplash && !useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = SplashRoute,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) }
                ) {
                    composable<SplashRoute> {
                        SplashScreen(
                            uiState = uiState,
                            onContinue = {
                                navController.navigate(DashboardRoute) {
                                    popUpTo<SplashRoute> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<DashboardRoute> {
                        DashboardScreen(
                            uiState = uiState,
                            onPlayClick = {
                                if (viewModel.launchGameSession()) {
                                    context.startActivity(Intent(context, GameViewportActivity::class.java))
                                }
                            },
                            onNavigateConfig = { navController.navigate(GameConfigRoute) },
                            onNavigateRuntime = { navController.navigate(RuntimeManagerRoute) },
                            onNavigateGameFiles = { navController.navigate(GameLibraryRoute) },
                            onNavigateDebug = { navController.navigate(DebugConsoleRoute) },
                            onNavigateGraphics = { navController.navigate(GraphicsSettingsRoute) },
                            onNavigateControls = { navController.navigate(ControlsSettingsRoute) },
                            onNavigateSystemInfo = { navController.navigate(SystemInfoRoute) },
                            onNavigateLogs = { navController.navigate(LogViewerRoute) }
                        )
                    }

                    composable<GameLibraryRoute> {
                        GameLibraryScreen(
                            uiState = uiState,
                            onDirectoryPicked = viewModel::onGameDirectorySelected,
                            onCopyToPrivateStorage = viewModel::copyLinkedGameToPrivateStorage,
                            onClearSelection = viewModel::clearGameSelection,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<GameConfigRoute> {
                        GameConfigScreen(
                            uiState = uiState,
                            onApplyPreset = viewModel::applyPerformancePreset,
                            onUpdateConfig = viewModel::updateConfiguration,
                            onInitializePrefix = viewModel::initializeActivePrefix,
                            onResetPrefix = viewModel::resetActivePrefix,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<RuntimeManagerRoute> {
                        RuntimeManagerScreen(
                            uiState = uiState,
                            onImportPackage = viewModel::importRuntimePackage,
                            onRemoveComponent = viewModel::removeRuntimeComponent,
                            onReverifyAll = viewModel::refreshRuntimeAndDiagnostics,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<GraphicsSettingsRoute> {
                        GraphicsSettingsScreen(
                            uiState = uiState,
                            onUpdateConfig = viewModel::updateConfiguration,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<ControlsSettingsRoute> {
                        ControlsSettingsScreen(
                            uiState = uiState,
                            controllerState = controllerState,
                            onUpdateControls = viewModel::updateControlLayout,
                            onResetControls = viewModel::resetControlsToDefault,
                            onRefreshGamepads = viewModel::refreshConnectedGamepads,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<SystemInfoRoute> {
                        SystemInfoScreen(
                            uiState = uiState,
                            onRefresh = viewModel::refreshRuntimeAndDiagnostics,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<DebugConsoleRoute> {
                        DebugConsoleScreen(
                            uiState = uiState,
                            onRunDiagnostics = viewModel::refreshRuntimeAndDiagnostics,
                            onBuildSanitizedReport = viewModel::buildSanitizedExportLogs,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<LogViewerRoute> {
                        LogViewerScreen(
                            logs = logs,
                            onClearLogs = viewModel::clearLogs,
                            onBuildSanitizedLogs = viewModel::buildSanitizedExportLogs,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
