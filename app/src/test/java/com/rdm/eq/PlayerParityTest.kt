package com.rdm.eq

import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.BuiltInPresets
import com.rdm.eq.model.EqBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RDM Eq's equalizer must match RDM Player's (RDM-Player 1.4.0, EqPresets / AudioEffectsManager / EqualizerScreen).
 * The reference values below are copied verbatim from that source.
 */
class PlayerParityTest {
    private val playerFrequencies = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    private val playerLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    private val playerPresets = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
        "Pop" to listOf(-1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f, -1f),
        "Jazz" to listOf(4f, 3f, 1f, 2f, -1f, -1f, 0f, 1f, 3f, 4f),
        "Classical" to listOf(5f, 4f, 3f, 2f, -1f, -1f, 0f, 2f, 3f, 4f),
        "Electronic" to listOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 1f, 4f, 5f),
        "Hip-Hop" to listOf(5f, 4f, 1f, 3f, -1f, -1f, 1f, -1f, 2f, 3f),
        "Vocal" to listOf(-2f, -3f, -3f, 1f, 4f, 4f, 3f, 1f, 0f, -2f),
        "Acoustic" to listOf(4f, 4f, 3f, 1f, 2f, 1f, 3f, 3f, 3f, 2f),
        "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 6f),
    )

    /** RDM Player's EqPresets.effectiveBands(). */
    private fun playerEffective(bands: List<Float>, bass: Float, treble: Float): List<Float> {
        val bassShape = floatArrayOf(1f, 0.9f, 0.6f, 0.3f, 0.1f, 0f, 0f, 0f, 0f, 0f)
        val trebleShape = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.1f, 0.3f, 0.6f, 0.9f, 1f)
        return List(10) { (bands[it] + bass * 8f * bassShape[it] + treble * 8f * trebleShape[it]).coerceIn(-15f, 15f) }
    }

    @Test fun bandsAndRangeMatch() {
        assertEquals(playerFrequencies.map { it.toFloat() }, EqBands.frequenciesHz.toList())
        assertEquals(playerLabels, EqBands.shortLabels)
        assertEquals(-12f, EqBands.MIN_DB); assertEquals(12f, EqBands.MAX_DB)
        assertEquals(-12f, AudioSettings.PREAMP_MIN); assertEquals(12f, AudioSettings.PREAMP_MAX)
        assertEquals(10f, AudioSettings.LOUDNESS_GAIN_MAX)
    }

    @Test fun playerPresetsComeFirstWithIdenticalValues() {
        val first = BuiltInPresets.all.take(playerPresets.size)
        assertEquals(playerPresets.keys.toList(), first.map { it.name })
        first.forEach { assertEquals(it.name, playerPresets[it.name], it.gains) }
    }

    @Test fun bassAndTrebleEnhancementMatchPlayer() {
        val curve = listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)
        for (bass in listOf(0f, 0.25f, 0.6f, 1f)) for (treble in listOf(0f, 0.5f, 1f)) {
            val ours = CurveMath.effectiveGains(AudioSettings(gains = curve, bassBoost = bass, trebleBoost = treble), null)
            val theirs = playerEffective(curve, bass, treble)
            for (i in 0 until 10) assertEquals("bass=$bass treble=$treble band=$i", theirs[i], ours[i], 1e-4f)
        }
    }

    @Test fun bandValueTextMatchesPlayer() {
        assertEquals("+3", EqBands.formatBandDb(3f))
        assertEquals("-2", EqBands.formatBandDb(-2f))
        assertEquals("+0", EqBands.formatBandDb(0f))
        assertEquals("+0", EqBands.formatBandDb(-0f))
        assertEquals("+2.5", EqBands.formatBandDb(2.5f))
    }

    @Test fun loudnessGainOnlyWithLimiter() {
        assertEquals(4f, CurveMath.loudnessGain(AudioSettings(loudnessGainDb = 4f, limiter = true)), 0f)
        assertEquals(0f, CurveMath.loudnessGain(AudioSettings(loudnessGainDb = 4f, limiter = false)), 0f)
    }

    @Test fun untouchedOldPresetCurveMovesToNewValues() {
        val old = BuiltInPresets.valuesIn120.getValue("rock")
        assertEquals(playerPresets["Rock"], BuiltInPresets.migratedGains("rock", old, modified = false))
        // Edited curve, user-modified preset or unknown preset: left alone.
        assertNull(BuiltInPresets.migratedGains("rock", old.mapIndexed { i, g -> if (i == 0) g + 1f else g }, modified = false))
        assertNull(BuiltInPresets.migratedGains("rock", old, modified = true))
        assertNull(BuiltInPresets.migratedGains("balanced", EqBands.flat(), modified = false))
    }
}
