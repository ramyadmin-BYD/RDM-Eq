package com.rdm.eq.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rdm.eq.RdmEqApp
import com.rdm.eq.util.DiagLog

/** adb shell am broadcast -n com.rdm.eq.debug/com.rdm.eq.debug.DebugControlReceiver --es cmd preset --es arg rock */
class DebugControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = RdmEqApp.controller(context)
        val arg = intent.getStringExtra("arg") ?: ""
        when (intent.getStringExtra("cmd")) {
            "enable" -> c.setEnabled(arg != "false")
            "preset" -> c.applyPreset(arg)
            "band" -> arg.split(":").takeIf { it.size == 2 }?.let { (i, db) -> c.setBand(i.toInt(), db.toFloat()) }
            "autoHeadroom" -> c.setAutoHeadroom(arg != "false")
            "engine" -> c.setEngine(com.rdm.eq.model.EnginePreference.valueOf(arg))
            "global" -> c.setGlobalMode(arg != "false")
            // Update testing: arg "http://10.0.2.2:8765|owner/repo" or "reset"; "updateCheck" runs a manual check.
            "updateSource" -> RdmEqApp.updates(context).let { u ->
                val parts = arg.split("|")
                if (arg == "reset" || parts.size != 2) u.setTestSource(null, null) else u.setTestSource(parts[0], parts[1])
            }
            "updateCheck" -> RdmEqApp.updates(context).check(manual = true)
        }
        DiagLog.i("debug cmd ${intent.getStringExtra("cmd")} $arg → ${c.status.value.activeSessions} active, update=${RdmEqApp.updates(context).state.value}")
    }
}
