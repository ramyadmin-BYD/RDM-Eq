package com.rdm.eq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.rdm.eq.audio.AudioCapabilities
import com.rdm.eq.audio.AudioEngine
import com.rdm.eq.audio.EngineParams
import com.rdm.eq.audio.TestSignalPlayer
import com.rdm.eq.data.ImportResult
import com.rdm.eq.data.PresetRepository
import com.rdm.eq.data.SettingsRepository
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.BuiltInPresets
import com.rdm.eq.model.EnginePreference
import com.rdm.eq.model.EqBands
import com.rdm.eq.model.Preset
import com.rdm.eq.model.ThemeMode
import com.rdm.eq.service.EqService
import com.rdm.eq.util.DiagLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Single source of truth for the UI, the service and the audio engine. Main thread only. */
class EqController(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val presetRepo: PresetRepository,
    val engine: AudioEngine,
) {
    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<AudioSettings> = _settings
    private val _presets = MutableStateFlow(presetRepo.all())
    val presets: StateFlow<List<Preset>> = _presets
    private val _volume = MutableStateFlow<Float?>(null)
    /** Android media volume 0..1 (may not follow the car's physical volume knob). */
    val volume: StateFlow<Float?> = _volume
    val capabilities: AudioCapabilities by lazy { AudioCapabilities.probe(context) }
    val testSignal = TestSignalPlayer(context, engine)
    val status = engine.status

    init {
        // Startup preset (if chosen) replaces the last-used curve when the process starts.
        val s = _settings.value
        if (s.startupPresetId.isNotEmpty()) {
            presetRepo.find(s.startupPresetId)?.let { p ->
                _settings.value = s.copy(gains = p.gains, activePresetId = p.id)
            }
        }
        readVolume()
        registerVolumeReceiver()
        runCatching { com.rdm.eq.audio.PlaybackMonitor(context).listen { engine.refresh() } }
        engine.setEnginePreference(_settings.value.engine)
        engine.setSessionMode(_settings.value.sessionMode)
        engine.setGlobalMode(_settings.value.globalMode)
        pushToEngine()
    }

    // ---- derived values -------------------------------------------------------------------

    fun effectiveGains(s: AudioSettings = settings.value) = CurveMath.effectiveGains(s, _volume.value)
    fun resolvedPreamp(s: AudioSettings = settings.value) = CurveMath.resolvedPreamp(s, effectiveGains(s))
    fun activePreset(): Preset? = presetRepo.find(settings.value.activePresetId)
    fun isActivePresetEdited(): Boolean = activePreset()?.let { it.gains != settings.value.gains } ?: true

    // ---- equalizer ------------------------------------------------------------------------

    fun setEnabled(on: Boolean) {
        update { it.copy(enabled = on) }
        if (on) EqService.start(context)
    }

    fun setBand(index: Int, db: Float) = update { s ->
        s.copy(gains = s.gains.toMutableList().also { it[index] = EqBands.snap(db) })
    }

    fun applyPreset(id: String) {
        val p = presetRepo.find(id) ?: return
        update { it.copy(gains = p.gains, activePresetId = p.id) }
        DiagLog.i("Preset applied: ${p.name}")
    }

    fun flat() = update { it.copy(gains = EqBands.flat(), activePresetId = BuiltInPresets.FLAT_ID) }

    /** Discards unsaved edits: back to the active preset's stored values (or flat). */
    fun resetCurve() {
        val p = activePreset()
        update { it.copy(gains = p?.gains ?: EqBands.flat(), activePresetId = p?.id ?: BuiltInPresets.FLAT_ID) }
    }

    fun setAutoHeadroom(on: Boolean) = update { s ->
        // Switching to manual keeps the level the user currently hears.
        if (!on) s.copy(autoHeadroom = false, preampDb = resolvedPreamp(s).coerceIn(AudioSettings.PREAMP_MIN, AudioSettings.PREAMP_MAX))
        else s.copy(autoHeadroom = true)
    }
    fun setPreamp(db: Float) = update { it.copy(preampDb = (Math.round(db * 2) / 2f).coerceIn(AudioSettings.PREAMP_MIN, AudioSettings.PREAMP_MAX)) }
    fun setBassBoost(v: Float) = update { it.copy(bassBoost = v.coerceIn(0f, 1f)) }
    fun setTrebleBoost(v: Float) = update { it.copy(trebleBoost = v.coerceIn(0f, 1f)) }
    fun setLoudness(on: Boolean) = update { it.copy(loudness = on) }
    fun setBalance(v: Float) = update { it.copy(balance = v.coerceIn(-1f, 1f)) }
    fun setLimiter(on: Boolean) = update { it.copy(limiter = on) }

    // ---- presets --------------------------------------------------------------------------

    fun saveAsNew(name: String): Preset {
        val p = presetRepo.saveCustom(name, settings.value.gains)
        refreshPresets()
        update { it.copy(activePresetId = p.id) }
        return p
    }

    /** Writes the current curve into the active preset (built-ins become "modified"). */
    fun saveIntoActive(): Boolean {
        val id = settings.value.activePresetId
        presetRepo.updateGains(id, settings.value.gains) ?: return false
        refreshPresets()
        return true
    }

    fun rename(id: String, name: String) { presetRepo.rename(id, name); refreshPresets() }

    fun delete(id: String) {
        presetRepo.delete(id)
        refreshPresets()
        if (settings.value.activePresetId == id) update { it.copy(activePresetId = "") }
        if (settings.value.startupPresetId == id) update { it.copy(startupPresetId = "") }
    }

    fun restoreBuiltIn(id: String) {
        val p = presetRepo.restoreBuiltIn(id) ?: return
        refreshPresets()
        if (settings.value.activePresetId == id) update { it.copy(gains = p.gains) }
    }

    fun exportPresets(): String = presetRepo.exportJson()
    fun importPresets(json: String): ImportResult = presetRepo.importJson(json).also { refreshPresets() }

    // ---- settings -------------------------------------------------------------------------

    fun setTheme(t: ThemeMode) = update { it.copy(theme = t) }
    fun setStartupPreset(id: String) = update { it.copy(startupPresetId = id) }
    fun setAutoStart(on: Boolean) = update { it.copy(autoStart = on) }
    fun setSessionMode(on: Boolean) { update { it.copy(sessionMode = on) }; engine.setSessionMode(on); pushToEngine() }
    fun setGlobalMode(on: Boolean) { update { it.copy(globalMode = on) }; engine.setGlobalMode(on); pushToEngine() }
    fun setEngine(p: EnginePreference) { update { it.copy(engine = p) }; engine.setEnginePreference(p); pushToEngine() }

    fun restoreDefaults() {
        presetRepo.resetAll()
        settingsRepo.clear()
        refreshPresets()
        val d = AudioSettings()
        _settings.value = d
        settingsRepo.save(d)
        engine.setEnginePreference(d.engine); engine.setSessionMode(d.sessionMode); engine.setGlobalMode(d.globalMode)
        pushToEngine()
        DiagLog.i("Restored defaults")
    }

    // ---- internals ------------------------------------------------------------------------

    private fun update(f: (AudioSettings) -> AudioSettings) {
        val next = f(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        settingsRepo.save(next)
        pushToEngine()
    }

    private fun refreshPresets() { _presets.value = presetRepo.all() }

    fun pushToEngine() {
        val s = _settings.value
        val eff = effectiveGains(s)
        engine.update(EngineParams(s.enabled, eff, CurveMath.resolvedPreamp(s, eff), s.balance, s.limiter))
    }

    private fun readVolume() {
        val am = context.getSystemService(AudioManager::class.java)
        _volume.value = runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else null
        }.getOrNull()
    }

    private fun registerVolumeReceiver() {
        // Unofficial but long-standing framework broadcast; used only for loudness compensation.
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val before = _volume.value
                readVolume()
                if (before != _volume.value && _settings.value.loudness) pushToEngine()
            }
        }
        ContextCompat.registerReceiver(context, r, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
