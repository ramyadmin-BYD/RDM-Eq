package com.rdm.eq.audio

import android.media.audiofx.Equalizer
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.EqBands

/**
 * Fallback for devices without DynamicsProcessing: the classic fixed-band Equalizer
 * (often 5 bands). The 7-point curve is sampled at the device's real band centres,
 * and those real frequencies are reported on the status screen.
 */
class LegacyEqualizerBackend(override val sessionId: Int, priority: Int) : EffectBackend {
    private val eq = Equalizer(priority, sessionId)
    private val bandCount = eq.numberOfBands.toInt()
    private val centresHz = FloatArray(bandCount) { eq.getCenterFreq(it.toShort()) / 1000f }
    private val range = eq.bandLevelRange // millibels
    override val kind = BackendKind.LEGACY_EQUALIZER
    override var verified = false; private set
    override val supportsBalance = false
    override val supportsLimiter = false
    override val supportsPreamp = true // folded into the bands
    override val hasControl get() = runCatching { eq.hasControl() }.getOrDefault(false)
    override val isEnabled get() = runCatching { eq.enabled }.getOrDefault(false)
    override val channelCount = 0
    var appliedDb: FloatArray = FloatArray(bandCount); private set
    override val detail: String
        get() = "$bandCount hardware bands: " + centresHz.indices.joinToString(", ") {
            "${EqBands.formatHz(centresHz[it])} ${EqBands.formatDb(appliedDb[it])} dB"
        } + "; range ${range[0] / 100}…${range[1] / 100} dB"

    override fun apply(p: EngineParams) {
        appliedDb = CurveMath.mapToHardwareBands(centresHz, p.effectiveGains, p.preampDb, range[0] / 100f, range[1] / 100f)
        for (i in 0 until bandCount) eq.setBandLevel(i.toShort(), (appliedDb[i] * 100).toInt().toShort())
        if (eq.enabled != p.enabled) eq.enabled = p.enabled
        verified = runCatching {
            (0 until bandCount).all { kotlin.math.abs(eq.getBandLevel(it.toShort()) - (appliedDb[it] * 100).toInt()) <= 1 }
        }.getOrDefault(false)
    }

    override fun setControlListener(onChange: () -> Unit) {
        eq.setControlStatusListener { _, _ -> onChange() }
        eq.setEnableStatusListener { _, _ -> onChange() }
    }

    override fun release() = runCatching { eq.release() }.let { }
}
