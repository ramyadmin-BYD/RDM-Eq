package com.rdm.eq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rdm.eq.R
import com.rdm.eq.RdmEqApp
import com.rdm.eq.ui.MainActivity
import com.rdm.eq.util.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps RDM Eq's process alive so it can hear OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION broadcasts
 * while the user is in another media app. Most players send those broadcasts implicitly, which
 * Android 8+ only delivers to receivers registered at runtime like this one.
 */
class EqService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = SessionBroadcastReceiver.handle(c, intent)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification("Starting…"), type)
        ContextCompat.registerReceiver(
            this, sessionReceiver,
            IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            },
            ContextCompat.RECEIVER_EXPORTED, // sent by other apps
        )
        val c = RdmEqApp.controller(this)
        c.engine.setServiceRunning(true)
        scope.launch {
            combine(c.settings, c.status) { s, st ->
                val preset = c.activePreset()?.name ?: "Custom"
                when {
                    !s.enabled -> "Bypassed · $preset"
                    st.activeSessions > 0 -> "Processing ${st.activeSessions} audio stream(s) · $preset"
                    st.globalActive -> "Global mode attached (check by ear) · $preset"
                    else -> "Waiting for a media app to announce its audio · $preset"
                }
            }.distinctUntilChanged().collect { text ->
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
            }
        }
        DiagLog.i("EqService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            RdmEqApp.controller(this).setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.getParcelableExtraCompat()?.let { SessionBroadcastReceiver.handle(this, it) }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(): Intent? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(EXTRA_FORWARDED, Intent::class.java) else getParcelableExtra(EXTRA_FORWARDED)

    override fun onDestroy() {
        runCatching { unregisterReceiver(sessionReceiver) }
        RdmEqApp.controller(this).engine.setServiceRunning(false)
        scope.cancel()
        DiagLog.i("EqService stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "RDM Eq engine", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows that RDM Eq is processing audio"
            setShowBadge(false)
        })
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, EqService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_eq)
            .setContentTitle("RDM Eq")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "engine"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.rdm.eq.STOP"
        const val EXTRA_FORWARDED = "forwarded"

        fun start(c: Context, forwarded: Intent? = null) {
            try {
                ContextCompat.startForegroundService(c, Intent(c, EqService::class.java).apply {
                    forwarded?.let { putExtra(EXTRA_FORWARDED, it) }
                })
            } catch (t: Throwable) {
                // e.g. ForegroundServiceStartNotAllowedException when started from the background.
                DiagLog.w("Could not start EqService", t)
            }
        }
    }
}
