package com.rdm.eq.audio

/** What every backend receives. Gains are the 7 effective control points (after bass/treble/loudness). */
data class EngineParams(
    val enabled: Boolean,
    val effectiveGains: List<Float>,
    val preampDb: Float,
    val balance: Float,
    val limiter: Boolean,
)

enum class BackendKind(val label: String) {
    DYNAMICS_PROCESSING("Dynamics Processing (7 bands → 24-band DSP)"),
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
    fun apply(p: EngineParams)
    fun setControlListener(onChange: () -> Unit)
    fun release()
}
