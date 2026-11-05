package com.rdm.eq.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.BuildConfig
import com.rdm.eq.EqController
import com.rdm.eq.audio.SessionStatus
import com.rdm.eq.audio.TestSignalPlayer
import com.rdm.eq.ui.components.KeyValue
import com.rdm.eq.ui.components.SectionCard
import com.rdm.eq.ui.components.StatusPill
import com.rdm.eq.ui.components.SupportBadge
import com.rdm.eq.ui.theme.RdmColors
import com.rdm.eq.util.DiagLog

@Composable
fun StatusScreen(c: EqController, onRequestNotifications: () -> Unit) {
    val s by c.settings.collectAsStateWithLifecycle()
    val st by c.status.collectAsStateWithLifecycle()
    val log by DiagLog.lines.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val caps = c.capabilities
    var playing by remember { mutableStateOf(c.testSignal.isPlaying) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++; c.engine.refresh() }
    LaunchedEffect(Unit) { c.engine.refresh() }

    val notifOk = remember(tick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val (summary, color) = engineSummary(s, st)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoCol = maxWidth > 760.dp
        val left: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard("Audio engine") {
                    StatusPill(summary, color)
                    KeyValue("Equalizer switch", if (s.enabled) "On" else "Off (bypassed)")
                    KeyValue("Background service", if (st.serviceRunning) "Running" else "Not running",
                        if (st.serviceRunning) RdmColors.success else RdmColors.warning)
                    KeyValue("Processing backend", st.anyBackend?.label ?: "None attached yet")
                    KeyValue("Engine preference", s.engine.label)
                    KeyValue("Session mode", if (s.sessionMode) "On — attaches to apps that announce their audio" else "Off")
                    KeyValue("Global mode (experimental)", when {
                        !s.globalMode -> "Off"
                        st.global?.processing == true -> "Effect attached to output mix — confirm by ear"
                        st.global?.error != null -> "Failed: ${st.global?.error}"
                        else -> "Requested, not active"
                    })
                    KeyValue("Streams processed now", "${st.activeSessions}")
                }
                SectionCard("Audio streams") {
                    if (st.sessions.isEmpty()) Text(
                        "No media app has announced an audio session yet. Start playback in Spotify or a local music player. " +
                            "YouTube and some others never announce their session — see Known limitations.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    st.sessions.forEach { SessionRow(it) }
                    st.global?.let { SessionRow(it) }
                }
                SectionCard("Hear it for yourself") {
                    Text("Plays a test signal through RDM Eq's own audio session. Switch EQ on/off or try presets while it plays to confirm the DSP engine changes the sound on this head unit.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TestSignalPlayer.Signal.entries.forEach { sig ->
                            OutlinedButton(onClick = { c.testSignal.start(sig); playing = true }) { Text(sig.label) }
                        }
                        Button(onClick = { c.testSignal.stop(); playing = false }, enabled = playing) { Text("Stop") }
                    }
                }
                SectionCard("Permissions & background") {
                    SupportBadge(true, "Modify audio settings — granted automatically")
                    Row {
                        Column(Modifier.weight(1f)) { SupportBadge(notifOk, "Notifications (shows that the engine is running)") }
                        if (!notifOk) TextButton(onClick = onRequestNotifications) { Text("Allow") }
                    }
                    Row {
                        Column(Modifier.weight(1f)) { SupportBadge(batteryOk, "Exempt from battery optimisation") }
                        if (!batteryOk) TextButton(onClick = {
                            runCatching {
                                // Opens the system list; no special permission needed. Pick RDM Eq → "Don't optimise".
                                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }.onFailure { Toast.makeText(ctx, "This head unit does not offer that setting", Toast.LENGTH_LONG).show() }
                        }) { Text("Open settings") }
                    }
                    SupportBadge(if (s.autoStart) null else false,
                        if (s.autoStart) "Auto-start after boot enabled — whether DiLink delivers the boot event is unverified" else "Auto-start after boot is off")
                }
            }
        }
        val right: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard("Supported audio effects (probed on this unit)") {
                    SupportBadge(caps.dynamicsProcessing, "Dynamics Processing — 10-band EQ, preamp, limiter, balance")
                    SupportBadge(caps.equalizer, "Classic Equalizer — fallback")
                    SupportBadge(caps.bassBoost, "Bass Boost effect (not needed; bass is shaped in the EQ)")
                    SupportBadge(caps.virtualizer, "Virtualizer (not used in a car)")
                    SupportBadge(caps.loudnessEnhancer, "Loudness Enhancer (not used — it is a gain stage, not loudness compensation)")
                    var showAll by remember { mutableStateOf(false) }
                    TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Hide effect list" else "Show all ${caps.effects.size} effects") }
                    if (showAll) caps.effects.forEach {
                        Text("• ${it.type}: ${it.name} (${it.implementor})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                SectionCard("This head unit") {
                    KeyValue("Device", "${caps.manufacturer} ${caps.model} (${caps.brand})")
                    KeyValue("Android", "${caps.androidRelease} (API ${caps.sdkInt})")
                    KeyValue("Firmware build", caps.buildDisplay)
                    KeyValue("Android Automotive OS", if (caps.isAndroidAutomotive) "Yes" else "No (standard Android build)")
                    KeyValue("Output sample rate", "${caps.outputSampleRate} Hz, ${caps.framesPerBuffer} frames/buffer")
                    KeyValue("Output devices", caps.outputDevices.joinToString("\n").ifEmpty { "—" })
                    KeyValue("RDM Eq version", BuildConfig.VERSION_NAME)
                }
                SectionCard("Known limitations") {
                    listOf(
                        "Android lets an equalizer app process another app's audio only through that app's audio session ID. Apps share it by broadcasting it when playback starts.",
                        "Spotify (with \"Device broadcast status\"/equalizer support), most local players and ExoPlayer/Media3-based apps announce their session → processed.",
                        "YouTube and many video apps do not announce a session → they bypass RDM Eq unless Global mode works on this unit.",
                        "Global mode (session 0) is deprecated by Android; whether the head unit honours it depends on BYD's audio build. It may attach without changing the sound — always confirm by ear.",
                        "Capturing or re-routing other apps' audio is blocked by Android security and is not attempted.",
                        "The car's own amplifier DSP sits after RDM Eq; RDM Eq cannot see or change it.",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                SectionCard("Diagnostic log", trailing = {
                    TextButton(onClick = {
                        val text = buildDiagnostics(c, st.sessions) + "\n\n" + log.joinToString("\n")
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("RDM Eq diagnostics", text))
                        Toast.makeText(ctx, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                }) {
                    HorizontalDivider()
                    log.takeLast(30).reversed().forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Audio status", style = MaterialTheme.typography.titleLarge)
            if (twoCol) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { left(Modifier.weight(1f)); right(Modifier.weight(1f)) }
            else { left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun SessionRow(x: SessionStatus) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("${x.source}  ·  session ${x.sessionId}", style = MaterialTheme.typography.titleMedium)
        Text(
            listOf(
                "effect: ${x.backend?.name ?: "none"}",
                "control: ${if (x.hasControl) "yes" else "no"}",
                "enabled: ${if (x.enabled) "yes" else "no"}",
                "params verified: ${if (x.verified) "yes" else "no"}",
                "playing: ${when (x.playing) { true -> "yes"; false -> "no"; null -> "unknown" }}",
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = if (x.processing) RdmColors.success else RdmColors.warning,
        )
        if (x.detail.isNotEmpty()) Text(x.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        x.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RdmColors.danger) }
    }
}

private fun buildDiagnostics(c: EqController, sessions: List<SessionStatus>): String {
    val k = c.capabilities
    return buildString {
        appendLine("RDM Eq ${BuildConfig.VERSION_NAME}")
        appendLine("Device: ${k.manufacturer} ${k.model} / ${k.brand}; Android ${k.androidRelease} (API ${k.sdkInt}); build ${k.buildDisplay}; AAOS=${k.isAndroidAutomotive}")
        appendLine("Effects: DP=${k.dynamicsProcessing} EQ=${k.equalizer} BB=${k.bassBoost} Virt=${k.virtualizer} LE=${k.loudnessEnhancer}")
        appendLine("Outputs: ${k.outputDevices.joinToString("; ")}; ${k.outputSampleRate} Hz")
        appendLine("Settings: ${c.settings.value}")
        sessions.forEach { appendLine("Session ${it.sessionId} ${it.source}: ${it.backend} control=${it.hasControl} enabled=${it.enabled} verified=${it.verified} ch=${it.channelCount} err=${it.error}") }
        appendLine("Global: ${c.status.value.global}")
    }
}
