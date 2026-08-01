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
        const val PATTERNS = 4     // A / B / C / D, chainable into a song
        private const val MAX_VOICES = 64
        private const val BUF_FRAMES = 512
    }

    private class Voice(
        val sample: FloatArray,
        val gain: Float,
        var offset: Int,
        val rate: Float,
        val choke: Boolean = false,
    ) {
        var pos = 0.0
        var fading = false
        var fadeRamp = 1f
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

    /** Four patterns (A-D), each TRACKS x STEPS; [currentPattern] selects the live one. */
    val patterns = Array(PATTERNS) { Array(TRACKS) { BooleanArray(STEPS) } }
    val accents = Array(PATTERNS) { Array(TRACKS) { BooleanArray(STEPS) } }
    @Volatile var currentPattern = 0
    @Volatile var chain = false        // song mode: cycle non-empty patterns A->B->C->D

    /** The active pattern/accents - all UI editing goes through these. */
    val pattern: Array<BooleanArray> get() = patterns[currentPattern]
    val accent: Array<BooleanArray> get() = accents[currentPattern]

    /** Bass Station: per-pattern monophonic bassline (MIDI note per step, -1 = off). */
    val basslines = Array(PATTERNS) { IntArray(STEPS) { -1 } }
    val bassline: IntArray get() = basslines[currentPattern]
    @Volatile var bassBank = "808"
    @Volatile var bassGain = 1f

    /* per-track mixer */
    val trackGain = FloatArray(TRACKS) { 1f }
    val trackMute = BooleanArray(TRACKS)

    @Volatile var metronome = false
    @Volatile var reverbMix = 0f       // 0..1 master reverb send
    @Volatile var delayMix = 0f        // 0..1 master delay send (tempo-synced dotted 8th)

    /**
     * Knock: psychoacoustic sub-harmonic enhancer. Phone speakers can't play
     * 50 Hz, so we synthesize the sub band's harmonics up where they CAN be
     * heard - the brain reconstructs the missing fundamental (MaxxBass-style).
     */
    @Volatile var bassEnhance = 0.45f  // 0..1

    val micSamples = arrayOfNulls<FloatArray>(MIC_SLOTS)

    /** Active drum kit for tracks 0-5; null means the built-in synth kit. */
    @Volatile var kitTracks: Array<FloatArray>? = null

    /** Current sequencer column for the UI highlight; -1 when stopped. */
    val stepFlow = MutableStateFlow(-1)

    /** Pattern currently playing (changes while chaining). */
    val patternFlow = MutableStateFlow(0)

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

    /** [rate] varispeed-pitches the sample: 2^(semitones/12). 1.0 = original pitch. */
    fun play(sample: FloatArray?, gain: Float = 1f, delayMs: Int = 0, rate: Float = 1f) {
        if (sample == null) return
        val offset = delayMs * SR / 1000
        synchronized(lock) {
            if (voices.size < MAX_VOICES) voices.add(Voice(sample, gain, offset, rate))
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
                    val r = v.rate.toDouble()
                    while (out < BUF_FRAMES && p < s.size - 1) {
                        val i0 = p.toInt()
                        val frac = (p - i0).toFloat()
                        var g = v.gain
                        if (v.fading) {
                            v.fadeRamp -= 0.004f    // ~6 ms choke fade, no clicks
                            if (v.fadeRamp <= 0f) break
                            g *= v.fadeRamp
                        }
                        mix[out] += (s[i0] + (s[i0 + 1] - s[i0]) * frac) * g
                        out++
                        p += r
                    }
                    v.pos = p
                    v.offset = 0
                    if (p >= s.size - 1 || (v.fading && v.fadeRamp <= 0f)) {
                        voices.removeAt(i)
                    } else i++
                }
                frameClock += BUF_FRAMES
            }
            processFx(mix)
            master(mix)
            if (recording) synchronized(recChunks) { recChunks.add(mix.copyOf()) }
            track.write(mix, 0, BUF_FRAMES, AudioTrack.WRITE_BLOCKING)
        }
        try {
            track.stop()
        } catch (_: IllegalStateException) {
        }
        track.release()
    }

    /* ---- automatic mastering chain: everything you hear (and record) passes
       through a rumble filter, program compressor, makeup gain, and soft
       limiter - so every beat comes out loud, glued, and clip-free. ---- */

    private var hpState = 0f
    private var compEnv = 0f
    private var subLp = 0f
    private var harmLp = 0f
    private var harmHp = 0f
    private val hpCoef = (2.0 * Math.PI * 18.0 / SR).toFloat()          // ~18 Hz rumble cut (below 808 territory)
    private val subLpCoef = (2.0 * Math.PI * 120.0 / SR).toFloat()      // sub-band isolation
    private val harmLpCoef = (2.0 * Math.PI * 900.0 / SR).toFloat()     // tame fizz above 900 Hz
    private val harmHpCoef = (2.0 * Math.PI * 90.0 / SR).toFloat()      // keep harmonics out of the mud
    private val attackCoef = Math.exp(-1.0 / (0.010 * SR)).toFloat()    // 10 ms attack - lets transients punch
    private val releaseCoef = Math.exp(-1.0 / (0.25 * SR)).toFloat()    // 250 ms release - no pumping on long 808 decays

    private fun master(mix: FloatArray) {
        val threshold = 0.6f
        val invRatio = 1f / 2.5f
        val makeup = 1.15f
        val knee = 0.85f
        val knock = bassEnhance
        for (j in mix.indices) {
            var x = mix[j]
            // one-pole high-pass removes sub-sonic rumble before compression
            hpState += hpCoef * (x - hpState)
            x -= hpState
            // Knock: rectify + saturate the sub band to synthesize audible
            // harmonics of the fundamental, band-limited to ~90-700 Hz
            if (knock > 0.01f) {
                subLp += subLpCoef * (x - subLp)
                val rect = if (subLp >= 0) subLp else -subLp        // 2nd harmonic
                val asym = rect - 0.35f * subLp                     // push energy higher
                val rect2 = if (asym >= 0) asym else -asym          // 4th harmonic
                val odd = tanh(subLp * 6f)                          // odd series
                harmLp += harmLpCoef * ((rect * 1.4f + rect2 * 0.8f + odd * 0.8f) - harmLp)
                harmHp += harmHpCoef * (harmLp - harmHp)
                x += (harmLp - harmHp) * knock * 2.4f
            }
            // gentle program compressor with envelope follower
            val level = if (x >= 0) x else -x
            compEnv = if (level > compEnv) {
                attackCoef * compEnv + (1 - attackCoef) * level
            } else {
                releaseCoef * compEnv + (1 - releaseCoef) * level
            }
            var y = x
            if (compEnv > threshold) {
                y *= (threshold + (compEnv - threshold) * invRatio) / compEnv
            }
            y *= makeup
            // transparent limiter: PERFECTLY LINEAR below the knee, so clean
            // low sines (808 kick, sub bass) keep their character; only true
            // peaks get soft-saturated
            val a = if (y >= 0) y else -y
            if (a > knee) {
                val soft = knee + (1f - knee) * tanh((a - knee) / (1f - knee))
                y = if (y >= 0) soft else -soft
            }
            mix[j] = y
        }
    }

    /** Called with [lock] held: queue every step that falls inside this buffer. */
    private fun scheduleSteps() {
        val stepFrames = 60.0 / bpm / 4.0 * SR
        while (nextStepFrame < frameClock + BUF_FRAMES) {
            val offset = (nextStepFrame - frameClock).coerceAtLeast(0L).toInt()
            val pat = patterns[currentPattern]
            val acc = accents[currentPattern]
            for (t in 0 until TRACKS) {
                if (pat[t][step] && !trackMute[t]) {
                    val s = trackSample(t)
                    if (s != null && voices.size < MAX_VOICES) {
                        val g = (if (acc[t][step]) 1.25f else 0.9f) * trackGain[t]
                        voices.add(Voice(s, g, offset, 1f))
                    }
                }
            }
            val bassNote = basslines[currentPattern][step]
            if (bassNote >= 0 && voices.size < MAX_VOICES) {
                val anchors = Synth.bassBanks[bassBank]?.second ?: Synth.subAnchors
                SampleLibrary.noteFor(anchors, bassNote)?.let { (s, r) ->
                    // monophonic: choke the previous bass note
                    for (v in voices) if (v.choke) v.fading = true
                    voices.add(Voice(s, bassGain, offset, r, choke = true))
                }
            }
            if (metronome && step % 4 == 0 && voices.size < MAX_VOICES) {
                voices.add(Voice(Synth.click, if (step == 0) 0.5f else 0.28f, offset, 1f))
            }
            stepFlow.value = step
            val dur = if (step % 2 == 0) stepFrames * (1 + swing) else stepFrames * (1 - swing)
            step = (step + 1) % STEPS
            if (step == 0 && chain) {
                advancePattern()
                patternFlow.value = currentPattern
            }
            nextStepFrame += dur.roundToLong()
        }
    }

    /** Song mode: move to the next pattern that has any steps in it. */
    private fun advancePattern() {
        for (k in 1..PATTERNS) {
            val cand = (currentPattern + k) % PATTERNS
            if (patterns[cand].any { row -> row.any { it } }) {
                currentPattern = cand
                return
            }
        }
    }

    /* ---- master FX: tempo-synced dotted-8th delay + Schroeder reverb ---- */

    private val delayBuf = FloatArray(SR * 2)
    private var delayPos = 0
    private val combBufs = arrayOf(
        FloatArray(1116), FloatArray(1188), FloatArray(1277), FloatArray(1356)
    )
    private val combPos = IntArray(4)
    private val combFb = floatArrayOf(0.805f, 0.795f, 0.783f, 0.769f)
    private val apBufs = arrayOf(FloatArray(556), FloatArray(441))
    private val apPos = IntArray(2)

    private fun processFx(mix: FloatArray) {
        val dMix = delayMix
        val rMix = reverbMix
        if (dMix <= 0.001f && rMix <= 0.001f) return
        val dLen = (3.0 * 60.0 / bpm / 4.0 * SR).toInt().coerceIn(1024, delayBuf.size - 1)
        for (j in mix.indices) {
            val dry = mix[j]
            // delay
            var wet = 0f
            if (dMix > 0.001f) {
                val readPos = (delayPos - dLen + delayBuf.size) % delayBuf.size
                val dOut = delayBuf[readPos]
                delayBuf[delayPos] = dry + dOut * 0.4f
                delayPos = (delayPos + 1) % delayBuf.size
                wet += dOut * dMix * 0.7f
            }
            // reverb
            if (rMix > 0.001f) {
                val input = dry * 0.35f
                var rev = 0f
                for (c in 0 until 4) {
                    val buf = combBufs[c]
                    val p = combPos[c]
                    val yc = buf[p]
                    buf[p] = input + yc * combFb[c]
                    combPos[c] = (p + 1) % buf.size
                    rev += yc
                }
                rev *= 0.25f
                for (a in 0 until 2) {
                    val buf = apBufs[a]
                    val p = apPos[a]
                    val bufOut = buf[p]
                    buf[p] = rev + bufOut * 0.5f
                    apPos[a] = (p + 1) % buf.size
                    rev = bufOut - rev * 0.5f
                }
                wet += rev * rMix
            }
            mix[j] = dry + wet
        }
    }
}
