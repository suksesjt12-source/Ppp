package com.stormrevolution.launcher.manager

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager as AndroidAudioManager
import com.stormrevolution.launcher.model.AudioBackendMode
import com.stormrevolution.launcher.model.GameConfiguration

class AudioManager(private val context: Context) {

    fun detectAudioOutputs(): List<String> {
        val sysAudio = context.getSystemService(Context.AUDIO_SERVICE) as? AndroidAudioManager
            ?: return listOf("Default Android Audio Sink")
        val devices = runCatching { sysAudio.getDevices(AndroidAudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull()
        if (devices == null || devices.isEmpty()) {
            return listOf("Speaker / Primary Android Sink")
        }
        return devices.map { info ->
            val typeLabel = when (info.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Internal Speaker"
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "3.5mm Wired Audio"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-C Audio"
                else -> "Audio Sink (${info.type})"
            }
            val rates = info.sampleRates?.joinToString(",")?.ifBlank { "48000" } ?: "48000"
            "$typeLabel [${rates}Hz]"
        }.distinct()
    }

    fun buildAudioEnvVars(config: GameConfiguration, runtimeDirPath: String): Map<String, String> {
        return when (config.audioBackend) {
            AudioBackendMode.PULSEAUDIO -> mapOf(
                "STORM_AUDIO_DRIVER" to "pulseaudio",
                "PULSE_SERVER" to "unix:$runtimeDirPath/audio/pulse.sock",
                "PULSE_LATENCY_MSEC" to config.audioBufferMs.coerceIn(24, 200).toString(),
                "WINE_AUDIO_DRIVER" to "pulse",
                "WINE_AUDIO_RATE" to config.audioSampleRateHz.toString()
            )
            AudioBackendMode.ALSA -> mapOf(
                "STORM_AUDIO_DRIVER" to "alsa",
                "WINE_AUDIO_DRIVER" to "alsa",
                "WINE_AUDIO_RATE" to config.audioSampleRateHz.toString()
            )
            AudioBackendMode.DISABLED -> mapOf(
                "STORM_AUDIO_DRIVER" to "null",
                "WINE_AUDIO_DRIVER" to ""
            )
        }
    }
}
