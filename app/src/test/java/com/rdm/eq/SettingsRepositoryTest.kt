package com.rdm.eq

import com.rdm.eq.data.InMemoryStore
import com.rdm.eq.data.SettingsRepository
import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.EnginePreference
import com.rdm.eq.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    @Test fun defaultsWhenEmpty() = assertEquals(AudioSettings(), SettingsRepository(InMemoryStore()).load())

    @Test fun fullRoundTripSurvivesRestart() {
        val store = InMemoryStore()
        val s = AudioSettings(
            enabled = false, gains = listOf(1f, -2f, 3.5f, 0f, 0f, 4f, -12f, 2f, 0.5f, 6f), activePresetId = "rock",
            autoHeadroom = false, preampDb = -4.5f, bassBoost = 0.3f, trebleBoost = 0.7f, loudness = true,
            loudnessGainDb = 3.5f, normalization = true,
            balance = -0.25f, limiter = false, sessionMode = false, globalMode = true,
            engine = EnginePreference.LEGACY_EQUALIZER, theme = ThemeMode.LIGHT, startupPresetId = "jazz", autoStart = false,
        )
        SettingsRepository(store).save(s)
        assertEquals(s, SettingsRepository(store).load())
    }

    @Test fun sevenBandSettingsFromVersion1AreConverted() {
        val store = InMemoryStore().apply {
            map["settings_v1"] = """{"gains":[4,2.5,-1,-1.5,1,3,3.5],"activePresetId":"rock","theme":"LIGHT"}"""
        }
        val s = SettingsRepository(store).load()
        assertEquals(10, s.gains.size)
        assertEquals(-1.5f, s.gains[5]) // 1 kHz
        assertEquals("rock", s.activePresetId); assertEquals(ThemeMode.LIGHT, s.theme)
    }

    @Test fun corruptedDataFallsBackToDefaults() {
        val store = InMemoryStore().apply { map["settings_v1"] = "{broken" }
        assertEquals(AudioSettings(), SettingsRepository(store).load())
    }

    @Test fun outOfRangeValuesAreClamped() {
        val store = InMemoryStore().apply {
            map["settings_v1"] = """{"gains":[99,0,0,0,0,0,0,0,0,-99],"preampDb":40,"balance":5,"bassBoost":3,"loudnessGainDb":40,"engine":"BOGUS"}"""
        }
        val s = SettingsRepository(store).load()
        assertEquals(12f, s.gains[0]); assertEquals(-12f, s.gains[9])
        assertEquals(AudioSettings.PREAMP_MAX, s.preampDb)
        assertEquals(1f, s.balance); assertEquals(1f, s.bassBoost)
        assertEquals(AudioSettings.LOUDNESS_GAIN_MAX, s.loudnessGainDb)
        assertEquals(EnginePreference.AUTO, s.engine)
    }
}
