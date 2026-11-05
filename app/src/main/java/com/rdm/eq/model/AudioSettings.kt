package com.rdm.eq.model

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class EnginePreference(val label: String) {
    AUTO("Automatic (Dynamics Processing, else classic EQ)"),
    DYNAMICS_PROCESSING("Dynamics Processing only"),
    LEGACY_EQUALIZER("Classic Android Equalizer only"),
}

data class AudioSettings(
    val enabled: Boolean = true,
    val gains: List<Float> = EqBands.flat(),
    /** Preset the current curve came from; empty when the curve is not tied to a preset. */
    val activePresetId: String = BuiltInPresets.FLAT_ID,
    val autoHeadroom: Boolean = true,
    val preampDb: Float = 0f,
    /** 0..1, adds up to +6 dB at 60 Hz. */
    val bassBoost: Float = 0f,
    /** 0..1, adds up to +5 dB at 15 kHz. */
    val trebleBoost: Float = 0f,
    val loudness: Boolean = false,
    /** -1 (left) .. +1 (right). */
    val balance: Float = 0f,
    val limiter: Boolean = true,
    /** Attach to sessions that media apps announce. */
    val sessionMode: Boolean = true,
    /** Experimental: attach to the global output mix (session 0). */
    val globalMode: Boolean = false,
    val engine: EnginePreference = EnginePreference.AUTO,
    val theme: ThemeMode = ThemeMode.DARK,
    /** Preset to load when the app process starts; empty = keep last used curve. */
    val startupPresetId: String = "",
    val autoStart: Boolean = true,
) {
    companion object {
        const val PREAMP_MIN = -12f
        const val PREAMP_MAX = 6f
    }
}
