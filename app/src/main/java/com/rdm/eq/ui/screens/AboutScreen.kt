package com.rdm.eq.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.BuildConfig
import com.rdm.eq.R
import com.rdm.eq.update.UpdateManager
import com.rdm.eq.update.UpdateState
import com.rdm.eq.update.statusText
import com.rdm.eq.ui.components.KeyValue
import com.rdm.eq.ui.components.SectionCard
import com.rdm.eq.ui.components.UpdateProgress
import com.rdm.eq.ui.theme.RdmColors
import java.text.DateFormat
import java.util.Date

@Composable
fun AboutScreen(u: UpdateManager) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 760.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeveloperCreditCard(wide)
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VersionCard(u, Modifier.weight(1f)); AboutInfoCard(Modifier.weight(1f))
            } else { VersionCard(u, Modifier.fillMaxWidth()); AboutInfoCard(Modifier.fillMaxWidth()) }
        }
    }
}

/** First element of the About screen. */
@Composable
fun DeveloperCreditCard(wide: Boolean) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Surface(
        shape = shape,
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.55f), cs.secondary.copy(alpha = 0.25f)))),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = cs.primary.copy(alpha = 0.25f), spotColor = cs.primary.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.12f), cs.secondary.copy(alpha = 0.04f), cs.surfaceContainer)),
            ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (wide) 40.dp else 22.dp, vertical = if (wide) 30.dp else 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(if (wide) 84.dp else 60.dp).clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(if (wide) 56.dp else 40.dp)) }
                Spacer(Modifier.width(if (wide) 28.dp else 18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Designed and Developed by",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Eng. Ramy Abdelaziz",
                        style = TextStyle(
                            brush = Brush.linearGradient(listOf(cs.primary, cs.secondary)),
                            fontSize = if (wide) 40.sp else 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                        ),
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(72.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(cs.primary, cs.secondary))))
                }
            }
        }
    }
}

@Composable
private fun VersionCard(u: UpdateManager, modifier: Modifier) {
    val st by u.state.collectAsStateWithLifecycle()
    val busy = st is UpdateState.Checking || st is UpdateState.Downloading || st is UpdateState.Verifying || st is UpdateState.Installing
    SectionCard("Application", modifier) {
        KeyValue("Application name", "RDM Eq")
        KeyValue("Installed version", u.installedVersionName)
        KeyValue("Build number", u.installedVersionCode.toString())
        KeyValue("Update status", st.statusText(), when (st) {
            is UpdateState.UpToDate -> MaterialTheme.colorScheme.primary
            is UpdateState.Failed -> RdmColors.danger
            is UpdateState.Available, is UpdateState.ReadyToInstall -> MaterialTheme.colorScheme.primary
            else -> androidx.compose.ui.graphics.Color.Unspecified
        })
        if (u.lastCheckedAt > 0) KeyValue("Last checked", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(u.lastCheckedAt)))
        UpdateProgress(st)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { u.check(manual = true) }, enabled = !busy) {
                Text(if (st is UpdateState.Checking) "Checking…" else "Check for Updates")
            }
            if (u.currentUpdate() != null) OutlinedButton(onClick = u::showDialog) { Text("View update") }
        }
    }
}

@Composable
private fun AboutInfoCard(modifier: Modifier) {
    SectionCard("About", modifier) {
        Text("RDM Eq ${BuildConfig.VERSION_NAME}")
        Text(
            "A 7-band equalizer for BYD DiLink 5 / 5.1. The equalizer works fully offline. " +
                "The internet is used only to check GitHub for RDM Eq updates — no analytics, ads or accounts.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        Text("Updates: github.com/${BuildConfig.UPDATE_REPO}/releases", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
