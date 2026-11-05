package com.rdm.eq.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.update.ReleaseParser
import com.rdm.eq.update.UpdateManager
import com.rdm.eq.update.UpdateState
import com.rdm.eq.update.statusText
import com.rdm.eq.ui.theme.RdmColors

/** GitHub release notes are Markdown; show them as clean plain text. */
fun cleanNotes(md: String): String = md.lines()
    .dropWhile { it.isBlank() || it.trim().trimStart('#').trim().equals("What's new", ignoreCase = true) }
    .joinToString("\n") { l ->
    l.trimEnd()
        .replace(Regex("^#{1,6}\\s*"), "")
        .replace(Regex("^\\s*[-*+]\\s+"), "• ")
        .replace("**", "").replace("__", "").replace("`", "")
}.trim().ifEmpty { "No release notes." }

/** The update dialog. Its buttons follow the current step: Update Now → progress → Install. */
@Composable
fun UpdateDialog(u: UpdateManager) {
    val show by u.dialog.collectAsStateWithLifecycle()
    val st by u.state.collectAsStateWithLifecycle()
    if (!show) return
    val ctx = LocalContext.current
    val update = u.currentUpdate()

    if (update == null) {
        // Manual check results without an update: up to date, or a check error.
        when (st) {
            is UpdateState.UpToDate, is UpdateState.Failed -> AlertDialog(
                onDismissRequest = u::dismiss,
                title = { Text(if (st is UpdateState.UpToDate) "No update available" else "Couldn't check for updates") },
                text = { Text(st.statusText()) },
                confirmButton = {
                    if (st is UpdateState.Failed) TextButton(onClick = { u.check(manual = true) }) { Text("Retry") }
                    else TextButton(onClick = u::dismiss) { Text("OK") }
                },
                dismissButton = { if (st is UpdateState.Failed) TextButton(onClick = u::dismiss) { Text("Close") } },
            )
            else -> Unit // still checking: no dialog yet
        }
        return
    }

    val m = update.metadata
    val busy = st is UpdateState.Downloading || st is UpdateState.Verifying || st is UpdateState.Installing
    AlertDialog(
        onDismissRequest = { if (!busy) u.later() },
        properties = DialogProperties(dismissOnClickOutside = !busy, usePlatformDefaultWidth = false),
        modifier = Modifier.padding(32.dp).widthInMax(),
        title = {
            Column {
                Text("Update available", style = MaterialTheme.typography.titleLarge)
                Text(
                    "RDM Eq ${m.versionName}" + (if (update.release.prerelease) " (beta)" else "") +
                        " · released ${ReleaseParser.formatDate(update.release.publishedAt)} · you have ${u.installedVersionName}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("What's new", style = MaterialTheme.typography.titleMedium)
                        Text(cleanNotes(update.release.notes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                UpdateProgress(st)
                Text(
                    "Your presets and settings are kept. Android will ask you to confirm the installation. Music keeps playing while downloading.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            when (st) {
                is UpdateState.Available -> Button(onClick = u::download) { Text("Update Now", fontWeight = FontWeight.Bold) }
                is UpdateState.Downloading -> OutlinedButton(onClick = u::cancelDownload) { Text("Pause download") }
                is UpdateState.ReadyToInstall -> Button(onClick = u::install) { Text("Install", fontWeight = FontWeight.Bold) }
                is UpdateState.NeedsInstallPermission -> Button(onClick = {
                    if (!u.openInstallPermissionSettings()) Toast.makeText(ctx,
                        "This head unit doesn't offer the 'Install unknown apps' setting. See the README for manual installation.", Toast.LENGTH_LONG).show()
                }) { Text("Allow installs") }
                is UpdateState.Failed -> Button(onClick = {
                    if ((st as UpdateState.Failed).kind == com.rdm.eq.update.UpdateException.Kind.INSTALL) u.install() else u.download()
                }) { Text("Retry") }
                else -> Unit
            }
        },
        dismissButton = { if (!busy) TextButton(onClick = u::later) { Text("Later") } },
    )
}

@Composable
private fun Modifier.widthInMax(): Modifier = this.then(Modifier.width(720.dp))

@Composable
fun UpdateProgress(st: UpdateState) {
    when (st) {
        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Downloading… ${st.percent} %", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (st.total > 0) Text("%.1f / %.1f MB".format(st.downloaded / 1e6, st.total / 1e6), style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(progress = { st.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.Verifying -> Column { Text("Verifying checksum and signature…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        is UpdateState.ReadyToInstall -> Text("✓ Downloaded and verified (SHA-256 and signing certificate match).", color = RdmColors.success)
        is UpdateState.NeedsInstallPermission -> Text(
            "Android needs your permission for RDM Eq to install updates. Tap \"Allow installs\", switch it on, then come back.",
            color = RdmColors.warning,
        )
        is UpdateState.Installing -> Text("Opening Android's installer… confirm with \"Update\".")
        is UpdateState.Failed -> Text(st.message, color = RdmColors.danger)
        else -> Unit
    }
}
