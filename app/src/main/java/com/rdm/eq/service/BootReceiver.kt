package com.rdm.eq.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rdm.eq.BuildConfig
import com.rdm.eq.R
import com.rdm.eq.RdmEqApp
import com.rdm.eq.ui.MainActivity
import com.rdm.eq.util.DiagLog

/** Starts the engine after boot / app update (if enabled) and announces a finished update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = RdmEqApp.controller(context).settings.value
        DiagLog.i("Boot event ${intent.action}; autoStart=${s.autoStart} enabled=${s.enabled}")
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) notifyUpdated(context)
        if (s.autoStart && s.enabled) EqService.start(context)
    }

    /** Android does not relaunch an app after it updates itself, so offer a one-tap way back in. */
    private fun notifyUpdated(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("updates", "RDM Eq updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            nm.notify(2, NotificationCompat.Builder(context, "updates")
                .setSmallIcon(R.drawable.ic_stat_eq)
                .setContentTitle("RDM Eq updated to ${BuildConfig.VERSION_NAME}")
                .setContentText("Your presets and settings were kept. Tap to open.")
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
