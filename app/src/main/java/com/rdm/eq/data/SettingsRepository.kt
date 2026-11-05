package com.rdm.eq.data

import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.EnginePreference
import com.rdm.eq.model.EqBands
import com.rdm.eq.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(private val store: KeyValueStore) {
    private companion object { const val KEY = "settings_v1" }

    fun load(): AudioSettings {
        val raw = store.getString(KEY) ?: return AudioSettings()
        return try { fromJson(JSONObject(raw)) } catch (e: Exception) { AudioSettings() }
    }

    fun save(s: AudioSettings) = store.putString(KEY, toJson(s).toString())

    fun clear() = store.remove(KEY)

    internal fun toJson(s: AudioSettings) = JSONObject().apply {
        put("enabled", s.enabled)
        put("gains", JSONArray().apply { s.gains.forEach { put(it.toDouble()) } })
        put("activePresetId", s.activePresetId)
        put("autoHeadroom", s.autoHeadroom)
        put("preampDb", s.preampDb.toDouble())
        put("bassBoost", s.bassBoost.toDouble())
        put("trebleBoost", s.trebleBoost.toDouble())
        put("loudness", s.loudness)
        put("balance", s.balance.toDouble())
        put("limiter", s.limiter)
        put("sessionMode", s.sessionMode)
        put("globalMode", s.globalMode)
        put("engine", s.engine.name)
        put("theme", s.theme.name)
        put("startupPresetId", s.startupPresetId)
        put("autoStart", s.autoStart)
    }

    internal fun fromJson(o: JSONObject): AudioSettings {
        val d = AudioSettings()
        val gArr = o.optJSONArray("gains")
        val gains = gArr?.let { a -> EqBands.fromStored(List(a.length()) { a.optDouble(it, 0.0).toFloat() }) } ?: d.gains
        return AudioSettings(
            enabled = o.optBoolean("enabled", d.enabled),
            gains = gains,
            activePresetId = o.optString("activePresetId", d.activePresetId),
            autoHeadroom = o.optBoolean("autoHeadroom", d.autoHeadroom),
            preampDb = o.optDouble("preampDb", d.preampDb.toDouble()).toFloat()
                .coerceIn(AudioSettings.PREAMP_MIN, AudioSettings.PREAMP_MAX),
            bassBoost = o.optDouble("bassBoost", 0.0).toFloat().coerceIn(0f, 1f),
            trebleBoost = o.optDouble("trebleBoost", 0.0).toFloat().coerceIn(0f, 1f),
            loudness = o.optBoolean("loudness", d.loudness),
            balance = o.optDouble("balance", 0.0).toFloat().coerceIn(-1f, 1f),
            limiter = o.optBoolean("limiter", d.limiter),
            sessionMode = o.optBoolean("sessionMode", d.sessionMode),
            globalMode = o.optBoolean("globalMode", d.globalMode),
            engine = enumOr(o.optString("engine"), d.engine),
            theme = enumOr(o.optString("theme"), d.theme),
            startupPresetId = o.optString("startupPresetId", d.startupPresetId),
            autoStart = o.optBoolean("autoStart", d.autoStart),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, def: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: def
}

