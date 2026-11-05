package com.rdm.eq.audio

import android.media.audiofx.DynamicsProcessing
import com.rdm.eq.dsp.CurveMath
import kotlin.math.abs

/**
 * Main engine: Android's DynamicsProcessing effect (API 28+).
 * The 7 control points are interpolated onto 24 log-spaced pre-EQ bands for a smooth curve,
 * with a master input gain (preamp / balance) and an output limiter.
 */
class DynamicsProcessingBackend(override val sessionId: Int, priority: Int) : EffectBackend {
    private val cutoffs = CurveMath.dpBandCutoffs(BANDS)
    private val dp: DynamicsProcessing
    override val kind = BackendKind.DYNAMICS_PROCESSING
    override var verified = false; private set
    override val supportsBalance get() = channelCount == 2
    override val supportsLimiter = true
    override val supportsPreamp = true
    private var lastGains: FloatArray? = null
    private var lastInput: Pair<Float, Float>? = null
    private var lastLimiter: Boolean? = null

    init {
        val eq = DynamicsProcessing.Eq(true, true, BANDS).apply {
            for (k in 0 until BANDS) setBand(k, DynamicsProcessing.EqBand(true, cutoffs[k], 0f))
        }
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // the framework adapts this to the stream's real channel count
            true, BANDS, false, 0, false, 0, true,
        )
            .setPreferredFrameDuration(10f)
            .setPreEqAllChannelsTo(eq)
            .setLimiterAllChannelsTo(limiter(true))
            .build()
        dp = DynamicsProcessing(priority, sessionId, cfg)
    }

    override val hasControl get() = runCatching { dp.hasControl() }.getOrDefault(false)
    override val isEnabled get() = runCatching { dp.enabled }.getOrDefault(false)
    override val channelCount get() = runCatching { dp.channelCount }.getOrDefault(0)
    override val detail get() = "$BANDS-band pre-EQ 20 Hz–20 kHz, input gain, limiter; ${channelCount}-channel stream"

    private fun limiter(enabled: Boolean) =
        DynamicsProcessing.Limiter(true, enabled, 0, 1f, 60f, 10f, -1f, 0f)

    override fun apply(p: EngineParams) {
        val gains = CurveMath.dpBandGains(p.effectiveGains, p.preampDb, cutoffs)
        if (!gains.contentEquals(lastGains)) {
            for (k in 0 until BANDS) dp.setPreEqBandAllChannelsTo(k, DynamicsProcessing.EqBand(true, cutoffs[k], gains[k]))
            lastGains = gains
        }
        val ch = channelCount
        val (balL, balR) = if (ch == 2) CurveMath.balanceGains(p.balance) else 0f to 0f
        val input = (p.preampDb + balL) to (p.preampDb + balR)
        if (input != lastInput) {
            if (ch == 2) {
                dp.setInputGainbyChannel(0, input.first)
                dp.setInputGainbyChannel(1, input.second)
            } else dp.setInputGainAllChannelsTo(p.preampDb)
            lastInput = input
        }
        if (p.limiter != lastLimiter) {
            dp.setLimiterAllChannelsTo(limiter(p.limiter))
            lastLimiter = p.limiter
        }
        if (dp.enabled != p.enabled) dp.enabled = p.enabled
        // Read back from the native effect to prove the parameters really landed.
        verified = runCatching {
            val mid = BANDS / 2
            abs(dp.getPreEqBandByChannelIndex(0, mid).gain - gains[mid]) < 0.1f &&
                abs(dp.getInputGainByChannelIndex(0) - input.first) < 0.1f
        }.getOrDefault(false)
    }

    override fun setControlListener(onChange: () -> Unit) {
        dp.setControlStatusListener { _, _ -> onChange() }
        dp.setEnableStatusListener { _, _ -> onChange() }
    }

    override fun release() = runCatching { dp.release() }.let { }

    companion object { const val BANDS = 24 }
}
