package com.rdm.eq.dsp

import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.EqBands
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

enum class ClipRisk { NONE, LOW, HIGH }

/**
 * Pure maths that turns the 10 user control points into what each audio backend needs.
 * Kept free of Android classes so it is covered by JVM unit tests.
 */
object CurveMath {
    // Contribution of each "enhancement" at full strength, per control point.
    // Bass and treble match RDM Player exactly (8 dB × its shelf shapes).
    //                                  31    62    125   250   500   1k    2k    4k    8k    16k
    val BASS_SHAPE = floatArrayOf(      8f,  7.2f, 4.8f, 2.4f, 0.8f, 0f,   0f,   0f,   0f,   0f)
    val TREBLE_SHAPE = floatArrayOf(    0f,   0f,   0f,   0f,   0f,  0.8f, 2.4f, 4.8f, 7.2f, 8f)
    val LOUDNESS_SHAPE = floatArrayOf(  5f,   6f,   3f,   1f,   0f,   0f,   0f,  0.5f, 1f,   3f)

    /** Same ceiling as RDM Player's effectiveBands(). */
    const val EFFECTIVE_LIMIT_DB = 15f

    /** Gain at [freqHz]: linear interpolation on a log-frequency axis, flat beyond the end points. */
    fun interpolate(freqHz: Float, gains: List<Float>, freqs: FloatArray = EqBands.frequenciesHz): Float {
        require(gains.size == freqs.size) { "gains/freqs size mismatch" }
        if (freqHz <= freqs.first()) return gains.first()
        if (freqHz >= freqs.last()) return gains.last()
        val lf = ln(freqHz)
        for (i in 0 until freqs.size - 1) {
            if (freqHz <= freqs[i + 1]) {
                val a = ln(freqs[i]); val b = ln(freqs[i + 1])
                val t = (lf - a) / (b - a)
                return gains[i] + (gains[i + 1] - gains[i]) * t
            }
        }
        return gains.last()
    }

    /** How much loudness compensation to apply at a media volume (0..1): full at silence, none from 60 %. */
    fun loudnessAmount(volumeFraction: Float): Float = ((0.6f - volumeFraction) / 0.6f).coerceIn(0f, 1f)

    /** User curve plus bass/treble/loudness additions = what the DSP really applies (before preamp). */
    fun effectiveGains(s: AudioSettings, volumeFraction: Float?): List<Float> {
        val loud = if (s.loudness && volumeFraction != null) loudnessAmount(volumeFraction) else 0f
        return List(EqBands.COUNT) { i ->
            val g = s.gains.getOrElse(i) { 0f } +
                s.bassBoost.coerceIn(0f, 1f) * BASS_SHAPE[i] +
                s.trebleBoost.coerceIn(0f, 1f) * TREBLE_SHAPE[i] +
                loud * LOUDNESS_SHAPE[i]
            g.coerceIn(-EFFECTIVE_LIMIT_DB, EFFECTIVE_LIMIT_DB)
        }
    }

    /**
     * Make-up gain for "Loudness". It is only applied while the limiter is on, because it relies on the
     * limiter to hold peaks below -1 dBFS (the same idea as Android's LoudnessEnhancer).
     */
    fun loudnessGain(s: AudioSettings): Float =
        if (s.limiter) s.loudnessGainDb.coerceIn(0f, AudioSettings.LOUDNESS_GAIN_MAX) else 0f

    /** Negative preamp that cancels the largest boost, so the curve never adds level. */
    fun autoPreamp(effective: List<Float>): Float = -max(0f, effective.maxOrNull() ?: 0f)

    fun resolvedPreamp(s: AudioSettings, effective: List<Float>): Float =
        if (s.autoHeadroom) autoPreamp(effective)
        else s.preampDb.coerceIn(AudioSettings.PREAMP_MIN, AudioSettings.PREAMP_MAX)

    /** Worst-case extra level vs. the original signal (linear interpolation never exceeds a control point). */
    fun peakGainDb(effective: List<Float>, preampDb: Float): Float = (effective.maxOrNull() ?: 0f) + preampDb

    fun clipRisk(effective: List<Float>, preampDb: Float): ClipRisk {
        val peak = peakGainDb(effective, preampDb)
        return when {
            peak <= 0.05f -> ClipRisk.NONE
            peak <= 3f -> ClipRisk.LOW
            else -> ClipRisk.HIGH
        }
    }

    /** Upper band edges for the DynamicsProcessing pre-EQ: [count] log-spaced bands from 20 Hz to 20 kHz. */
    fun dpBandCutoffs(count: Int = 31, minHz: Float = 20f, maxHz: Float = 20000f): FloatArray {
        require(count >= 2)
        val ratio = maxHz / minHz
        return FloatArray(count) { k -> minHz * ratio.pow((k + 1).toFloat() / count) }
    }

    /**
     * Gain for each DynamicsProcessing band, sampled at the band's geometric centre.
     * Boosts below 30 Hz are tapered to protect car speakers from inaudible subsonic energy.
     */
    fun dpBandGains(effective: List<Float>, preampDb: Float, cutoffs: FloatArray): FloatArray {
        val out = FloatArray(cutoffs.size)
        for (k in cutoffs.indices) {
            val lower = if (k == 0) cutoffs[0] / (cutoffs[1] / cutoffs[0]) else cutoffs[k - 1]
            val centre = sqrt(lower * cutoffs[k])
            var g = interpolate(centre, effective)
            if (g > 0f && centre < 30f) g *= ((centre - 15f) / 15f).coerceIn(0f, 1f)
            out[k] = g
        }
        return out
    }

    /**
     * Maps the curve onto a fixed-band hardware Equalizer (e.g. 5 bands at 60/230/910/3600/14000 Hz).
     * The preamp is folded into every band because the classic Equalizer has no master gain.
     */
    fun mapToHardwareBands(centresHz: FloatArray, effective: List<Float>, preampDb: Float, minDb: Float, maxDb: Float): FloatArray =
        FloatArray(centresHz.size) { i -> (interpolate(centresHz[i], effective) + preampDb).coerceIn(minDb, maxDb) }

    /**
     * Per-channel input gains for stereo balance. Only ever attenuates the opposite side
     * (never boosts), up to -24 dB at full pan.
     */
    fun balanceGains(balance: Float): Pair<Float, Float> {
        val b = balance.coerceIn(-1f, 1f)
        val left = if (b > 0f) -24f * b else 0f
        val right = if (b < 0f) 24f * b else 0f
        return left to right
    }

    fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
}
