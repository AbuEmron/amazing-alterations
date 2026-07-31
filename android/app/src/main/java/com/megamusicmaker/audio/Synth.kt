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

    val piano: Array<FloatArray> = Array(pianoFreqs.size) { i ->
        val f = pianoFreqs[i]
        val out = buf(0.8)
        for (n in out.indices) {
            val t = n.toDouble() / SR
            val v = sin(2 * PI * f * t) * expEnv(t, 0.8, 5.0) +
                0.25 * sin(4 * PI * f * t) * expEnv(t, 0.8, 9.0)
            out[n] = v.toFloat()
        }
        normalize(out, 0.6f)
    }
}
