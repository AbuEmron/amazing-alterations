package com.megamusicmaker.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Every sound in the app is synthesized right here, at install time, from pure math.
 * No sample packs, no downloads, no licenses - the whole drum kit is ~1 MB of floats.
 */
object Synth {

    const val SR = 44100

    /* ---------- building blocks ---------- */

    private val rnd = java.util.Random(42)

    private fun buf(seconds: Double) = FloatArray((seconds * SR).toInt())

    private fun expEnv(t: Double, dur: Double, k: Double) = exp(-k * t / dur)

    private fun normalize(a: FloatArray, peak: Float = 0.9f): FloatArray {
        var m = 1e-6f
        for (x in a) m = max(m, abs(x))
        val g = peak / m
        for (i in a.indices) a[i] *= g
        return a
    }

    private fun saw(phase: Double): Double {
        val p = phase / (2 * PI)
        return (p - floor(p)) * 2 - 1
    }

    private fun square(phase: Double): Double = if (sin(phase) >= 0) 1.0 else -1.0

    /** Pitch-swept oscillator with phase accumulation (no clicks). */
    private fun sweep(
        dur: Double,
        f0: Double,
        f1: Double,
        k: Double = 5.0,
        wave: (Double) -> Double = ::sin,
    ): FloatArray {
        val out = buf(dur)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            val prog = min(t / (dur * 0.8), 1.0)
            val f = f0 * (f1 / f0).pow(prog)
            phase += 2 * PI * f / SR
            out[i] = (wave(phase) * expEnv(t, dur, k)).toFloat()
        }
        return normalize(out)
    }

    /** Oscillator whose frequency follows an arbitrary curve (for boings and whistles). */
    private fun freqPath(
        dur: Double,
        k: Double,
        freqAt: (Double) -> Double,
    ): FloatArray {
        val out = buf(dur)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            phase += 2 * PI * freqAt(t) / SR
            out[i] = (sin(phase) * expEnv(t, dur, k)).toFloat()
        }
        return normalize(out)
    }

    /** RBJ biquad filter - enough DSP to make noise sound like real drums. */
    private class Biquad(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double,
    ) {
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }

        companion object {
            fun bandpass(f: Double, q: Double): Biquad {
                val w0 = 2 * PI * f / SR
                val alpha = sin(w0) / (2 * q)
                val a0 = 1 + alpha
                return Biquad(alpha / a0, 0.0, -alpha / a0, -2 * cos(w0) / a0, (1 - alpha) / a0)
            }

            fun highpass(f: Double, q: Double = 0.707): Biquad {
                val w0 = 2 * PI * f / SR
                val alpha = sin(w0) / (2 * q)
                val c = cos(w0)
                val a0 = 1 + alpha
                return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
            }
        }
    }

    private fun filteredNoise(dur: Double, filter: Biquad, k: Double): FloatArray {
        val out = buf(dur)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            out[i] = (filter.process(rnd.nextDouble() * 2 - 1) * expEnv(t, dur, k)).toFloat()
        }
        return normalize(out)
    }

    private fun layer(vararg parts: Pair<FloatArray, Float>): FloatArray {
        var n = 0
        for ((arr, _) in parts) n = max(n, arr.size)
        val out = FloatArray(n)
        for ((arr, gain) in parts) for (i in arr.indices) out[i] += arr[i] * gain
        return normalize(out)
    }

    private fun addAt(dst: FloatArray, src: FloatArray, offset: Int, gain: Float) {
        var i = 0
        while (i < src.size && offset + i < dst.size) {
            dst[offset + i] += src[i] * gain
            i++
        }
    }

    /* ---------- the drum kit ---------- */

    val kick: FloatArray = sweep(0.45, 150.0, 40.0, 5.0)

    val snare: FloatArray = layer(
        filteredNoise(0.2, Biquad.bandpass(1800.0, 1.0), 6.0) to 0.8f,
        sweep(0.12, 200.0, 150.0, 6.0) to 0.5f,
    )

    val hat: FloatArray = filteredNoise(0.06, Biquad.highpass(7000.0), 6.0)

    val clap: FloatArray = run {
        val burst = filteredNoise(0.15, Biquad.bandpass(1200.0, 1.2), 7.0)
        val out = buf(0.2)
        addAt(out, burst, 0, 0.7f)
        addAt(out, burst, (0.02 * SR).toInt(), 0.8f)
        addAt(out, burst, (0.04 * SR).toInt(), 1.0f)
        normalize(out)
    }

    val tom: FloatArray = sweep(0.3, 220.0, 80.0, 5.0)

    val bell: FloatArray = run {
        val out = buf(0.3)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            val v = square(2 * PI * 540.0 * t) * 0.5 + square(2 * PI * 810.0 * t) * 0.35
            out[i] = (v * expEnv(t, 0.3, 6.0)).toFloat()
        }
        normalize(out, 0.7f)
    }

    val zap: FloatArray = sweep(0.22, 1400.0, 90.0, 4.0, ::saw)

    val boing: FloatArray = freqPath(0.4, 4.0) { t ->
        if (t < 0.15) 130.0 + (420.0 - 130.0) * (t / 0.15)
        else 420.0 - (420.0 - 150.0) * ((t - 0.15) / 0.25)
    }

    val pop: FloatArray = sweep(0.09, 500.0, 100.0, 6.0)

    val whistle: FloatArray = freqPath(0.35, 3.0) { t ->
        if (t < 0.15) 900.0 + (1500.0 - 900.0) * (t / 0.15)
        else 1500.0 - (1500.0 - 900.0) * ((t - 0.15) / 0.2)
    }

    val shaker: FloatArray = filteredNoise(0.09, Biquad.highpass(5000.0), 5.0)

    val robot: FloatArray = run {
        val out = buf(0.35)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            val v = square(2 * PI * 110.0 * t) * (0.55 + 0.45 * sin(2 * PI * 28.0 * t))
            out[i] = (v * expEnv(t, 0.35, 4.0)).toFloat()
        }
        normalize(out, 0.7f)
    }

    /* ---------- rainbow piano (C major pentatonic - no wrong notes) ---------- */

    private val pianoFreqs = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.7, 1318.5)

    private fun melodicNote(f: Double, dur: Double): FloatArray {
        val out = buf(dur)
        for (n in out.indices) {
            val t = n.toDouble() / SR
            val v = sin(2 * PI * f * t) * expEnv(t, dur, 5.0) +
                0.25 * sin(4 * PI * f * t) * expEnv(t, dur, 9.0)
            out[n] = v.toFloat()
        }
        return normalize(out, 0.6f)
    }

    val piano: Array<FloatArray> = Array(pianoFreqs.size) { melodicNote(pianoFreqs[it], 0.8) }

    /** One octave C4..B4 for Magic Chords accompaniment. */
    private val chordFreqs = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.0, 440.0, 493.88)

    val chordNotes: Array<FloatArray> = Array(chordFreqs.size) { melodicNote(chordFreqs[it], 1.1) }

    /* ---------- sub bass (808-style: pitch-drop attack, saturated sine) ---------- */

    private fun subBassNote(f: Double): FloatArray {
        val dur = 1.6
        val out = buf(dur)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            // starts an octave up and drops fast for that 808 knock
            val freq = f * (1.0 + exp(-t / 0.02))
            phase += 2 * PI * freq / SR
            val body = kotlin.math.tanh(1.6 * sin(phase))
            out[i] = (body * expEnv(t, dur, 3.5)).toFloat()
        }
        return normalize(out, 0.85f)
    }

    /** Sub bass anchors at C1 / C2 / C3 (MIDI 24 / 36 / 48) for rate-pitching. */
    val subAnchors: List<Pair<Int, FloatArray>> = listOf(
        24 to subBassNote(32.70),
        36 to subBassNote(65.41),
        48 to subBassNote(130.81),
    )

    /** Short metronome click. */
    val click: FloatArray = run {
        val out = buf(0.03)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            out[i] = (sin(2 * PI * 2000.0 * t) * expEnv(t, 0.03, 8.0)).toFloat()
        }
        normalize(out, 0.8f)
    }

    /* ---------- Bass Station: six bass instruments, all pure synthesis ---------- */

    /** Clean sine sub with a soft attack - the foundation. */
    private fun pureSub(f: Double): FloatArray {
        val dur = 1.5
        val out = buf(dur)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            phase += 2 * PI * f / SR
            val attack = min(t / 0.005, 1.0)
            out[i] = (sin(phase) * attack * expEnv(t, dur, 2.5)).toFloat()
        }
        return normalize(out, 0.85f)
    }

    /** Reese: two detuned saws + sub sine, low-passed dark. DnB staple. */
    private fun reese(f: Double): FloatArray {
        val dur = 1.6
        val out = buf(dur)
        var p1 = 0.0; var p2 = 0.0; var ps = 0.0
        var lp = 0.0
        val lpCoef = 2 * PI * 500.0 / SR
        for (i in out.indices) {
            val t = i.toDouble() / SR
            p1 += 2 * PI * f * 1.006 / SR
            p2 += 2 * PI * f * 0.994 / SR
            ps += 2 * PI * f / SR
            val raw = saw(p1) * 0.5 + saw(p2) * 0.5 + sin(ps) * 0.4
            lp += lpCoef * (raw - lp)
            val attack = min(t / 0.01, 1.0)
            out[i] = (lp * attack * expEnv(t, dur, 2.0)).toFloat()
        }
        return normalize(out, 0.8f)
    }

    /** Square bass with a closing filter sweep - funk machine. */
    private fun squareBass(f: Double): FloatArray {
        val dur = 1.2
        val out = buf(dur)
        var phase = 0.0
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            phase += 2 * PI * f / SR
            val cutoff = 250.0 + 1750.0 * exp(-t / 0.15)
            val lpCoef = 2 * PI * cutoff / SR
            lp += lpCoef * (square(phase) - lp)
            out[i] = (lp * expEnv(t, dur, 4.0)).toFloat()
        }
        return normalize(out, 0.85f)
    }

    /** Karplus-Strong plucked string - fingered electric bass feel. */
    private fun pluckBass(f: Double): FloatArray {
        val dur = 1.5
        val out = buf(dur)
        val n = (SR / f).toInt().coerceAtLeast(2)
        val delay = DoubleArray(n)
        for (i in delay.indices) delay[i] = rnd.nextDouble() * 2 - 1
        var idx = 0
        var lp = 0.0
        val lpCoef = 2 * PI * 1500.0 / SR
        for (i in out.indices) {
            val cur = delay[idx]
            val next = delay[(idx + 1) % n]
            delay[idx] = 0.996 * 0.5 * (cur + next)
            idx = (idx + 1) % n
            lp += lpCoef * (cur - lp)
            out[i] = lp.toFloat()
        }
        return normalize(out, 0.85f)
    }

    /** FM growl: modulation index sweep + saturation - modern bass music. */
    private fun growlBass(f: Double): FloatArray {
        val dur = 1.4
        val out = buf(dur)
        var cp = 0.0; var mp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            cp += 2 * PI * f / SR
            mp += 2 * PI * 2 * f / SR
            val index = 0.3 + 3.7 * exp(-t / 0.25)
            val v = kotlin.math.tanh(1.3 * sin(cp + index * sin(mp)))
            out[i] = (v * expEnv(t, dur, 3.0)).toFloat()
        }
        return normalize(out, 0.8f)
    }

    private val bassFreqs = listOf(24 to 32.70, 36 to 65.41, 48 to 130.81)

    private fun bank(gen: (Double) -> FloatArray): List<Pair<Int, FloatArray>> =
        bassFreqs.map { (midi, f) -> midi to gen(f) }

    /** All bass instruments: id -> (label, anchors at C1/C2/C3 for rate-pitching). */
    val bassBanks: LinkedHashMap<String, Pair<String, List<Pair<Int, FloatArray>>>> = linkedMapOf(
        "808" to ("808" to subAnchors),
        "sub" to ("Sub" to bank(::pureSub)),
        "reese" to ("Reese" to bank(::reese)),
        "square" to ("Square" to bank(::squareBass)),
        "pluck" to ("Pluck" to bank(::pluckBass)),
        "growl" to ("Growl" to bank(::growlBass)),
    )
}
