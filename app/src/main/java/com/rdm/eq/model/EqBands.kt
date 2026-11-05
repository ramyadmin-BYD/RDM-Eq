package com.rdm.eq.model

/** The seven user-facing control points of the RDM Eq graphic equalizer. */
object EqBands {
    const val COUNT = 7
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val STEP_DB = 0.5f

    val frequenciesHz = floatArrayOf(60f, 150f, 400f, 1000f, 2400f, 6000f, 15000f)
    val labels = listOf("60 Hz", "150 Hz", "400 Hz", "1 kHz", "2.4 kHz", "6 kHz", "15 kHz")
    val roles = listOf("Sub-bass", "Punch", "Warmth", "Mids", "Vocals", "Detail", "Air")

    fun flat(): List<Float> = List(COUNT) { 0f }

    fun clamp(db: Float): Float = db.coerceIn(MIN_DB, MAX_DB)

    /** Rounds to the slider step so stored values match what the UI shows. */
    fun snap(db: Float): Float = clamp(Math.round(db / STEP_DB) * STEP_DB)

    fun normalize(gains: List<Float>): List<Float> =
        List(COUNT) { i -> snap(gains.getOrElse(i) { 0f }) }

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
