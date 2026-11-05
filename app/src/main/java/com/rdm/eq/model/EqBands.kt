package com.rdm.eq.model

import com.rdm.eq.dsp.CurveMath

/** The ten user-facing bands of the RDM Eq graphic equalizer (standard ISO octave centres). */
object EqBands {
    const val COUNT = 10
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val STEP_DB = 0.5f

    val frequenciesHz = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val labels = listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")
    val shortLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val roles = listOf("Sub-bass", "Bass", "Punch", "Warmth", "Body", "Mids", "Presence", "Clarity", "Detail", "Air")

    /** The 7-band layout used by RDM Eq 1.0–1.1, kept only to convert saved curves. */
    val legacyFrequenciesHz = floatArrayOf(60f, 150f, 400f, 1000f, 2400f, 6000f, 15000f)

    fun flat(): List<Float> = List(COUNT) { 0f }

    fun clamp(db: Float): Float = db.coerceIn(MIN_DB, MAX_DB)

    /** Rounds to the slider step so stored values match what the UI shows. */
    fun snap(db: Float): Float = clamp(Math.round(db / STEP_DB) * STEP_DB)

    fun normalize(gains: List<Float>): List<Float> =
        List(COUNT) { i -> snap(gains.getOrElse(i) { 0f }) }

    /**
     * Accepts a curve saved by any RDM Eq version: 10 values as-is, 7 values (1.0–1.1)
     * resampled onto the 10 bands so the sound stays the same. Returns null for anything else.
     */
    fun fromStored(gains: List<Float>): List<Float>? = when (gains.size) {
        COUNT -> normalize(gains)
        legacyFrequenciesHz.size -> normalize(frequenciesHz.map { CurveMath.interpolate(it, gains, legacyFrequenciesHz) })
        else -> null
    }

    fun formatHz(hz: Float): String =
        if (hz >= 1000f) {
            val k = hz / 1000f
            if (k == k.toInt().toFloat()) "${k.toInt()} kHz" else String.format(java.util.Locale.US, "%.1f kHz", k)
        } else "${hz.toInt()} Hz"

    fun formatDb(db: Float): String = when {
        db > 0.04f -> String.format(java.util.Locale.US, "+%.1f", db)
        db < -0.04f -> String.format(java.util.Locale.US, "%.1f", db)
        else -> "0.0"
    }
}
