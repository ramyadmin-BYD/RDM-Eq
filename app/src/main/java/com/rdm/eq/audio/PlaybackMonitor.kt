package com.rdm.eq.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper

/**
 * Reports which media is playing, using only public AudioManager APIs.
 * Android does not expose other apps' session IDs as a field; recent versions include
 * "session:N" in the configuration's description, which is used when present.
 */
class PlaybackMonitor(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val sessionRegex = Regex("""session:(\d+)""")

    fun snapshot(): PlaybackSnapshot {
        val configs = am.activePlaybackConfigurations.filter { isMedia(it) }
        val ids = configs.map { c -> sessionRegex.find(c.toString())?.groupValues?.get(1)?.toIntOrNull() }
        return PlaybackSnapshot(
            anyMediaPlaying = configs.isNotEmpty(),
            playingSessions = if (ids.isNotEmpty() && ids.all { it != null }) ids.filterNotNull().toSet()
            else if (configs.isEmpty()) emptySet() else null,
        )
    }

    fun listen(onChange: () -> Unit) {
        am.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = onChange()
        }, Handler(Looper.getMainLooper()))
    }

    private fun isMedia(c: AudioPlaybackConfiguration): Boolean {
        val u = c.audioAttributes.usage
        return u == AudioAttributes.USAGE_MEDIA || u == AudioAttributes.USAGE_GAME || u == AudioAttributes.USAGE_UNKNOWN
    }
}
