package com.rdm.eq

import android.app.Application
import android.content.Context
import com.rdm.eq.audio.AudioEngine
import com.rdm.eq.audio.PlaybackMonitor
import com.rdm.eq.data.PresetRepository
import com.rdm.eq.data.SettingsRepository
import com.rdm.eq.data.SharedPrefsStore
import com.rdm.eq.update.UpdateManager
import com.rdm.eq.util.DiagLog

class RdmEqApp : Application() {
    lateinit var controller: EqController
        private set
    lateinit var updates: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        val store = SharedPrefsStore(this)
        controller = EqController(this, SettingsRepository(store), PresetRepository(store), AudioEngine(PlaybackMonitor(this)::snapshot))
        updates = UpdateManager(this)
        DiagLog.i("RDM Eq ${BuildConfig.VERSION_NAME} started on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
    }

    companion object {
        fun controller(c: Context) = (c.applicationContext as RdmEqApp).controller
        fun updates(c: Context) = (c.applicationContext as RdmEqApp).updates
    }
}
