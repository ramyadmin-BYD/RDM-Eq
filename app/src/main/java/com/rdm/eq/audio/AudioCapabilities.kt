package com.rdm.eq.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build

data class EffectInfo(val name: String, val implementor: String, val type: String)

/** What this head unit actually offers, probed at runtime (never assumed). */
data class AudioCapabilities(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val androidRelease: String,
    val sdkInt: Int,
    val buildDisplay: String,
    val isAndroidAutomotive: Boolean,
    val dynamicsProcessing: Boolean,
    val equalizer: Boolean,
    val bassBoost: Boolean,
    val virtualizer: Boolean,
    val loudnessEnhancer: Boolean,
    val effects: List<EffectInfo>,
    val outputSampleRate: String,
    val framesPerBuffer: String,
    val outputDevices: List<String>,
) {
    companion object {
        fun probe(context: Context): AudioCapabilities {
            val descriptors = runCatching { AudioEffect.queryEffects()?.toList() }.getOrNull().orEmpty()
            fun has(type: java.util.UUID) = descriptors.any { it.type == type }
            val am = context.getSystemService(AudioManager::class.java)
            val devices = runCatching {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { d ->
                    "${deviceTypeName(d.type)}${d.productName?.let { n -> " ($n)" } ?: ""}" +
                        (d.channelCounts.takeIf { it.isNotEmpty() }?.let { c -> " ch=${c.joinToString("/")}" } ?: "")
                }
            }.getOrDefault(emptyList())
            return AudioCapabilities(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                brand = Build.BRAND,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                buildDisplay = Build.DISPLAY,
                isAndroidAutomotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
                dynamicsProcessing = has(AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING),
                equalizer = has(AudioEffect.EFFECT_TYPE_EQUALIZER),
                bassBoost = has(AudioEffect.EFFECT_TYPE_BASS_BOOST),
                virtualizer = has(AudioEffect.EFFECT_TYPE_VIRTUALIZER),
                loudnessEnhancer = has(AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER),
                effects = descriptors.map { EffectInfo(it.name, it.implementor, typeName(it.type)) },
                outputSampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "?",
                framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "?",
                outputDevices = devices,
            )
        }

        private fun typeName(t: java.util.UUID) = when (t) {
            AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING -> "Dynamics Processing"
            AudioEffect.EFFECT_TYPE_EQUALIZER -> "Equalizer"
            AudioEffect.EFFECT_TYPE_BASS_BOOST -> "Bass Boost"
            AudioEffect.EFFECT_TYPE_VIRTUALIZER -> "Virtualizer"
            AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER -> "Loudness Enhancer"
            AudioEffect.EFFECT_TYPE_PRESET_REVERB -> "Preset Reverb"
            AudioEffect.EFFECT_TYPE_ENV_REVERB -> "Environmental Reverb"
            AudioEffect.EFFECT_TYPE_AEC -> "Echo Canceller"
            AudioEffect.EFFECT_TYPE_AGC -> "Auto Gain Control"
            AudioEffect.EFFECT_TYPE_NS -> "Noise Suppressor"
            else -> t.toString()
        }

        private fun deviceTypeName(t: Int) = when (t) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
            AudioDeviceInfo.TYPE_BUS -> "Audio bus (car zone)"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line out (analog)"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line out (digital)"
            AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote submix"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            else -> "Type $t"
        }
    }
}
