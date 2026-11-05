package com.rdm.eq

import com.rdm.eq.dsp.ClipRisk
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.EqBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveMathTest {
    private val g = listOf(6f, 3f, 0f, -3f, 0f, 2f, 4f)

    @Test fun interpolationHitsControlPointsExactly() {
        EqBands.frequenciesHz.forEachIndexed { i, f -> assertEquals(g[i], CurveMath.interpolate(f, g), 1e-4f) }
    }

    @Test fun interpolationIsFlatOutsideRange() {
        assertEquals(6f, CurveMath.interpolate(20f, g), 1e-4f)
        assertEquals(4f, CurveMath.interpolate(20000f, g), 1e-4f)
    }

    @Test fun interpolationIsBetweenNeighboursOnLogAxis() {
        val mid = kotlin.math.sqrt(60f * 150f) // geometric midpoint
        assertEquals(4.5f, CurveMath.interpolate(mid, g), 1e-3f)
    }

    @Test fun bassAndTrebleBoostAddShapes() {
        val s = AudioSettings(gains = EqBands.flat(), bassBoost = 1f, trebleBoost = 0.5f)
        val e = CurveMath.effectiveGains(s, null)
        assertEquals(6f, e[0], 1e-4f); assertEquals(4f, e[1], 1e-4f); assertEquals(0f, e[3], 1e-4f)
        assertEquals(2.5f, e[6], 1e-4f)
    }

    @Test fun loudnessOnlyWhenEnabledAndVolumeKnown() {
        val s = AudioSettings(loudness = true)
        assertEquals(0f, CurveMath.effectiveGains(s, null)[0], 1e-4f)
        assertEquals(6f, CurveMath.effectiveGains(s, 0f)[0], 1e-4f)
        assertEquals(0f, CurveMath.effectiveGains(s, 0.8f)[0], 1e-4f)
        assertEquals(0f, CurveMath.effectiveGains(s.copy(loudness = false), 0f)[0], 1e-4f)
    }

    @Test fun autoPreampCancelsLargestBoost() {
        assertEquals(-6f, CurveMath.autoPreamp(g), 1e-4f)
        assertEquals(0f, CurveMath.autoPreamp(listOf(-1f, -2f, 0f, 0f, 0f, 0f, 0f)), 1e-4f)
        assertEquals(ClipRisk.NONE, CurveMath.clipRisk(g, -6f))
        assertEquals(ClipRisk.LOW, CurveMath.clipRisk(g, -4f))
        assertEquals(ClipRisk.HIGH, CurveMath.clipRisk(g, 0f))
    }

    @Test fun resolvedPreampHonoursManualMode() {
        val s = AudioSettings(gains = g, autoHeadroom = false, preampDb = -2f)
        assertEquals(-2f, CurveMath.resolvedPreamp(s, g), 1e-4f)
        assertEquals(-6f, CurveMath.resolvedPreamp(s.copy(autoHeadroom = true), g), 1e-4f)
    }

    @Test fun dpCutoffsAscendAndCoverAudioBand() {
        val c = CurveMath.dpBandCutoffs(24)
        assertEquals(24, c.size)
        for (i in 1 until c.size) assertTrue(c[i] > c[i - 1])
        assertEquals(20000f, c.last(), 1f)
        assertTrue(c.first() < 30f)
    }

    @Test fun dpBandGainsFollowCurveAndTaperSubsonic() {
        val c = CurveMath.dpBandCutoffs(24)
        val out = CurveMath.dpBandGains(listOf(10f, 10f, 10f, 10f, 10f, 10f, 10f), 0f, c)
        assertTrue("lowest band should be tapered", out[0] < 10f)
        assertEquals(10f, out[12], 1e-3f)
    }

    @Test fun hardwareMappingUsesRealCentresAndClamps() {
        val centres = floatArrayOf(60f, 230f, 910f, 3600f, 14000f) // common 5-band AOSP Equalizer
        val m = CurveMath.mapToHardwareBands(centres, listOf(20f, 0f, 0f, 0f, 0f, 0f, 0f), -2f, -15f, 15f)
        assertEquals(15f, m[0], 1e-4f) // clamped to hardware max
        assertEquals(-2f, m[4], 1e-4f) // preamp folded in
    }

    @Test fun balanceOnlyAttenuates() {
        assertEquals(0f to 0f, CurveMath.balanceGains(0f))
        val (l, r) = CurveMath.balanceGains(0.5f)
        assertEquals(-12f, l, 1e-4f); assertEquals(0f, r, 1e-4f)
        val (l2, r2) = CurveMath.balanceGains(-1f)
        assertEquals(0f, l2, 1e-4f); assertEquals(-24f, r2, 1e-4f)
    }

    @Test fun snapAndFormat() {
        assertEquals(1.5f, EqBands.snap(1.4f), 1e-4f)
        assertEquals(12f, EqBands.snap(40f), 1e-4f)
        assertEquals("+3.5", EqBands.formatDb(3.5f))
        assertEquals("0.0", EqBands.formatDb(0f))
        assertEquals("2.4 kHz", EqBands.formatHz(2400f))
    }
}
