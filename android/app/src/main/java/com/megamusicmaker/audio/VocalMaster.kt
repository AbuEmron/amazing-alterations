package com.megamusicmaker.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Automatic vocal mastering: every Voice Booth take is processed offline
 * through a studio vocal chain the moment recording stops -
 *
 *   80 Hz high-pass  -> removes handling noise and room rumble
 *   soft noise gate  -> silences breaths and background between phrases
 *   4:1 compressor   -> evens out loud and quiet words
 *   presence EQ      -> +3 dB around 3.5 kHz for clarity and air
 *   loudness         -> normalized and soft-limited to -0.5 dBFS
 *
 * plus silence trimming at both ends. Pure Kotlin, fully offline.
 */
object VocalMaster {

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
            fun highpass(f: Double, sr: Int, q: Double = 0.707): Biquad {
                val w0 = 2 * Math.PI * f / sr
                val alpha = sin(w0) / (2 * q)
                val c = cos(w0)
                val a0 = 1 + alpha
                return Biquad(
                    (1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0,
                    -2 * c / a0, (1 - alpha) / a0,
                )
            }

            fun peaking(f: Double, sr: Int, q: Double, dbGain: Double): Biquad {
                val amp = Math.pow(10.0, dbGain / 40)
                val w0 = 2 * Math.PI * f / sr
                val alpha = sin(w0) / (2 * q)
                val c = cos(w0)
                val a0 = 1 + alpha / amp
                return Biquad(
                    (1 + alpha * amp) / a0, -2 * c / a0, (1 - alpha * amp) / a0,
                    -2 * c / a0, (1 - alpha / amp) / a0,
                )
            }
        }
    }

    fun process(raw: FloatArray, sr: Int = Synth.SR): FloatArray {
        if (raw.size < sr / 10) return raw

        // filters: rumble cut + presence lift
        val hp = Biquad.highpass(80.0, sr)
        val presence = Biquad.peaking(3500.0, sr, 0.8, 3.0)
        val out = FloatArray(raw.size)
        for (i in raw.indices) {
            out[i] = presence.process(hp.process(raw[i].toDouble())).toFloat()
        }

        // envelope-follower gate + compressor
        val attackCoef = exp(-1.0 / (0.003 * sr)).toFloat()
        val releaseCoef = exp(-1.0 / (0.1 * sr)).toFloat()
        var env = 0f
        var gateGain = 1f
        val gateThresh = 0.02f
        val compThresh = 0.25f
        for (i in out.indices) {
            val level = abs(out[i])
            env = if (level > env) {
                attackCoef * env + (1 - attackCoef) * level
            } else {
                releaseCoef * env + (1 - releaseCoef) * level
            }
            val gateTarget = if (env > gateThresh) 1f else 0.12f
            gateGain += (gateTarget - gateGain) * 0.0008f
            var y = out[i] * gateGain
            if (env > compThresh) {
                y *= (compThresh + (env - compThresh) / 4f) / env
            }
            out[i] = y
        }

        // trim silence from both ends (keep a little air)
        var peak = 1e-4f
        for (x in out) peak = max(peak, abs(x))
        val th = peak * 0.02f
        var start = 0
        while (start < out.size && abs(out[start]) < th) start++
        var end = out.size
        while (end > start && abs(out[end - 1]) < th) end--
        start = max(0, start - sr / 20)
        end = min(out.size, end + sr / 4)
        if (end - start < sr / 10) return FloatArray(0)
        val trimmed = out.copyOfRange(start, end)

        // normalize + soft limit to -0.5 dBFS
        var p = 1e-4f
        for (x in trimmed) p = max(p, abs(x))
        val gain = 0.95f / p
        for (i in trimmed.indices) {
            trimmed[i] = tanh(trimmed[i] * gain * 1.15f) * 0.94f
        }
        return trimmed
    }
}
