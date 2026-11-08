package com.rdm.eq.audio

/** What every backend receives. Gains are the 10 effective control points (after bass/treble/loudness). */
data class EngineParams(
    val enabled: Boolean,
    val effectiveGains: List<Float>,
    val preampDb: Float,
    val balance: Float,
    val limiter: Boolean,
    /** Extra input gain held by the limiter (0 when the limiter is off). */
    val loudnessGainDb: Float = 0f,
    val normalization: Boolean = false,
)

enum class BackendKind(val label: String) {
    DYNAMICS_PROCESSING("Dynamics Processing (10 bands → 31-band DSP)"),
    LEGACY_EQUALIZER("Classic Android Equalizer"),
}

/** One audio effect instance attached to one audio session. */
interface EffectBackend {
    val sessionId: Int
    val kind: BackendKind
    val hasControl: Boolean
    val isEnabled: Boolean
    /** Channel count the effect runs at (DP only; 0 if unknown). */
    val channelCount: Int
    /** Human-readable description of what is actually applied (bands, ranges). */
    val detail: String
    /** True once parameters written to the effect were read back and matched. */
    val verified: Boolean
    val supportsBalance: Boolean
    val supportsLimiter: Boolean
    val supportsPreamp: Boolean
    /** Loudness gain needs the limiter; normalization needs the multi-band compressor stage. */
    val supportsLoudnessGain: Boolean get() = false
    val supportsNormalization: Boolean get() = false
    fun apply(p: EngineParams)
    fun setControlListener(onChange: () -> Unit)
    fun release()
}
