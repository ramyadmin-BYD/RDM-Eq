package com.rdm.eq.audio

import android.media.audiofx.DynamicsProcessing
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.util.DiagLog
import kotlin.math.abs

/**
 * Main engine: Android's DynamicsProcessing effect (API 28+).
 * The 10 bands are interpolated onto 31 log-spaced (about 1/3-octave) pre-EQ bands for a smooth curve,
 * with a master input gain (preamp / balance / loudness), a one-band compressor for volume
 * normalization, and an output limiter.
 */
class DynamicsProcessingBackend(override val sessionId: Int, priority: Int) : EffectBackend {
    private val cutoffs = CurveMath.dpBandCutoffs(BANDS)
    private val dp: DynamicsProcessing
    /** False if the device refused the compressor stage and we fell back to EQ + limiter only. */
    private val hasMbc: Boolean
    override val kind = BackendKind.DYNAMICS_PROCESSING
    override var verified = false; private set
    override val supportsBalance get() = channelCount == 2
    override val supportsLimiter = true
    override val supportsPreamp = true
    override val supportsLoudnessGain = true
    override val supportsNormalization get() = hasMbc
    private var lastGains: FloatArray? = null
    private var lastInput: Pair<Float, Float>? = null
    private var lastLimiter: Boolean? = null
    private var lastNormalization: Boolean? = null

    init {
        var mbcOk = true
        dp = try {
            DynamicsProcessing(priority, sessionId, config(withMbc = true))
        } catch (t: Throwable) {
            DiagLog.w("Compressor stage refused on session $sessionId (${t.message}); using EQ + limiter only")
            mbcOk = false
            DynamicsProcessing(priority, sessionId, config(withMbc = false))
        }
        hasMbc = mbcOk
    }

    private fun config(withMbc: Boolean): DynamicsProcessing.Config {
        val eq = DynamicsProcessing.Eq(true, true, BANDS).apply {
            for (k in 0 until BANDS) setBand(k, DynamicsProcessing.EqBand(true, cutoffs[k], 0f))
        }
        val b = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // the framework adapts this to the stream's real channel count
            true, BANDS, withMbc, if (withMbc) 1 else 0, false, 0, true,
        )
            .setPreferredFrameDuration(10f)
            .setPreEqAllChannelsTo(eq)
            .setLimiterAllChannelsTo(limiter(true))
        if (withMbc) b.setMbcAllChannelsTo(DynamicsProcessing.Mbc(true, true, 1).apply { setBand(0, mbcBand(false)) })
        return b.build()
    }

    override val hasControl get() = runCatching { dp.hasControl() }.getOrDefault(false)
    override val isEnabled get() = runCatching { dp.enabled }.getOrDefault(false)
    override val channelCount get() = runCatching { dp.channelCount }.getOrDefault(0)
    override val detail get() = "$BANDS-band pre-EQ 20 Hz–20 kHz, input gain, " +
        (if (hasMbc) "compressor, " else "") + "limiter; ${channelCount}-channel stream"

    private fun limiter(enabled: Boolean) =
        DynamicsProcessing.Limiter(true, enabled, 0, 1f, 60f, 10f, -1f, 0f)

    /** RDM Player's "volume normalization" compressor, parameter for parameter. */
    private fun mbcBand(on: Boolean) = DynamicsProcessing.MbcBand(
        on, 20000f, 10f, 200f, if (on) 3f else 1f,
        -24f, 6f, -90f, 1f, 0f, if (on) 6f else 0f,
    )

    override fun apply(p: EngineParams) {
        val gains = CurveMath.dpBandGains(p.effectiveGains, p.preampDb, cutoffs)
        if (!gains.contentEquals(lastGains)) {
            for (k in 0 until BANDS) dp.setPreEqBandAllChannelsTo(k, DynamicsProcessing.EqBand(true, cutoffs[k], gains[k]))
            lastGains = gains
        }
        val ch = channelCount
        val (balL, balR) = if (ch == 2) CurveMath.balanceGains(p.balance) else 0f to 0f
        // Loudness gain only makes sense with the limiter holding the peaks.
        val master = p.preampDb + if (p.limiter) p.loudnessGainDb else 0f
        val input = (master + balL) to (master + balR)
        if (input != lastInput) {
            if (ch == 2) {
                dp.setInputGainbyChannel(0, input.first)
                dp.setInputGainbyChannel(1, input.second)
            } else dp.setInputGainAllChannelsTo(master)
            lastInput = input
        }
        if (p.limiter != lastLimiter) {
            dp.setLimiterAllChannelsTo(limiter(p.limiter))
            lastLimiter = p.limiter
        }
        if (hasMbc && p.normalization != lastNormalization) {
            dp.setMbcBandAllChannelsTo(0, mbcBand(p.normalization))
            lastNormalization = p.normalization
        }
        if (dp.enabled != p.enabled) dp.enabled = p.enabled
        // Read back from the native effect to prove the parameters really landed.
        verified = runCatching {
            val mid = BANDS / 2
            abs(dp.getPreEqBandByChannelIndex(0, mid).gain - gains[mid]) < 0.1f &&
                abs(dp.getInputGainByChannelIndex(0) - input.first) < 0.1f &&
                (!hasMbc || dp.getMbcBandByChannelIndex(0, 0).isEnabled == p.normalization)
        }.getOrDefault(false)
    }

    override fun setControlListener(onChange: () -> Unit) {
        dp.setControlStatusListener { _, _ -> onChange() }
        dp.setEnableStatusListener { _, _ -> onChange() }
    }

    override fun release() = runCatching { dp.release() }.let { }

    companion object { const val BANDS = 31 }
}
