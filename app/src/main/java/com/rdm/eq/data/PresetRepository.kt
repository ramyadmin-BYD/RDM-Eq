package com.rdm.eq.data

import com.rdm.eq.model.BuiltInPresets
import com.rdm.eq.model.EqBands
import com.rdm.eq.model.Preset
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ImportResult(val customsAdded: Int, val builtInsUpdated: Int)

object PresetNames {
    const val MAX_LENGTH = 40
    fun validate(name: String): String? {
        val t = name.trim()
        return when {
            t.isEmpty() -> "Name can't be empty"
            t.length > MAX_LENGTH -> "Name is too long (max $MAX_LENGTH)"
            else -> null
        }
    }
}

/**
 * Built-in presets (with optional user overrides) and custom presets.
 * Built-ins can be edited and restored; customs can also be renamed and deleted.
 */
class PresetRepository(
    private val store: KeyValueStore,
    private val newId: () -> String = { "c_" + UUID.randomUUID().toString().substring(0, 8) },
) {
    private companion object {
        const val KEY_CUSTOM = "custom_presets_v1"
        const val KEY_OVERRIDES = "builtin_overrides_v1"
        const val EXPORT_FORMAT = "rdm-eq-presets"
    }

    private var customs: MutableList<Preset> = loadCustoms()
    private var overrides: MutableMap<String, List<Float>> = loadOverrides()

    fun builtIns(): List<Preset> = BuiltInPresets.all.map { b ->
        overrides[b.id]?.let { b.copy(gains = it, modified = true) } ?: b
    }

    fun customs(): List<Preset> = customs.toList()
    fun all(): List<Preset> = builtIns() + customs()
    fun find(id: String): Preset? = all().firstOrNull { it.id == id }

    fun saveCustom(name: String, gains: List<Float>): Preset {
        PresetNames.validate(name)?.let { throw IllegalArgumentException(it) }
        val p = Preset(newId(), name.trim(), EqBands.normalize(gains), builtIn = false)
        customs.add(p)
        persistCustoms()
        return p
    }

    /** Stores new values into an existing preset (override for built-ins). */
    fun updateGains(id: String, gains: List<Float>): Preset? {
        val g = EqBands.normalize(gains)
        if (BuiltInPresets.byId(id) != null) {
            if (g == BuiltInPresets.byId(id)!!.gains) overrides.remove(id) else overrides[id] = g
            persistOverrides()
            return find(id)
        }
        val i = customs.indexOfFirst { it.id == id }
        if (i < 0) return null
        customs[i] = customs[i].copy(gains = g)
        persistCustoms()
        return customs[i]
    }

    fun rename(id: String, newName: String): Boolean {
        PresetNames.validate(newName)?.let { throw IllegalArgumentException(it) }
        val i = customs.indexOfFirst { it.id == id }
        if (i < 0) return false
        customs[i] = customs[i].copy(name = newName.trim())
        persistCustoms()
        return true
    }

    fun delete(id: String): Boolean {
        val removed = customs.removeAll { it.id == id }
        if (removed) persistCustoms()
        return removed
    }

    fun restoreBuiltIn(id: String): Preset? {
        if (overrides.remove(id) != null) persistOverrides()
        return find(id)
    }

    fun resetAll() {
        customs.clear(); overrides.clear()
        store.remove(KEY_CUSTOM); store.remove(KEY_OVERRIDES)
    }

    fun exportJson(): String = JSONObject().apply {
        put("format", EXPORT_FORMAT)
        put("version", 1)
        put("bandsHz", JSONArray().apply { EqBands.frequenciesHz.forEach { put(it.toDouble()) } })
        put("custom", JSONArray().apply { customs.forEach { put(presetToJson(it)) } })
        put("builtInOverrides", JSONObject().apply { overrides.forEach { (k, v) -> put(k, gainsToJson(v)) } })
    }.toString(2)

    /** Merges presets from an export file. Throws IllegalArgumentException for invalid files. */
    fun importJson(json: String): ImportResult {
        val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException("Not a valid preset file") }
        if (root.optString("format") != EXPORT_FORMAT) throw IllegalArgumentException("Not an RDM Eq preset file")
        var added = 0
        var updated = 0
        root.optJSONArray("custom")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val gains = jsonToGains(o.optJSONArray("gains")) ?: continue
                if (PresetNames.validate(name) != null) continue
                val existing = customs.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (existing >= 0) customs[existing] = customs[existing].copy(gains = gains)
                else customs.add(Preset(newId(), name, gains, builtIn = false))
                added++
            }
        }
        root.optJSONObject("builtInOverrides")?.let { o ->
            o.keys().forEach { id ->
                if (BuiltInPresets.byId(id) != null) {
                    jsonToGains(o.optJSONArray(id))?.let { overrides[id] = it; updated++ }
                }
            }
        }
        persistCustoms(); persistOverrides()
        return ImportResult(added, updated)
    }

    // --- persistence -------------------------------------------------------------------------

    private fun presetToJson(p: Preset) = JSONObject().put("id", p.id).put("name", p.name).put("gains", gainsToJson(p.gains))
    private fun gainsToJson(g: List<Float>) = JSONArray().apply { g.forEach { put(it.toDouble()) } }
    private fun jsonToGains(a: JSONArray?): List<Float>? {
        if (a == null || a.length() != EqBands.COUNT) return null
        val out = ArrayList<Float>(EqBands.COUNT)
        for (i in 0 until a.length()) {
            val d = a.optDouble(i, Double.NaN)
            if (d.isNaN()) return null
            out.add(d.toFloat())
        }
        return EqBands.normalize(out)
    }

    private fun persistCustoms() =
        store.putString(KEY_CUSTOM, JSONArray().apply { customs.forEach { put(presetToJson(it)) } }.toString())

    private fun persistOverrides() =
        store.putString(KEY_OVERRIDES, JSONObject().apply { overrides.forEach { (k, v) -> put(k, gainsToJson(v)) } }.toString())

    private fun loadCustoms(): MutableList<Preset> = try {
        val arr = JSONArray(store.getString(KEY_CUSTOM) ?: "[]")
        MutableList(arr.length()) { i -> arr.getJSONObject(i) }.mapNotNull { o ->
            val g = jsonToGains(o.optJSONArray("gains")) ?: return@mapNotNull null
            Preset(o.getString("id"), o.getString("name"), g, builtIn = false)
        }.toMutableList()
    } catch (e: Exception) { mutableListOf() }

    private fun loadOverrides(): MutableMap<String, List<Float>> = try {
        val o = JSONObject(store.getString(KEY_OVERRIDES) ?: "{}")
        val m = LinkedHashMap<String, List<Float>>()
        o.keys().forEach { k -> if (BuiltInPresets.byId(k) != null) jsonToGains(o.optJSONArray(k))?.let { m[k] = it } }
        m
    } catch (e: Exception) { mutableMapOf() }
}
