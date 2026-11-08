package com.rdm.eq.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.EqController
import com.rdm.eq.R
import com.rdm.eq.audio.BackendKind
import com.rdm.eq.dsp.ClipRisk
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.EnginePreference
import com.rdm.eq.model.EqBands
import com.rdm.eq.ui.components.NameDialog
import com.rdm.eq.ui.components.ResponseCurve
import com.rdm.eq.ui.components.StatusPill
import com.rdm.eq.ui.components.VerticalGainSlider
import com.rdm.eq.ui.theme.RdmColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Equalizer laid out like RDM Player's: header with Save and on/off, preset chips (with "Custom"),
 * the 10 faders in a rounded card, then preamp / bass / treble / loudness / balance / normalization.
 * Everything drives RDM Eq's own cross-app engine (DynamicsProcessing, classic Equalizer fallback).
 */
@Composable
fun EqualizerScreen(c: EqController) {
    val s by c.settings.collectAsStateWithLifecycle()
    val presets by c.presets.collectAsStateWithLifecycle()
    val st by c.status.collectAsStateWithLifecycle()
    val vol by c.volume.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val eff = remember(s, vol) { CurveMath.effectiveGains(s, vol) }
    val preamp = CurveMath.resolvedPreamp(s, eff)
    val loudGain = CurveMath.loudnessGain(s)
    val risk = CurveMath.clipRisk(eff, preamp)
    val active = presets.firstOrNull { it.id == s.activePresetId }
    val edited = active == null || active.gains != s.gains
    var saveAsDialog by remember { mutableStateOf(false) }
    val (summary, summaryColor) = engineSummary(s, st)

    // Features that need the Dynamics Processing engine (not the classic Equalizer).
    val attached = (st.sessions + listOfNotNull(st.global)).filter { it.backend != null }
    val legacyOnly = attached.isNotEmpty() && attached.all { it.backend == BackendKind.LEGACY_EQUALIZER }
    val dpFeatures = c.capabilities.dynamicsProcessing && !legacyOnly && s.engine != EnginePreference.LEGACY_EQUALIZER
    val dpAttached = attached.filter { it.backend == BackendKind.DYNAMICS_PROCESSING }
    val normalizationOk = dpFeatures && (dpAttached.isEmpty() || dpAttached.any { it.supportsNormalization })

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 600.dp
        val gutter = if (narrow) 16.dp else 20.dp
        val stepButtons = !narrow
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header (RDM Player's ScreenHeader: title, subtitle, Save, on/off)
            Row(
                Modifier.fillMaxWidth().padding(start = gutter, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    val presetLabel = (active?.name ?: "Custom") + if (active != null && edited) " · edited" else ""
                    Text(
                        "RDM Eq · $presetLabel", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { saveAsDialog = true }) {
                    Icon(painterResource(R.drawable.ic_save), contentDescription = "Save preset")
                }
                Switch(
                    checked = s.enabled, onCheckedChange = c::setEnabled,
                    modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "Equalizer on/off" },
                )
            }
            StatusPill(summary, summaryColor, Modifier.padding(horizontal = gutter))
            Text(
                "Applies to apps that announce their audio to Android's effects system (most music and video players). " +
                    "Apps that don't announce it can't be changed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = gutter, vertical = 6.dp),
            )

            // Preset chips + "Custom", as in RDM Player
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) {
                val idx = presets.indexOfFirst { it.id == s.activePresetId }
                if (idx > 0) listState.scrollToItem(idx)
            }
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = gutter, vertical = 12.dp),
            ) {
                items(presets, key = { it.id }) { p ->
                    FilterChip(
                        selected = p.id == s.activePresetId && !edited,
                        onClick = { c.applyPreset(p.id) },
                        label = { Text(p.name, modifier = Modifier.padding(vertical = 8.dp)) },
                    )
                }
                item(key = "custom") {
                    FilterChip(selected = edited, onClick = {}, label = { Text("Custom", modifier = Modifier.padding(vertical = 8.dp)) })
                }
            }

            // Faders card
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = gutter),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp)) {
                    ResponseCurve(eff, preamp + loudGain, s.enabled, Modifier.fillMaxWidth().height(if (narrow) 64.dp else 96.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(if (stepButtons) 372.dp else 280.dp)) {
                        for (i in 0 until EqBands.COUNT) {
                            VerticalGainSlider(
                                value = s.gains[i],
                                onValueChange = { c.setBand(i, it) },
                                label = EqBands.shortLabels[i],
                                enabled = s.enabled,
                                showStepButtons = stepButtons,
                                narrow = narrow,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Clipping warning
                if (risk != ClipRisk.NONE && s.enabled) {
                    val peak = CurveMath.peakGainDb(eff, preamp)
                    Text(
                        "⚠ Settings can raise peaks by ${EqBands.formatDb(peak)} dB — " +
                            (if (s.limiter) "the limiter will catch digital overs, but " else "") +
                            "lower the preamp or turn on Auto headroom to avoid distortion.",
                        color = if (risk == ClipRisk.HIGH) RdmColors.danger else RdmColors.warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Preset actions
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    val pad = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    OutlinedButton(onClick = c::flat, contentPadding = pad) { Text("Flat") }
                    OutlinedButton(onClick = c::resetCurve, enabled = edited, contentPadding = pad) { Text("Reset") }
                    if (active != null) OutlinedButton(
                        onClick = { if (c.saveIntoActive()) Toast.makeText(ctx, "Saved to “${active.name}”", Toast.LENGTH_SHORT).show() },
                        enabled = edited, contentPadding = pad,
                    ) { Text("Save to preset") }
                    Button(onClick = { saveAsDialog = true }, contentPadding = pad) { Text("Save as new…") }
                }

                // RDM Player's controls under the faders
                EqLabeledSlider(
                    "Preamp", preamp, AudioSettings.PREAMP_MIN, AudioSettings.PREAMP_MAX,
                    String.format(Locale.ROOT, "%+.1f dB", preamp + 0f), s.enabled && !s.autoHeadroom, c::setPreamp,
                )
                EqSwitchRow("Auto headroom", s.autoHeadroom, "Lowers the preamp by the largest boost so the curve never adds level", enabled = true, onChange = c::setAutoHeadroom)
                EqLabeledSlider(
                    "Bass enhancement", s.bassBoost, 0f, 1f, "${(s.bassBoost * 100).roundToInt()}%", s.enabled,
                ) { c.setBassBoost((it * 20).roundToInt() / 20f) }
                EqLabeledSlider(
                    "Treble enhancement", s.trebleBoost, 0f, 1f, "${(s.trebleBoost * 100).roundToInt()}%", s.enabled,
                ) { c.setTrebleBoost((it * 20).roundToInt() / 20f) }
                if (dpFeatures) {
                    EqLabeledSlider(
                        "Loudness", s.loudnessGainDb, 0f, AudioSettings.LOUDNESS_GAIN_MAX,
                        String.format(Locale.ROOT, "%+.1f dB", s.loudnessGainDb), s.enabled && s.limiter, c::setLoudnessGain,
                    )
                    if (!s.limiter) Text(
                        "Loudness needs the limiter (Sound → Limiter) to keep peaks from clipping, so it is paused.",
                        style = MaterialTheme.typography.bodySmall, color = RdmColors.warning,
                    )
                }
                EqLabeledSlider(
                    "Balance (left / right)", s.balance, -1f, 1f,
                    String.format(Locale.ROOT, "%+.0f%%", s.balance * 100 + 0f), s.enabled && dpFeatures,
                ) { v -> c.setBalance(if (abs(v) < 0.04f) 0f else (v * 20).roundToInt() / 20f) }
                EqSwitchRow(
                    "Volume normalization", s.normalization,
                    if (normalizationOk) "Evens out loud and quiet songs with a gentle compressor"
                    else "Not available with the classic Android Equalizer on this unit",
                    enabled = normalizationOk, onChange = c::setNormalization,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (saveAsDialog) NameDialog(
        title = "Save as new preset", initial = "", confirmLabel = "Save",
        onConfirm = { name ->
            c.saveAsNew(name); saveAsDialog = false
            Toast.makeText(ctx, "Saved “$name”", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { saveAsDialog = false },
    )
}

/** RDM Player's LabeledSlider: title on the left, value in the accent colour on the right, slider below. */
@Composable
private fun EqLabeledSlider(
    label: String, value: Float, min: Float, max: Float, valueText: String, enabled: Boolean, onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max, enabled = enabled,
            modifier = Modifier.height(48.dp).semantics { contentDescription = label },
        )
    }
}

@Composable
private fun EqSwitchRow(label: String, checked: Boolean, desc: String?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
