package com.rdm.eq.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.rdm.eq.util.DiagLog
import kotlin.random.Random

/**
 * Plays pink noise or a bass/treble music-like loop through RDM Eq's own audio session so the
 * user can confirm on the head unit, by ear, that the DSP engine really changes the sound.
 * This is a diagnostic tool, not a music player.
 */
class TestSignalPlayer(private val context: Context, private val engine: AudioEngine) {
    enum class Signal(val label: String) { PINK_NOISE("Pink noise"), MUSIC_LOOP("Bass + hi-hat loop") }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private var sessionId = 0
    val isPlaying get() = running

    fun start(signal: Signal) {
        stop()
        val am = context.getSystemService(AudioManager::class.java)
        sessionId = am.generateAudioSessionId()
        val rate = 48000
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(maxOf(minBuf, 4096) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(sessionId)
            .build()
        track = t
        engine.openSession(sessionId, AudioEngine.TEST_SIGNAL_TAG)
        running = true
        t.play()
        thread = Thread({ render(t, rate, signal) }, "rdm-test-signal").apply { start() }
        DiagLog.i("Test signal started (${signal.label}) on session $sessionId")
    }

    fun stop() {
        if (!running && track == null) return
        running = false
        thread?.join(500); thread = null
        track?.runCatching { stop(); release() }; track = null
        if (sessionId != 0) engine.closeSession(sessionId)
        sessionId = 0
    }

    private fun render(t: AudioTrack, rate: Int, signal: Signal) {
        val frames = 1024
        val buf = ShortArray(frames * 2)
        val rnd = Random(42)
        // Paul Kellet pink-noise filter state
        var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
        var n = 0L
        val amp = 0.125f // about -18 dBFS so there is headroom for boosts
        while (running) {
            for (i in 0 until frames) {
                val s: Float = when (signal) {
                    Signal.PINK_NOISE -> {
                        val w = rnd.nextFloat() * 2f - 1f
                        b0 = 0.99886f * b0 + w * 0.0555179f; b1 = 0.99332f * b1 + w * 0.0750759f
                        b2 = 0.96900f * b2 + w * 0.1538520f; b3 = 0.86650f * b3 + w * 0.3104856f
                        b4 = 0.55000f * b4 + w * 0.5329522f; b5 = -0.7616f * b5 - w * 0.0168980f
                        val p = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
                        b6 = w * 0.115926f
                        p
                    }
                    Signal.MUSIC_LOOP -> {
                        val tSec = n.toFloat() / rate
                        val beat = tSec % 0.5f
                        val bass = kotlin.math.sin(2 * Math.PI * 55.0 * tSec).toFloat() * kotlin.math.exp(-beat * 6f)
                        val mid = kotlin.math.sin(2 * Math.PI * 440.0 * tSec).toFloat() * 0.25f
                        val hat = if (tSec % 0.25f < 0.03f) (rnd.nextFloat() * 2f - 1f) * 0.5f else 0f
                        (bass * 0.7f + mid + hat) * 0.8f
                    }
                }
                val v = (s * amp * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                buf[2 * i] = v; buf[2 * i + 1] = v
                n++
            }
            if (t.write(buf, 0, buf.size) < 0) break
        }
    }
}
