package com.stormrevolution.launcher.manager

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.ControllerState
import com.stormrevolution.launcher.model.GamepadDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class ControllerManager(private val context: Context) {

    private val _state = MutableStateFlow(ControllerState())
    val state: StateFlow<ControllerState> = _state.asStateFlow()

    private val sequence = AtomicInteger(0)
    private var inputSocketFd: Int = -1
    private var activeSocketPath: String = ""

    fun refreshConnectedGamepads(): List<GamepadDeviceInfo> {
        val inputMgr = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            ?: return emptyList()

        val pads = runCatching {
            inputMgr.inputDeviceIds.toList().mapNotNull { id ->
                val dev = inputMgr.getInputDevice(id) ?: return@mapNotNull null
                val src = dev.sources
                val isPad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (isPad) {
                    GamepadDeviceInfo(
                        deviceId = dev.id,
                        name = dev.name ?: "Gamepad #$id",
                        vendorId = dev.vendorId,
                        productId = dev.productId
                    )
                } else {
                    null
                }
            }
        }.getOrDefault(emptyList())

        _state.update { it.copy(connectedGamepads = pads) }
        return pads
    }

    fun openXInputChannel(socketPath: String) {
        closeXInputChannel()
        activeSocketPath = socketPath
        inputSocketFd = NativeBridge.openInputSocket(socketPath)
        _state.update { it.copy(ipcSocketConnected = inputSocketFd >= 0) }
    }

    fun closeXInputChannel() {
        if (inputSocketFd >= 0) {
            NativeBridge.closeInputSocket(inputSocketFd)
            inputSocketFd = -1
        }
        _state.update { it.copy(ipcSocketConnected = false) }
    }

    fun setVirtualButton(bitMask: Int, pressed: Boolean) {
        _state.update { cur ->
            val nextMask = if (pressed) (cur.buttonsMask or bitMask) else (cur.buttonsMask and bitMask.inv())
            cur.copy(buttonsMask = nextMask)
        }
        dispatchCurrentState()
    }

    fun setVirtualTrigger(isLeft: Boolean, pressed: Boolean) {
        val value = if (pressed) 255 else 0
        _state.update { cur ->
            if (isLeft) cur.copy(leftTrigger = value) else cur.copy(rightTrigger = value)
        }
        dispatchCurrentState()
    }

    fun setVirtualStick(isLeft: Boolean, xNorm: Float, yNorm: Float, deadzone: Float) {
        val lx = applyDeadzone(xNorm, deadzone)
        val ly = applyDeadzone(-yNorm, deadzone)
        val sx = (lx.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        val sy = (ly.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        _state.update { cur ->
            if (isLeft) cur.copy(thumbLX = sx, thumbLY = sy) else cur.copy(thumbRX = sx, thumbRY = sy)
        }
        dispatchCurrentState()
    }

    fun handleHardwareKeyEvent(keyCode: Int, pressed: Boolean): Boolean {
        val bit = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE -> 0x1000
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_K -> 0x2000
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_J -> 0x4000
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_I -> 0x8000
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> 0x0100
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> 0x0200
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> 0x0010
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_TAB -> 0x0020
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> 0x0001
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> 0x0002
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> 0x0004
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> 0x0008
            else -> 0
        }
        if (bit == 0) return false
        setVirtualButton(bit, pressed)
        return true
    }

    fun handleHardwareMotionEvent(event: MotionEvent, deadzone: Float): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false
        val x = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X), deadzone)
        val y = applyDeadzone(-event.getAxisValue(MotionEvent.AXIS_Y), deadzone)
        val lt = (event.getAxisValue(MotionEvent.AXIS_LTRIGGER).coerceIn(0f, 1f) * 255f).toInt()
        val rt = (event.getAxisValue(MotionEvent.AXIS_RTRIGGER).coerceIn(0f, 1f) * 255f).toInt()
        _state.update {
            it.copy(
                thumbLX = (x * 32767f).toInt().toShort(),
                thumbLY = (y * 32767f).toInt().toShort(),
                leftTrigger = lt,
                rightTrigger = rt
            )
        }
        dispatchCurrentState()
        return true
    }

    fun clearInputs() {
        _state.update {
            it.copy(
                buttonsMask = 0,
                leftTrigger = 0,
                rightTrigger = 0,
                thumbLX = 0,
                thumbLY = 0,
                thumbRX = 0,
                thumbRY = 0
            )
        }
        dispatchCurrentState()
    }

    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        return if (abs(value) < deadzone) 0f else value
    }

    private fun dispatchCurrentState() {
        if (inputSocketFd < 0 || activeSocketPath.isBlank()) return
        val s = _state.value
        NativeBridge.sendXInputState(
            fd = inputSocketFd,
            socketPath = activeSocketPath,
            buttonsMask = s.buttonsMask,
            leftTrigger = s.leftTrigger,
            rightTrigger = s.rightTrigger,
            thumbLX = s.thumbLX,
            thumbLY = s.thumbLY,
            thumbRX = s.thumbRX,
            thumbRY = s.thumbRY,
            sequence = sequence.incrementAndGet()
        )
    }
}
