package com.rdm.eq.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.EqController
import com.rdm.eq.audio.BackendKind
import com.rdm.eq.dsp.ClipRisk
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.EqBands
import com.rdm.eq.ui.components.SectionCard
import com.rdm.eq.ui.components.SupportBadge
import com.rdm.eq.ui.theme.RdmColors
import kotlin.math.roundToInt

@Composable
fun SoundScreen(c: EqController) {
    val s by c.settings.collectAsStateWithLifecycle()
    val st by c.status.collectAsStateWithLifecycle()
    val vol by c.volume.collectAsStateWithLifecycle()
    val caps = c.capabilities
    val attached = (st.sessions + listOfNotNull(st.global)).filter { it.backend != null }
    val dpAvailable = caps.dynamicsProcessing
    val legacyOnly = attached.isNotEmpty() && attached.all { it.backend == BackendKind.LEGACY_EQUALIZER }
    val eff = CurveMath.effectiveGains(s, vol)
    val preamp = CurveMath.resolvedPreamp(s, eff)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoCol = maxWidth > 760.dp
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { m ->
                SectionCard("Bass boost", m) {
                    Text("Adds up to +6 dB at 62 Hz, +5 dB at 31 Hz and +4 dB at 125 Hz. Very low (subsonic) frequencies are tapered to protect the speakers.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LabeledSlider("Amount", s.bassBoost, "+${EqBands.formatDb(s.bassBoost * 6f).trimStart('+')} dB @ 62 Hz", s.enabled, c::setBassBoost)
                    SupportBadge(true, "Applied by the EQ engine (no separate BassBoost effect needed)")
                }
            },
            { m ->
                SectionCard("Treble enhancement", m) {
                    Text("Adds up to +5 dB at 16 kHz and +3 dB at 8 kHz for detail and air.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LabeledSlider("Amount", s.trebleBoost, "+${EqBands.formatDb(s.trebleBoost * 5f).trimStart('+')} dB @ 16 kHz", s.enabled, c::setTrebleBoost)
                    SupportBadge(true, "Applied by the EQ engine")
                }
            },
            { m ->
                SectionCard("Loudness compensation", m, trailing = {
                    Switch(s.loudness, c::setLoudness, enabled = vol != null, modifier = Modifier.semantics { contentDescription = "Loudness compensation" })
                }) {
                    Text("At low volume the ear hears less bass and treble. This adds them back as Android's media volume goes down (full effect at silence, none above 60 %).",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (vol != null) {
                        val amt = CurveMath.loudnessAmount(vol!!)
                        Text("Android media volume: ${(vol!! * 100).roundToInt()} % → +${"%.1f".format(amt * 6f)} dB bass, +${"%.1f".format(amt * 3f)} dB treble",
                            style = MaterialTheme.typography.bodyMedium)
                        SupportBadge(null, "Unverified on DiLink: if the car's volume knob controls the amplifier instead of Android's media volume, this will not follow it")
                    } else SupportBadge(false, "Android media volume can't be read on this device — unsupported")
                }
            },
            { m ->
                SectionCard("Balance (left / right)", m) {
                    val ch = attached.map { it.channelCount }.filter { it > 0 }.distinct()
                    val (ok, why) = when {
                        !dpAvailable || legacyOnly -> false to "Not supported by the classic Android Equalizer"
                        attached.isEmpty() -> null to "Applies per-channel gain to stereo streams once one is attached"
                        ch.all { it == 2 } -> true to "Applied as per-channel input gain (only ever turns one side down)"
                        else -> false to "Attached stream is ${ch.joinToString("/")}-channel; balance is only applied to stereo"
                    }
                    val label = when {
                        s.balance < -0.01f -> "Left ${(-s.balance * 100).roundToInt()} %"
                        s.balance > 0.01f -> "Right ${(s.balance * 100).roundToInt()} %"
                        else -> "Centre"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("L", style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = s.balance, onValueChange = { c.setBalance((it * 20).roundToInt() / 20f) },
                            valueRange = -1f..1f, enabled = s.enabled && ok != false,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "Balance" },
                        )
                        Text("R", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    SupportBadge(ok, why)
                    Text("BYD's own sound settings usually also offer balance/fader; those act on the amplifier and are independent of RDM Eq.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            { m ->
                SectionCard("Limiter / clipping protection", m, trailing = {
                    Switch(s.limiter, c::setLimiter, enabled = dpAvailable && !legacyOnly, modifier = Modifier.semantics { contentDescription = "Limiter" })
                }) {
                    Text("Catches digital peaks above −1 dBFS inside RDM Eq's processing. It cannot prevent distortion that happens later in the car's own amplifier or speakers.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SupportBadge(dpAvailable && !legacyOnly, if (dpAvailable && !legacyOnly) "Supported (Dynamics Processing limiter)" else "Not available with the classic Equalizer")
                    val risk = CurveMath.clipRisk(eff, preamp)
                    val peak = CurveMath.peakGainDb(eff, preamp)
                    Text(
                        when (risk) {
                            ClipRisk.NONE -> "Current settings: no added peak level (${EqBands.formatDb(peak)} dB)."
                            ClipRisk.LOW -> "Current settings: peaks up to ${EqBands.formatDb(peak)} dB — mild clipping risk."
                            ClipRisk.HIGH -> "Current settings: peaks up to ${EqBands.formatDb(peak)} dB — high clipping risk. Lower the preamp."
                        },
                        color = when (risk) { ClipRisk.NONE -> RdmColors.success; ClipRisk.LOW -> RdmColors.warning; ClipRisk.HIGH -> RdmColors.danger },
                    )
                }
            },
            { m ->
                SectionCard("Not offered", m) {
                    SupportBadge(false, "Stereo widening / Virtualizer — designed for headphones; " +
                        (if (caps.virtualizer) "present on this unit but " else "not present on this unit, and ") +
                        "it has no dependable effect on car cabin speakers, so no control is shown.")
                    SupportBadge(false, "Front/rear fader — not reachable through Android audio-effect APIs; use BYD's own sound settings.")
                }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sound enhancements", style = MaterialTheme.typography.titleLarge)
            if (twoCol) cards.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { pair.forEach { it(Modifier.weight(1f)) } }
            } else cards.forEach { it(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, valueText: String, enabled: Boolean, onChange: (Float) -> Unit) {
    Column {
        Row { Text(label, Modifier.weight(1f)); Text(valueText, color = MaterialTheme.colorScheme.primary) }
        Slider(value = value, onValueChange = { onChange((it * 20).roundToInt() / 20f) }, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
