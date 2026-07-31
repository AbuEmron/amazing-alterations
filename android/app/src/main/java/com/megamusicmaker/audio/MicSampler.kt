package com.megamusicmaker.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Records short clips from the microphone and turns them into playable
 * samples. Audio never leaves the device - it lives only in this app's memory
 * (and the songs you explicitly save).
 */
class MicSampler {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val chunks = ArrayList<ShortArray>()

    @Volatile private var capturing = false

    val isCapturing: Boolean get() = capturing

    /** Starts capturing. Caller must hold RECORD_AUDIO permission. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (capturing) return true
        val minBuf = AudioRecord.getMinBufferSize(
            Synth.SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                Synth.SR,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, 8192)
            )
        } catch (_: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        synchronized(chunks) { chunks.clear() }
        record = r
        capturing = true
        r.startRecording()
        thread = Thread {
            val buf = ShortArray(2048)
            while (capturing) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) synchronized(chunks) { chunks.add(buf.copyOf(n)) }
            }
        }.apply { start() }
        return true
    }

    /** Stops capturing and returns the clip, or null if it was too short. */
    fun stop(maxSeconds: Double = 5.0): FloatArray? {
        if (!capturing) return null
        capturing = false
        thread?.join(500)
        thread = null
        record?.let { r ->
            try {
                r.stop()
            } catch (_: IllegalStateException) {
            }
            r.release()
        }
        record = null

        var total = 0
        val parts: List<ShortArray>
        synchronized(chunks) {
            parts = ArrayList(chunks)
            chunks.clear()
        }
        for (c in parts) total += c.size
        if (total < Synth.SR / 20) return null   // under 50 ms: ignore

        total = min(total, (maxSeconds * Synth.SR).toInt())
        val out = FloatArray(total)
        var p = 0
        outer@ for (c in parts) {
            for (s in c) {
                if (p >= total) break@outer
                out[p++] = s / 32768f
            }
        }

        // Normalize so quiet voices still slap
        var peak = 1e-4f
        for (x in out) peak = max(peak, abs(x))
        val g = 0.9f / peak
        for (i in out.indices) out[i] *= g
        return out
    }
}
