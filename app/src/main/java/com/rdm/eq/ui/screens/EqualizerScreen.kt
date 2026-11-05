package com.rdm.eq.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.rdm.eq.dsp.ClipRisk
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.AudioSettings
import com.rdm.eq.model.EqBands
import com.rdm.eq.ui.components.NameDialog
import com.rdm.eq.ui.components.ResponseCurve
import com.rdm.eq.ui.components.StatusPill
import com.rdm.eq.ui.components.VerticalGainSlider
import com.rdm.eq.ui.theme.RdmColors

@Composable
fun EqualizerScreen(c: EqController) {
    val s by c.settings.collectAsStateWithLifecycle()
    val presets by c.presets.collectAsStateWithLifecycle()
    val st by c.status.collectAsStateWithLifecycle()
    val vol by c.volume.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val eff = remember(s, vol) { CurveMath.effectiveGains(s, vol) }
    val preamp = CurveMath.resolvedPreamp(s, eff)
    val risk = CurveMath.clipRisk(eff, preamp)
    val active = presets.firstOrNull { it.id == s.activePresetId }
    val edited = active == null || active.gains != s.gains
    var saveAsDialog by remember { mutableStateOf(false) }
    val (summary, summaryColor) = engineSummary(s, st)

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        val tall = maxHeight > 700.dp
        val compact = maxHeight < 560.dp
        val narrow = maxWidth < 600.dp
        val preampW = if (narrow) 76.dp else 132.dp
        val tiny = maxHeight < 440.dp // e.g. a phone in landscape: scroll instead of squashing the faders
        Column(
            (if (tiny) Modifier.fillMaxWidth().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            val presetLabel = (active?.name ?: "Custom (unsaved)") + if (active != null && edited) " · edited" else ""
            val onOff: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = s.enabled, onCheckedChange = c::setEnabled, modifier = Modifier.semantics { contentDescription = "Equalizer on/off" })
                    Text(if (s.enabled) "EQ ON" else "EQ OFF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = if (s.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val presetInfo: @Composable () -> Unit = {
                Column(horizontalAlignment = if (narrow) Alignment.Start else Alignment.End) {
                    Text("Preset", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(presetLabel, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (narrow) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(36.dp))
                    Text("RDM Eq", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    onOff()
                }
                presetInfo()
                StatusPill(summary, summaryColor)
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(44.dp))
                Text("RDM Eq", style = MaterialTheme.typography.headlineSmall)
                StatusPill(summary, summaryColor, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                presetInfo()
                onOff()
            }

            if (!compact) Row(Modifier.fillMaxWidth().height(if (tall) 110.dp else 72.dp)) {
                ResponseCurve(eff, preamp, s.enabled, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(preampW + 17.dp)) // matches divider + preamp column below
            }

            // Faders
            Row((if (tiny) Modifier.height(300.dp) else Modifier.weight(1f)).fillMaxWidth()) {
                Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (i in 0 until EqBands.COUNT) {
                        VerticalGainSlider(
                            value = s.gains[i],
                            onValueChange = { c.setBand(i, it) },
                            label = if (narrow) EqBands.labels[i].removeSuffix(" Hz").removeSuffix(" kHz").let { l -> if (EqBands.frequenciesHz[i] >= 1000f) l + "k" else l } else EqBands.labels[i],
                            sublabel = EqBands.roles[i],
                            enabled = s.enabled,
                            showStepButtons = !compact && !narrow,
                            narrow = narrow,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                VerticalDivider(Modifier.padding(horizontal = 8.dp))
                Column(Modifier.width(preampW).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    VerticalGainSlider(
                        value = preamp,
                        onValueChange = c::setPreamp,
                        label = "Preamp",
                        sublabel = if (s.autoHeadroom) "Auto headroom" else "Manual",
                        narrow = narrow,
                        min = AudioSettings.PREAMP_MIN, max = AudioSettings.PREAMP_MAX,
                        enabled = s.enabled && !s.autoHeadroom,
                        showStepButtons = !compact && !narrow,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!narrow) Text("Auto", style = MaterialTheme.typography.labelMedium)
                        if (!narrow) Spacer(Modifier.width(6.dp))
                        Switch(s.autoHeadroom, c::setAutoHeadroom, modifier = Modifier.semantics { contentDescription = "Auto headroom" })
                    }
                }
            }

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

            // Quick presets
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) {
                val idx = presets.indexOfFirst { it.id == s.activePresetId }
                if (idx > 0) listState.scrollToItem(idx)
            }
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(presets, key = { it.id }) { p ->
                    FilterChip(
                        selected = p.id == s.activePresetId,
                        onClick = { c.applyPreset(p.id) },
                        label = { Text(p.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 10.dp)) },
                    )
                }
            }

            // Actions
            Row(
                (if (narrow) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = c::flat, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) { Text("Flat") }
                OutlinedButton(onClick = c::resetCurve, enabled = edited, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) { Text("Reset") }
                if (!narrow) Spacer(Modifier.weight(1f))
                if (active != null) OutlinedButton(
                    onClick = { if (c.saveIntoActive()) Toast.makeText(ctx, "Saved to “${active.name}”", Toast.LENGTH_SHORT).show() },
                    enabled = edited, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                ) { Text("Save to preset") }
                Button(onClick = { saveAsDialog = true }, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) { Text("Save as new…") }
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
