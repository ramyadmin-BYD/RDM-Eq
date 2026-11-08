package com.rdm.eq

import com.rdm.eq.data.InMemoryStore
import com.rdm.eq.data.PresetRepository
import com.rdm.eq.model.BuiltInPresets
import com.rdm.eq.model.EqBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRepositoryTest {
    private var n = 0
    private fun repo(store: InMemoryStore = InMemoryStore()) = PresetRepository(store) { "c_${n++}" }
    private val curve = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)

    @Test fun builtInLibraryIsComplete() {
        val names = BuiltInPresets.all.map { it.name }
        listOf("Flat", "BYD Factory Sound Reference", "Balanced", "Pop", "Rock", "Jazz", "Classical", "Acoustic",
            "Vocal", "Electronic", "Bass Boost", "Treble Boost", "Podcast and Speech", "Hip-Hop", "EDM", "Deep Bass", "Bass Punch", "Treble Clarity",
            "Night Driving", "Highway Driving", "Audiophile").forEach { assertTrue("missing $it", it in names) }
        BuiltInPresets.all.forEach { p ->
            assertEquals(EqBands.COUNT, p.gains.size)
            p.gains.forEach { assertTrue(it in EqBands.MIN_DB..EqBands.MAX_DB) }
        }
        assertEquals(EqBands.flat(), BuiltInPresets.byId(BuiltInPresets.BYD_REFERENCE_ID)!!.gains)
        assertEquals(BuiltInPresets.all.size, BuiltInPresets.all.map { it.id }.toSet().size)
    }

    @Test fun saveRenameDeleteCustom() {
        val r = repo()
        val p = r.saveCustom("  My Car  ", curve)
        assertEquals("My Car", p.name)
        assertEquals(curve, r.find(p.id)!!.gains)
        assertTrue(r.rename(p.id, "Road Trip"))
        assertEquals("Road Trip", r.find(p.id)!!.name)
        assertTrue(r.delete(p.id))
        assertNull(r.find(p.id))
        assertFalse(r.delete("flat")) // built-ins can't be deleted
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyNameRejected() { repo().saveCustom("   ", curve) }

    @Test fun customPresetsSurviveRestart() {
        val store = InMemoryStore()
        val id = repo(store).saveCustom("Persisted", curve).id
        val reloaded = repo(store)
        assertEquals("Persisted", reloaded.find(id)!!.name)
        assertEquals(curve, reloaded.find(id)!!.gains)
    }

    @Test fun builtInCanBeModifiedAndRestored() {
        val store = InMemoryStore()
        val r = repo(store)
        r.updateGains("rock", curve)
        assertTrue(r.find("rock")!!.modified)
        assertEquals(curve, repo(store).find("rock")!!.gains) // persisted
        val restored = r.restoreBuiltIn("rock")!!
        assertFalse(restored.modified)
        assertEquals(BuiltInPresets.byId("rock")!!.gains, restored.gains)
        assertFalse(repo(store).find("rock")!!.modified)
    }

    @Test fun gainsAreNormalisedOnSave() {
        val p = repo().saveCustom("x", listOf(30f, -30f, 1.26f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        assertEquals(listOf(12f, -12f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), p.gains)
    }

    @Test fun exportImportRoundTrip() {
        val a = repo()
        a.saveCustom("Export Me", curve)
        a.updateGains("jazz", curve)
        val json = a.exportJson()
        val b = repo()
        val res = b.importJson(json)
        assertEquals(1, res.customsAdded); assertEquals(1, res.builtInsUpdated)
        assertEquals(curve, b.customs().single { it.name == "Export Me" }.gains)
        assertTrue(b.find("jazz")!!.modified)
        // Importing again updates by name instead of duplicating.
        b.importJson(json)
        assertEquals(1, b.customs().count { it.name == "Export Me" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsForeignFiles() { repo().importJson("{\"hello\":1}") }

    @Test fun corruptedStorageFallsBackToEmpty() {
        val store = InMemoryStore().apply { map["custom_presets_v1"] = "not json"; map["builtin_overrides_v1"] = "[" }
        val r = repo(store)
        assertTrue(r.customs().isEmpty())
        assertEquals(BuiltInPresets.all.size, r.builtIns().size)
    }

    @Test fun customPresetsFromVersion1AreConverted() {
        // Exactly what RDM Eq 1.1 stored: 7 gains per preset, and a 7-band built-in override.
        val store = InMemoryStore().apply {
            map["custom_presets_v1"] = "[{\"id\":\"c_old\",\"name\":\"Old Car\",\"gains\":[6,3,0,-3,0,2,4]}]"
            map["builtin_overrides_v1"] = "{\"rock\":[1,1,1,1,1,1,1]}"
        }
        val r = repo(store)
        val old = r.find("c_old")!!
        assertEquals("Old Car", old.name)
        assertEquals(10, old.gains.size)
        assertEquals(6f, old.gains[0], 1e-4f); assertEquals(-3f, old.gains[5], 1e-4f); assertEquals(4f, old.gains[9], 1e-4f)
        assertEquals(List(10) { 1f }, r.find("rock")!!.gains)
    }

    @Test fun importsSevenBandExportFiles() {
        val json = "{\"format\":\"rdm-eq-presets\",\"version\":1,\"custom\":[{\"name\":\"Legacy\",\"gains\":[0,0,0,0,0,0,0]}]}"
        val r = repo()
        assertEquals(1, r.importJson(json).customsAdded)
        assertEquals(EqBands.flat(), r.customs().single().gains)
    }

    @Test fun resetAllClearsEverything() {
        val r = repo()
        r.saveCustom("x", curve); r.updateGains("pop", curve)
        r.resetAll()
        assertTrue(r.customs().isEmpty())
        assertFalse(r.find("pop")!!.modified)
    }
}
