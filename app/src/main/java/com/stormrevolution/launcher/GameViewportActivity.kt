package com.stormrevolution.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ChakraOrange
import com.example.ui.theme.RasenganCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusVerified
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StormLauncherTheme
import com.example.ui.theme.StormObsidian
import com.example.ui.theme.StormSurface
import com.stormrevolution.launcher.model.ControlLayoutConfig
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.LaunchSessionState
import com.stormrevolution.launcher.model.LogEntry
import com.stormrevolution.launcher.model.ProcessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

class GameViewportActivity : ComponentActivity() {

    private lateinit var appContainer: StormAppContainer
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeDeadzone: Float = 0.15f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveFullscreen()

        appContainer = (application as StormApplication).container

        setContent {
            StormLauncherTheme {
                val session by appContainer.processManager.sessionState.collectAsStateWithLifecycle()
                val logs by appContainer.logs.collectAsStateWithLifecycle()
                val controller by appContainer.controllerManager.state.collectAsStateWithLifecycle()

                var config by remember { mutableStateOf(GameConfiguration()) }
                var controls by remember { mutableStateOf(ControlLayoutConfig()) }
                var showPauseMenu by remember { mutableStateOf(false) }
                var showLiveLogs by remember { mutableStateOf(false) }
                var ramFreeMb by remember { mutableStateOf(0L) }
                var tempC by remember { mutableStateOf(-1f) }

                LaunchedEffect(Unit) {
                    config = appContainer.settingsManager.loadConfiguration()
                    controls = appContainer.settingsManager.loadControlLayout()
                    activeDeadzone = controls.deadzone
                    appContainer.controllerManager.refreshConnectedGamepads()
                }

                LaunchedEffect(Unit) {
                    while (true) {
                        val mem = NativeBridge.readProcMemoryKb()
                        ramFreeMb = mem.second / 1024L
                        tempC = NativeBridge.readThermalCelsius()
                        delay(1500L)
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        appContainer.controllerManager.clearInputs()
                    }
                }

                GameViewportContent(
                    config = config,
                    controls = controls,
                    session = session,
                    logs = logs,
                    freeRamMb = ramFreeMb,
                    tempCelsius = tempC,
                    connectedGamepads = controller.connectedGamepads.size,
                    ipcConnected = controller.ipcSocketConnected,
                    showPauseMenu = showPauseMenu,
                    showLiveLogs = showLiveLogs,
                    onTogglePauseMenu = { showPauseMenu = !showPauseMenu },
                    onToggleLiveLogs = { showLiveLogs = !showLiveLogs },
                    onVirtualButton = { bit, pressed ->
                        appContainer.controllerManager.setVirtualButton(bit, pressed)
                    },
                    onVirtualTrigger = { isLeft, pressed ->
                        appContainer.controllerManager.setVirtualTrigger(isLeft, pressed)
                    },
                    onVirtualStick = { nx, ny ->
                        appContainer.controllerManager.setVirtualStick(true, nx, ny, controls.deadzone)
                    },
                    onStopAndExit = { forceKill ->
                        appContainer.processManager.stopGame(forceKill)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (appContainer.controllerManager.handleHardwareKeyEvent(keyCode, true)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (appContainer.controllerManager.handleHardwareKeyEvent(keyCode, false)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null && appContainer.controllerManager.handleHardwareMotionEvent(event, activeDeadzone)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
private fun GameViewportContent(
    config: GameConfiguration,
    controls: ControlLayoutConfig,
    session: LaunchSessionState,
    logs: List<LogEntry>,
    freeRamMb: Long,
    tempCelsius: Float,
    connectedGamepads: Int,
    ipcConnected: Boolean,
    showPauseMenu: Boolean,
    showLiveLogs: Boolean,
    onTogglePauseMenu: () -> Unit,
    onToggleLiveLogs: () -> Unit,
    onVirtualButton: (Int, Boolean) -> Unit,
    onVirtualTrigger: (Boolean, Boolean) -> Unit,
    onVirtualStick: (Float, Float) -> Unit,
    onStopAndExit: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val hideTouchOverlay = controls.autoHideOnPhysicalGamepad && connectedGamepads > 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0C172B), StormObsidian)
                )
            )
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Render Target Viewport Canvas Info
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NSUNSR.exe • ${config.resolution.width}×${config.resolution.height} Viewport",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = session.statusDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = when (session.state) {
                    ProcessState.RUNNING -> StatusVerified
                    ProcessState.FAILED -> StatusError
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (ipcConnected) {
                    "XInput AF_UNIX IPC Linked • Wine Direct3D Surface"
                } else {
                    "Awaiting Wine XInput socket hook • Touch & Hardware Input Armed"
                },
                style = MaterialTheme.typography.labelMedium,
                color = RasenganCyan
            )
        }

        // Top-Left Telemetry Strip
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            color = StormSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PID ${if (session.pid > 0) session.pid else "—"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusVerified
                )
                Text(
                    text = "${config.targetFps.fps} FPS Target",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChakraOrange
                )
                Text(
                    text = "Free RAM ${freeRamMb}MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (tempCelsius > 0f) {
                    Text(
                        text = "${tempCelsius.toInt()}°C",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (tempCelsius >= 45f) StatusWarning else RasenganCyan
                    )
                }
                IconButton(
                    onClick = onToggleLiveLogs,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("viewport_toggle_logs")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Toggle runtime console",
                        tint = RasenganCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onTogglePauseMenu,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("viewport_pause_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Open runtime pause menu",
                        tint = ChakraOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Virtual Gamepad Overlay
        if (controls.showVirtualOverlay && !hideTouchOverlay) {
            for (elem in controls.elements) {
                val centerX = (elem.xFraction * widthPx).roundToInt()
                val centerY = (elem.yFraction * heightPx).roundToInt()
                val sizeDp = (64f * elem.scale).dp
                val halfSizePx = with(density) { (sizeDp / 2).toPx() }.roundToInt()

                if (elem.id == "l_stick") {
                    VirtualAnalogStick(
                        modifier = Modifier
                            .offset { IntOffset(centerX - halfSizePx, centerY - halfSizePx) }
                            .size(sizeDp * 1.45f)
                            .alpha(controls.opacity),
                        onMove = onVirtualStick
                    )
                } else {
                    VirtualActionPadButton(
                        label = elem.label.substringBefore(" •"),
                        modifier = Modifier
                            .offset { IntOffset(centerX - halfSizePx, centerY - halfSizePx) }
                            .size(sizeDp)
                            .alpha(controls.opacity),
                        onPressChanged = { pressed ->
                            if (pressed && controls.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            when (elem.id) {
                                "btn_lt" -> onVirtualTrigger(true, pressed)
                                "btn_rt" -> onVirtualTrigger(false, pressed)
                                else -> if (elem.xInputBit != 0) onVirtualButton(elem.xInputBit, pressed)
                            }
                        }
                    )
                }
            }
        }

        // Live Wine Log Overlay Drawer
        if (showLiveLogs) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.7f)
                    .height(180.dp)
                    .padding(bottom = 12.dp),
                color = StormObsidian.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Runtime Output", style = MaterialTheme.typography.titleSmall, color = RasenganCyan)
                        IconButton(onClick = onToggleLiveLogs, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close logs", tint = Color.White)
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(logs.takeLast(40)) { log ->
                            Text(
                                text = "[${log.source}] ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Pause Modal
        if (showPauseMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = StormSurface,
                    modifier = Modifier.width(340.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gamepad, contentDescription = null, tint = ChakraOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Session Control", style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(
                            text = "Profile: ${config.launchProfile.label} • ${config.graphicsBackend.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onTogglePauseMenu,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resume Session")
                        }
                        OutlinedButton(
                            onClick = { onStopAndExit(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("viewport_stop_graceful")
                        ) {
                            Text("Stop Wine Gracefully & Return")
                        }
                        Button(
                            onClick = { onStopAndExit(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("viewport_force_kill")
                        ) {
                            Text("Force Kill (SIGKILL)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualActionPadButton(
    label: String,
    modifier: Modifier = Modifier,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (pressed) ChakraOrange.copy(alpha = 0.72f) else StormSurface.copy(alpha = 0.78f)
            )
            .border(
                width = 1.5.dp,
                color = if (pressed) RasenganCyan else ChakraOrange.copy(alpha = 0.7f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyDown = event.changes.any { it.pressed }
                        if (anyDown != pressed) {
                            pressed = anyDown
                            onPressChanged(anyDown)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun VirtualAnalogStick(
    modifier: Modifier = Modifier,
    onMove: (Float, Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val maxRadius = size.width * 0.35f
                        val next = knobOffset + dragAmount
                        val dist = hypot(next.x, next.y)
                        knobOffset = if (dist > maxRadius && dist > 0f) {
                            Offset(next.x / dist * maxRadius, next.y / dist * maxRadius)
                        } else {
                            next
                        }
                        if (maxRadius > 0f) {
                            onMove(
                                (knobOffset.x / maxRadius).coerceIn(-1f, 1f),
                                (knobOffset.y / maxRadius).coerceIn(-1f, 1f)
                            )
                        }
                    }
                )
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = StormSurface.copy(alpha = 0.75f),
            radius = size.minDimension / 2f,
            center = center
        )
        drawCircle(
            color = RasenganCyan.copy(alpha = 0.65f),
            radius = size.minDimension / 2f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
        drawCircle(
            color = ChakraOrange.copy(alpha = 0.9f),
            radius = size.minDimension * 0.24f,
            center = center + knobOffset
        )
    }
}
