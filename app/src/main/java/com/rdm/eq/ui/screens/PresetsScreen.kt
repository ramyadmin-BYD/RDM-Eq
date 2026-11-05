package com.rdm.eq.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.EqController
import com.rdm.eq.model.Preset
import com.rdm.eq.ui.components.ConfirmDialog
import com.rdm.eq.ui.components.MiniCurve
import com.rdm.eq.ui.components.NameDialog
import com.rdm.eq.ui.theme.RdmColors

@Composable
fun PresetsScreen(c: EqController) {
    val s by c.settings.collectAsStateWithLifecycle()
    val presets by c.presets.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<Preset?>(null) }
    var restoring by remember { mutableStateOf<Preset?>(null) }
    var saveAs by remember { mutableStateOf(false) }
    val builtIns = presets.filter { it.builtIn }
    val customs = presets.filter { !it.builtIn }

    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
        val wide = maxWidth > 760.dp
        val builtInList: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text("Built-in presets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(builtIns, key = { it.id }) { p ->
                        PresetRow(p, p.id == s.activePresetId, onApply = { c.applyPreset(p.id) }) {
                            if (p.modified) IconButton(onClick = { restoring = p }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Restore original ${p.name}")
                            }
                        }
                    }
                }
            }
        }
        val customList: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Text("My presets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = { saveAs = true }) { Text("Save current…") }
                }
                if (customs.isEmpty()) Text(
                    "No custom presets yet. Shape a sound on the Equalizer screen, then save it here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(customs, key = { it.id }) { p ->
                        PresetRow(p, p.id == s.activePresetId, onApply = { c.applyPreset(p.id) }) {
                            IconButton(onClick = { renaming = p }, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Edit, "Rename ${p.name}") }
                            IconButton(onClick = { deleting = p }, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Delete, "Delete ${p.name}") }
                        }
                    }
                }
            }
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            builtInList(Modifier.weight(1.2f)); customList(Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            customList(Modifier.weight(0.4f)); builtInList(Modifier.weight(0.6f))
        }
    }

    renaming?.let { p ->
        NameDialog("Rename preset", p.name, "Rename", onConfirm = { c.rename(p.id, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { p ->
        ConfirmDialog("Delete “${p.name}”?", "This custom preset will be removed permanently.", "Delete",
            onConfirm = { c.delete(p.id); deleting = null }, onDismiss = { deleting = null })
    }
    restoring?.let { p ->
        ConfirmDialog("Restore “${p.name}”?", "Your changes to this built-in preset will be replaced with its original values.", "Restore",
            onConfirm = { c.restoreBuiltIn(p.id); restoring = null }, onDismiss = { restoring = null })
    }
    if (saveAs) NameDialog("Save as new preset", "", "Save", onConfirm = { c.saveAsNew(it); saveAs = false }, onDismiss = { saveAs = false })
}

@Composable
private fun PresetRow(p: Preset, active: Boolean, onApply: () -> Unit, actions: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) cs.primaryContainer else cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniCurve(p.gains, Modifier.size(56.dp, 40.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(p.name + if (active) "  ● Active" else "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    p.modified -> "Modified — tap ↻ to restore original"
                    p.description.isNotEmpty() -> p.description
                    else -> "Custom preset"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (p.modified) RdmColors.warning else cs.onSurfaceVariant)
            }
            actions()
        }
    }
}
