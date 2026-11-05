package com.rdm.eq.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.BuildConfig
import com.rdm.eq.EqController
import com.rdm.eq.model.EnginePreference
import com.rdm.eq.model.ThemeMode
import com.rdm.eq.ui.components.ConfirmDialog
import com.rdm.eq.ui.components.SectionCard
import com.rdm.eq.util.DiagLog
import com.rdm.eq.update.UpdateManager
import com.rdm.eq.update.UpdateState
import com.rdm.eq.update.statusText

@Composable
fun SettingsScreen(c: EqController, u: UpdateManager, onOpenAbout: () -> Unit) {
    val s by c.settings.collectAsStateWithLifecycle()
    val presets by c.presets.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var pickStartup by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(c.exportPresets().toByteArray()) } }
            .onSuccess { Toast.makeText(ctx, "Presets exported", Toast.LENGTH_SHORT).show() }
            .onFailure { DiagLog.e("Export failed", it); Toast.makeText(ctx, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { c.importPresets(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }) }
            .onSuccess { Toast.makeText(ctx, "Imported ${it.customsAdded} custom, ${it.builtInsUpdated} built-in presets", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(ctx, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoCol = maxWidth > 760.dp
        val left: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard("Appearance") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeMode.entries.forEach { t ->
                            FilterChip(selected = s.theme == t, onClick = { c.setTheme(t) },
                                label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(vertical = 10.dp)) })
                        }
                    }
                }
                SectionCard("Startup") {
                    val startupName = if (s.startupPresetId.isEmpty()) "Last used settings" else presets.firstOrNull { it.id == s.startupPresetId }?.name ?: "Last used settings"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Default startup preset")
                            Text(startupName, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedButton(onClick = { pickStartup = true }) { Text("Change") }
                    }
                    ToggleRow("Start automatically after the head unit boots",
                        "Uses Android's boot broadcast. Some head units suspend instead of rebooting, or block it — test in your car.",
                        s.autoStart, c::setAutoStart)
                }
                SectionCard("Preset backup") {
                    Text("Export your custom and edited presets to a JSON file (e.g. on a USB stick) or import them back.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { exporter.launch("rdm-eq-presets.json") }) { Text("Export presets") }
                        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import presets") }
                    }
                }
            }
        }
        val right: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard("Audio processing") {
                    ToggleRow("Session mode (recommended)",
                        "Attach to audio from apps that announce their session (Spotify, most music players).",
                        s.sessionMode, c::setSessionMode)
                    ToggleRow("Global mode — experimental",
                        "Attach to the whole output mix (session 0). Deprecated by Android; works only if the head unit's audio system allows it. Confirm by ear.",
                        s.globalMode, c::setGlobalMode)
                    Text("Processing engine", style = MaterialTheme.typography.titleMedium)
                    EnginePreference.entries.forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable { c.setEngine(p) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = s.engine == p, onClick = { c.setEngine(p) })
                            Text(p.label)
                        }
                    }
                }
                SectionCard("Reset") {
                    Text("Restores every setting and built-in preset, and deletes custom presets.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { confirmReset = true }) { Text("Restore defaults") }
                }
                SectionCard("Updates") {
                    val ust by u.state.collectAsStateWithLifecycle()
                    val auto by u.autoCheck.collectAsStateWithLifecycle()
                    val beta by u.includeBeta.collectAsStateWithLifecycle()
                    Text("Installed: RDM Eq ${u.installedVersionName} (build ${u.installedVersionCode})")
                    Text(ust.statusText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { u.check(manual = true) }, enabled = ust !is UpdateState.Checking && ust !is UpdateState.Downloading) { Text("Check for Updates") }
                        OutlinedButton(onClick = onOpenAbout) { Text("About RDM Eq") }
                    }
                    ToggleRow("Check automatically", "When the app opens, at most every 12 hours. Uses the internet only to read GitHub Releases.", auto, u::setAutoCheck)
                    ToggleRow("Include beta versions", "Also offer pre-release versions. Leave off for stable updates only.", beta, u::setIncludeBeta)
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            if (twoCol) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { left(Modifier.weight(1f)); right(Modifier.weight(1f)) }
            else { left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth()) }
        }
    }

    if (pickStartup) AlertDialog(
        onDismissRequest = { pickStartup = false },
        title = { Text("Default startup preset") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { RadioRow("Last used settings", s.startupPresetId.isEmpty()) { c.setStartupPreset(""); pickStartup = false } }
                items(presets, key = { it.id }) { p ->
                    RadioRow(p.name, s.startupPresetId == p.id) { c.setStartupPreset(p.id); pickStartup = false }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickStartup = false }) { Text("Close") } },
    )
    if (confirmReset) ConfirmDialog("Restore defaults?", "All settings return to factory values and custom presets are deleted. Export them first if you want to keep them.", "Restore",
        onConfirm = { c.restoreDefaults(); confirmReset = false }, onDismiss = { confirmReset = false })
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
