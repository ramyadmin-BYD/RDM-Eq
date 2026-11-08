package com.rdm.eq.audio

import com.rdm.eq.model.EnginePreference
import com.rdm.eq.util.DiagLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SessionStatus(
    val sessionId: Int,
    val source: String,
    val backend: BackendKind?,
    val hasControl: Boolean,
    val enabled: Boolean,
    val verified: Boolean,
    val channelCount: Int,
    val detail: String,
    val error: String?,
    /** Whether audio is currently playing on this session: true/false, or null when Android doesn't say. */
    val playing: Boolean? = null,
    val supportsNormalization: Boolean = false,
) {
    /** True only when the effect exists, owns control, is enabled and its parameters were read back. */
    val ready get() = backend != null && hasControl && enabled && verified && error == null
    /** Ready and audio is (or may be) flowing through it right now. */
    val processing get() = ready && playing != false
}

/** What Android reports about current playback (public API only). */
data class PlaybackSnapshot(val anyMediaPlaying: Boolean, val playingSessions: Set<Int>?)

data class EngineStatus(
    val serviceRunning: Boolean = false,
    val sessionMode: Boolean = true,
    val globalRequested: Boolean = false,
    val global: SessionStatus? = null,
    val sessions: List<SessionStatus> = emptyList(),
    val lastError: String? = null,
) {
    val activeSessions get() = sessions.count { it.processing }
    val readySessions get() = sessions.count { it.ready }
    val globalActive get() = global?.processing == true
    val anyBackend get() = (sessions + listOfNotNull(global)).firstOrNull { it.backend != null }?.backend
}

/**
 * Owns every audio effect RDM Eq creates. All calls happen on the main thread.
 * Sessions come from apps' OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION broadcasts,
 * from our own test signal, or from the global mix (session 0, experimental).
 */
class AudioEngine(private val playbackProbe: () -> PlaybackSnapshot? = { null }) {
    private class Attached(val source: String, var backend: EffectBackend?, var error: String?)

    private val known = LinkedHashMap<Int, String>() // sessions announced by apps, kept to re-attach
    private val attached = LinkedHashMap<Int, Attached>()
    private var global: Attached? = null
    private var params: EngineParams? = null
    private var pref = EnginePreference.AUTO
    private var sessionMode = true
    private var globalRequested = false
    private var serviceRunning = false
    private var lastError: String? = null

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status

    fun setServiceRunning(running: Boolean) { serviceRunning = running; publish() }

    fun openSession(sessionId: Int, source: String) {
        if (sessionId <= 0) return
        if (attached[sessionId]?.backend != null) return // duplicate announcement
        known[sessionId] = source
        DiagLog.i("Session opened: $sessionId from $source")
        pruneStale(source)
        if (sessionMode || source.contains(TEST_SIGNAL_TAG)) attach(sessionId, source)
        publish()
    }

    /**
     * Apps that are killed never send CLOSE, leaving effects on dead sessions. Android gives a normal
     * app no way to list other apps' live sessions, so keep only the newest sessions per app.
     */
    private fun pruneStale(source: String) {
        val mine = known.keys.filter { known[it] == source }
        if (mine.size <= MAX_SESSIONS_PER_APP) return
        mine.dropLast(MAX_SESSIONS_PER_APP).forEach { id ->
            known.remove(id); attached.remove(id)?.backend?.release()
            DiagLog.i("Released stale session $id from $source")
        }
    }

    fun closeSession(sessionId: Int) {
        known.remove(sessionId)
        attached.remove(sessionId)?.backend?.release()
        DiagLog.i("Session closed: $sessionId")
        publish()
    }

    fun setSessionMode(on: Boolean) {
        if (on == sessionMode) return
        sessionMode = on
        if (on) known.forEach { (id, src) -> if (!attached.containsKey(id)) attach(id, src) }
        else attached.keys.filter { !(known[it] ?: "").contains(TEST_SIGNAL_TAG) }.forEach { id -> attached.remove(id)?.backend?.release() }
        publish()
    }

    fun setGlobalMode(on: Boolean) {
        globalRequested = on
        if (on && global == null) {
            global = create(0, "Global output mix (session 0)")
        } else if (!on) {
            global?.backend?.release(); global = null
        }
        publish()
    }

    fun setEnginePreference(p: EnginePreference) {
        if (p == pref) return
        pref = p
        // Recreate everything with the new backend type.
        val ids = attached.map { it.key to it.value.source }
        attached.values.forEach { it.backend?.release() }; attached.clear()
        ids.forEach { (id, src) -> attach(id, src) }
        if (global != null) { global?.backend?.release(); global = create(0, "Global output mix (session 0)") }
        publish()
    }

    fun update(p: EngineParams) {
        params = p
        (attached.values + listOfNotNull(global)).forEach { applyTo(it) }
        publish()
    }

    fun releaseAll() {
        attached.values.forEach { it.backend?.release() }; attached.clear()
        global?.backend?.release(); global = null
        publish()
    }

    fun refresh() = publish()

    private fun attach(sessionId: Int, source: String) {
        attached.remove(sessionId)?.backend?.release()
        attached[sessionId] = create(sessionId, source)
    }

    private fun create(sessionId: Int, source: String): Attached {
        val order = when (pref) {
            EnginePreference.AUTO -> listOf(BackendKind.DYNAMICS_PROCESSING, BackendKind.LEGACY_EQUALIZER)
            EnginePreference.DYNAMICS_PROCESSING -> listOf(BackendKind.DYNAMICS_PROCESSING)
            EnginePreference.LEGACY_EQUALIZER -> listOf(BackendKind.LEGACY_EQUALIZER)
        }
        val errors = mutableListOf<String>()
        for (kind in order) {
            try {
                val b: EffectBackend = when (kind) {
                    BackendKind.DYNAMICS_PROCESSING -> DynamicsProcessingBackend(sessionId, PRIORITY)
                    BackendKind.LEGACY_EQUALIZER -> LegacyEqualizerBackend(sessionId, PRIORITY)
                }
                b.setControlListener { publish() }
                DiagLog.i("Attached ${kind.name} to session $sessionId ($source), control=${b.hasControl}")
                return Attached(source, b, null).also { applyTo(it) }
            } catch (t: Throwable) {
                // UnsupportedOperationException / RuntimeException when the effect can't be created.
                errors += "${kind.label}: ${t.javaClass.simpleName}${t.message?.let { ": $it" } ?: ""}"
                DiagLog.w("Could not create ${kind.name} on session $sessionId", t)
            }
        }
        lastError = errors.joinToString("; ")
        return Attached(source, null, lastError)
    }

    private fun applyTo(a: Attached) {
        val p = params ?: return
        val b = a.backend ?: return
        try {
            b.apply(p)
            a.error = if (!b.hasControl) "Another audio app has priority over this session" else null
        } catch (t: Throwable) {
            a.error = "Apply failed: ${t.javaClass.simpleName}${t.message?.let { ": $it" } ?: ""}"
            DiagLog.w("Apply failed on session ${b.sessionId}", t)
        }
    }

    private fun Attached.toStatus(id: Int, snap: PlaybackSnapshot?) = SessionStatus(
        sessionId = id, source = source, backend = backend?.kind,
        hasControl = backend?.hasControl == true, enabled = backend?.isEnabled == true,
        verified = backend?.verified == true, channelCount = backend?.channelCount ?: 0,
        detail = backend?.detail ?: "", error = error,
        supportsNormalization = backend?.supportsNormalization == true,
        playing = when {
            snap == null -> null
            id == 0 -> snap.anyMediaPlaying
            snap.playingSessions != null -> id in snap.playingSessions
            !snap.anyMediaPlaying -> false
            else -> null
        },
    )

    private fun publish() {
        val snap = runCatching { playbackProbe() }.getOrNull()
        _status.value = EngineStatus(
            serviceRunning = serviceRunning,
            sessionMode = sessionMode,
            globalRequested = globalRequested,
            global = global?.toStatus(0, snap),
            sessions = attached.map { (id, a) -> a.toStatus(id, snap) } +
                known.filterKeys { !attached.containsKey(it) }.map { (id, src) ->
                    SessionStatus(id, src, null, false, false, false, 0, "", "Not attached (session mode off)")
                },
            lastError = lastError,
        )
    }

    /** Backends present on this engine right now (for feature-support badges). */
    fun backends(): List<EffectBackend> = (attached.values + listOfNotNull(global)).mapNotNull { it.backend }

    companion object {
        /** Higher than the default (0) so RDM Eq gets control when it coexists with player-built EQs. */
        const val PRIORITY = 100
        const val MAX_SESSIONS_PER_APP = 2
        const val TEST_SIGNAL_TAG = "RDM Eq test signal"
    }
}
