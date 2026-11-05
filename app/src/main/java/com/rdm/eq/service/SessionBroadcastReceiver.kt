package com.rdm.eq.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.rdm.eq.RdmEqApp

/**
 * Handles the standard "audio effect control session" broadcasts that well-behaved media apps
 * send when playback starts/stops. This manifest receiver only gets explicitly-targeted
 * broadcasts; the running EqService catches the (more common) implicit ones.
 */
class SessionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        handle(context, intent)
        if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION &&
            RdmEqApp.controller(context).settings.value.enabled
        ) EqService.start(context)
    }

    companion object {
        fun handle(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown app"
            val engine = RdmEqApp.controller(context).engine
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> engine.openSession(id, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> engine.closeSession(id)
            }
        }
    }
}
