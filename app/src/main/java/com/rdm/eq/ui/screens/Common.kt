package com.rdm.eq.ui.screens

import androidx.compose.ui.graphics.Color
import com.rdm.eq.audio.EngineStatus
import com.rdm.eq.model.AudioSettings
import com.rdm.eq.ui.theme.RdmColors

/** One honest line: is the selected setting actually reaching audio? */
fun engineSummary(s: AudioSettings, st: EngineStatus): Pair<String, Color> = when {
    !s.enabled -> "Bypassed — audio is unchanged" to RdmColors.neutral
    st.activeSessions > 0 -> "Processing ${st.activeSessions} audio stream${if (st.activeSessions > 1) "s" else ""}" to RdmColors.success
    st.globalActive -> "Global mode attached — confirm by ear" to RdmColors.warning
    st.readySessions > 0 -> "Ready on ${st.readySessions} app session${if (st.readySessions > 1) "s" else ""} — nothing playing" to RdmColors.success
    st.sessions.any { it.error != null && it.backend == null } || st.global?.error != null ->
        "Effect could not be applied — see Status" to RdmColors.danger
    else -> "Selected, not yet applied — no media app connected" to RdmColors.warning
}
