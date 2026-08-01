package com.megamusicmaker.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.tanh

/**
 * Real-time polyphonic mixer + sample-accurate step sequencer.
 *
 * One dedicated audio thread renders 512-frame buffers into a low-latency
 * AudioTrack. Sequencer hits are scheduled at exact frame offsets inside each
 * buffer, so the groove is sample-accurate - tighter than trigger-on-a-timer
 * apps can ever be. The same mix bus feeds the song recorder, so what you hear
 * is exactly what gets saved.
 */
class AudioEngine {

    companion object {
        const val SR = Synth.SR
        const val STEPS = 16
        const val TRACKS = 8       // 6 synth drums + 2 of your own recorded sounds
        const val MIC_SLOTS = 4
        private const val MAX_VOICES = 64
        private const val BUF_FRAMES = 512
    }

    private class Voice(val sample: FloatArray, val gain: Float, var offset: Int) {
        var pos = 0
    }

    private val lock = Any()
    private val voices = ArrayList<Voice>()
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile var playing = false
        private set
    @Volatile var bpm = 110
    @Volatile var swing = 0f              // 0..0.35 - shifts off-beats for groove
    @Volatile var recording = false
        private set

    val pattern = Array(TRACKS) { BooleanArray(STEPS) }
    val micSamples = arrayOfNulls<FloatArray>(MIC_SLOTS)

    /** Active drum kit for tracks 0-5; null means the built-in synth kit. */
    @Volatile var kitTracks: Array<FloatArray>? = null

    /** Current sequencer column for the UI highlight; -1 when stopped. */
    val stepFlow = MutableStateFlow(-1)

    private val recChunks = ArrayList<FloatArray>()

    private var frameClock = 0L
    private var nextStepFrame = 0L
    private var step = 0

    fun trackSample(track: Int): FloatArray? {
        val kit = kitTracks
        return when (track) {
            in 0..5 -> kit?.getOrNull(track) ?: when (track) {
                0 -> Synth.kick
                1 -> Synth.snare
                2 -> Synth.hat
                3 -> Synth.clap
                4 -> Synth.tom
                else -> Synth.bell
            }
            6 -> micSamples[0]
            7 -> micSamples[1]
            else -> null
        }
    }

    fun play(sample: FloatArray?, gain: Float = 1f, delayMs: Int = 0) {
        if (sample == null) return
        val offset = delayMs * SR / 1000
        synchronized(lock) {
            if (voices.size < MAX_VOICES) voices.add(Voice(sample, gain, offset))
        }
    }

    fun togglePlay(): Boolean {
        synchronized(lock) {
            if (playing) {
                playing = false
                stepFlow.value = -1
            } else {
                step = 0
                nextStepFrame = frameClock
                playing = true
            }
        }
        return playing
    }

    fun startRecording() {
        synchronized(recChunks) { recChunks.clear() }
        recording = true
    }

    fun stopRecording(): FloatArray {
        recording = false
        synchronized(recChunks) {
            var n = 0
            for (c in recChunks) n += c.size
            val out = FloatArray(n)
            var p = 0
            for (c in recChunks) {
                c.copyInto(out, p)
                p += c.size
            }
            recChunks.clear()
            return out
        }
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "MegaAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuf, BUF_FRAMES * 4 * 2))
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build()
    }

    private fun loop() {
        val track = buildTrack()
        track.play()
        val mix = FloatArray(BUF_FRAMES)
        while (running) {
            java.util.Arrays.fill(mix, 0f)
            synchronized(lock) {
                if (playing) scheduleSteps()
                var i = 0
                while (i < voices.size) {
                    val v = voices[i]
                    if (v.offset >= BUF_FRAMES) {
                        // Strum/delay: not due yet in this buffer
                        v.offset -= BUF_FRAMES
                        i++
                        continue
                    }
                    val s = v.sample
                    var out = v.offset
                    var p = v.pos
                    while (out < BUF_FRAMES && p < s.size) {
                        mix[out] += s[p] * v.gain
                        out++
                        p++
                    }
                    v.pos = p
                    v.offset = 0
                    if (p >= s.size) voices.removeAt(i) else i++
                }
                frameClock += BUF_FRAMES
            }
            // Soft limiter so stacking 20 sounds gets warm, never harsh
            for (j in mix.indices) mix[j] = tanh(mix[j] * 0.8f)
            if (recording) synchronized(recChunks) { recChunks.add(mix.copyOf()) }
            track.write(mix, 0, BUF_FRAMES, AudioTrack.WRITE_BLOCKING)
        }
        try {
            track.stop()
        } catch (_: IllegalStateException) {
        }
        track.release()
    }

    /** Called with [lock] held: queue every step that falls inside this buffer. */
    private fun scheduleSteps() {
        val stepFrames = 60.0 / bpm / 4.0 * SR
        while (nextStepFrame < frameClock + BUF_FRAMES) {
            val offset = (nextStepFrame - frameClock).coerceAtLeast(0L).toInt()
            for (t in 0 until TRACKS) {
                if (pattern[t][step]) {
                    val s = trackSample(t)
                    if (s != null && voices.size < MAX_VOICES) {
                        voices.add(Voice(s, 1f, offset))
                    }
                }
            }
            stepFlow.value = step
            val dur = if (step % 2 == 0) stepFrames * (1 + swing) else stepFrames * (1 - swing)
            step = (step + 1) % STEPS
            nextStepFrame += dur.roundToLong()
        }
    }
}
