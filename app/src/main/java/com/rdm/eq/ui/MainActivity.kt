package com.rdm.eq.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.eq.EqController
import com.rdm.eq.R
import com.rdm.eq.RdmEqApp
import com.rdm.eq.service.EqService
import com.rdm.eq.ui.components.UpdateDialog
import com.rdm.eq.update.UpdateManager
import com.rdm.eq.update.UpdateState
import com.rdm.eq.ui.screens.AboutScreen
import com.rdm.eq.ui.screens.EqualizerScreen
import com.rdm.eq.ui.screens.PresetsScreen
import com.rdm.eq.ui.screens.SettingsScreen
import com.rdm.eq.ui.screens.SoundScreen
import com.rdm.eq.ui.screens.StatusScreen
import com.rdm.eq.ui.theme.RdmEqTheme

enum class Screen(val label: String) { EQ("Equalizer"), SOUND("Sound"), PRESETS("Presets"), STATUS("Status"), SETTINGS("Settings"), ABOUT("About") }

class MainActivity : ComponentActivity() {
    private val controller: EqController get() = RdmEqApp.controller(this)
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePanelIntent(intent)
        if (savedInstanceState == null) requestNotificationsIfNeeded()
        setContent {
            val s by controller.settings.collectAsStateWithLifecycle()
            RdmEqTheme(s.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) {
                        AppRoot(controller, RdmEqApp.updates(this@MainActivity)) { requestNotificationsIfNeeded(force = true) }
                        UpdateDialog(RdmEqApp.updates(this@MainActivity))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePanelIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (controller.settings.value.enabled) EqService.start(this)
        RdmEqApp.updates(this).maybeAutoCheck()
    }

    override fun onResume() {
        super.onResume()
        RdmEqApp.updates(this).onResume()
    }

    /** A player's "Equalizer" button opens us with its session ID — attach directly. */
    private fun handlePanelIntent(i: Intent?) {
        if (i?.action != AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) return
        val id = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val pkg = i.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: callingPackage ?: "media app"
        if (id > 0) controller.engine.openSession(id, pkg)
    }

    private fun requestNotificationsIfNeeded(force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("ui", MODE_PRIVATE)
            if (force || !prefs.getBoolean("asked_notif", false)) {
                prefs.edit().putBoolean("asked_notif", true).apply()
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun AppRoot(c: EqController, updates: UpdateManager, onRequestNotifications: () -> Unit) {
    val ust by updates.state.collectAsStateWithLifecycle()
    val hasUpdate = updates.currentUpdate() != null && ust !is UpdateState.Failed
    var screen by rememberSaveable { mutableStateOfScreen() }
    val icon: @Composable (Screen) -> Painter = { s ->
        when (s) {
            Screen.EQ -> painterResource(R.drawable.ic_nav_eq)
            Screen.SOUND -> painterResource(R.drawable.ic_nav_sound)
            Screen.PRESETS -> rememberVectorPainter(Icons.AutoMirrored.Filled.List)
            Screen.STATUS -> rememberVectorPainter(Icons.Default.Info)
            Screen.SETTINGS -> rememberVectorPainter(Icons.Default.Settings)
            Screen.ABOUT -> rememberVectorPainter(Icons.Default.Person)
        }
    }
    val content: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            when (screen) {
                Screen.EQ -> EqualizerScreen(c)
                Screen.SOUND -> SoundScreen(c)
                Screen.PRESETS -> PresetsScreen(c)
                Screen.STATUS -> StatusScreen(c, onRequestNotifications)
                Screen.SETTINGS -> SettingsScreen(c, updates) { screen = Screen.ABOUT }
                Screen.ABOUT -> AboutScreen(updates)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= maxHeight || maxWidth > 840.dp) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(Modifier.verticalScroll(rememberScrollState()), containerColor = MaterialTheme.colorScheme.surfaceContainerLow, header = {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = "RDM Eq", modifier = Modifier.padding(top = 12.dp).size(40.dp))
                }) {
                    Screen.entries.forEach { s ->
                        NavigationRailItem(
                            selected = screen == s, onClick = { screen = s },
                            icon = {
                                BadgedBox(badge = { if (s == Screen.ABOUT && hasUpdate) Badge() }) {
                                    Icon(icon(s), contentDescription = null, modifier = Modifier.size(28.dp))
                                }
                            },
                            label = { Text(s.label) },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
                content(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f))
                NavigationBar {
                    Screen.entries.forEach { s ->
                        NavigationBarItem(selected = screen == s, onClick = { screen = s },
                            icon = { BadgedBox(badge = { if (s == Screen.ABOUT && hasUpdate) Badge() }) { Icon(icon(s), contentDescription = null) } }, label = { Text(s.label) })
                    }
                }
            }
        }
    }
}

private fun mutableStateOfScreen() = androidx.compose.runtime.mutableStateOf(Screen.EQ)
