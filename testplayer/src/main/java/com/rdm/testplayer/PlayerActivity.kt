package com.rdm.testplayer

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/** Plays white noise in its own audio session and announces it like a well-behaved music player. */
class PlayerActivity : Activity() {
    private var track: AudioTrack? = null
    @Volatile private var run = false
    private var session = 0
    private var viz: Visualizer? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        session = getSystemService(AudioManager::class.java).generateAudioSessionId()
        setContentView(TextView(this).apply { text = "Test player: session $session"; textSize = 32f })
    }

    override fun onResume() {
        super.onResume()
        if (track != null) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setSessionId(session).build()
        track = t; run = true; t.play()
        Thread {
            val buf = ShortArray(2048); val r = java.util.Random(1)
            while (run) { for (i in buf.indices) buf[i] = (r.nextGaussian() * 7000).toInt().toShort(); if (t.write(buf, 0, buf.size) < 0) break }
        }.start()
        sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC))
        Log.i("RDMTestPlayer", "Playing on session $session, broadcast sent")
        // Visualizer is inserted LAST in the session's effect chain, so it measures the signal after RDM Eq.
        window.decorView.postDelayed({ startMeter() }, 3000)
    }

    private fun startMeter() {
        runCatching {
            val v = Visualizer(0) // output mix: after all per-session effects
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            v.enabled = true
            viz = v
            Thread {
                val fft = ByteArray(v.captureSize)
                val binHz = 48000f / v.captureSize
                fun band(lo: Float, hi: Float): Double {
                    var e = 0.0; var n = 0
                    for (k in 1 until fft.size / 2) { val f = k * binHz; if (f in lo..hi) { val re = fft[2 * k].toDouble(); val im = fft[2 * k + 1].toDouble(); e += re * re + im * im; n++ } }
                    return 10 * Math.log10(e / maxOf(n, 1) + 1e-9)
                }
                while (run) {
                    Thread.sleep(1000)
                    val acc = DoubleArray(3); val reps = 20
                    repeat(reps) { v.getFft(fft); acc[0] += band(40f, 200f); acc[1] += band(800f, 1250f); acc[2] += band(8000f, 16000f); Thread.sleep(20) }
                    Log.i("RDMTestPlayer", String.format("LEVELS low=%.1f mid=%.1f high=%.1f dB", acc[0] / reps, acc[1] / reps, acc[2] / reps))
                }
            }.start()
        }.onFailure { Log.w("RDMTestPlayer", "Visualizer unavailable", it) }
    }

    override fun onDestroy() {
        run = false
        viz?.release(); viz = null
        sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session).putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName))
        track?.run { stop(); release() }; track = null
        super.onDestroy()
    }
}
