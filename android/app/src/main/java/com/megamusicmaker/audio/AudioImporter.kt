package com.megamusicmaker.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Imports any audio file the phone can play (MP3, WAV, M4A/AAC, OGG, FLAC...)
 * into a sampler buffer: decoded with the device's own MediaCodec, mixed to
 * mono, resampled to the engine rate, trimmed and normalized. Fully offline -
 * the file never leaves the device.
 */
object AudioImporter {

    fun decode(context: Context, uri: Uri, maxSeconds: Double = 60.0): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            val fmt = format ?: return null
            if (trackIndex < 0) return null
            extractor.selectTrack(trackIndex)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            val maxFrames = (maxSeconds * srcRate).toLong()
            val chunks = ArrayList<ShortArray>()
            var totalFrames = 0L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var spins = 0
            while (!outputDone && totalFrames < maxFrames && spins < 10000) {
                spins++
                if (!inputDone) {
                    val inIdx = dec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = dec.getInputBuffer(inIdx) ?: continue
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            dec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = dec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val buf = dec.getOutputBuffer(outIdx)
                        if (buf != null) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val shorts = ShortArray(info.size / 2)
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                            chunks.add(shorts)
                            totalFrames += shorts.size / channels
                        }
                    }
                    dec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            var total = 0
            for (c in chunks) total += c.size
            val monoLen = total / channels
            if (monoLen < 1000) return null
            var mono = FloatArray(monoLen)
            var frame = 0
            var acc = 0f
            var ch = 0
            outer@ for (chunk in chunks) {
                for (s in chunk) {
                    acc += s / 32768f
                    ch++
                    if (ch == channels) {
                        if (frame >= monoLen) break@outer
                        mono[frame++] = acc / channels
                        acc = 0f
                        ch = 0
                    }
                }
            }

            if (srcRate != Synth.SR) {
                val ratio = srcRate.toDouble() / Synth.SR
                val n = (mono.size / ratio).toInt().coerceAtLeast(1)
                val res = FloatArray(n)
                for (i in res.indices) {
                    val pos = i * ratio
                    val i0 = pos.toInt()
                    val fr = (pos - i0).toFloat()
                    val a = if (i0 < mono.size) mono[i0] else 0f
                    val b = if (i0 + 1 < mono.size) mono[i0 + 1] else 0f
                    res[i] = a + (b - a) * fr
                }
                mono = res
            }

            val cap = (maxSeconds * Synth.SR).toInt()
            if (mono.size > cap) mono = mono.copyOf(cap)
            var peak = 1e-4f
            for (x in mono) peak = max(peak, abs(x))
            val g = 0.9f / peak
            for (i in mono.indices) mono[i] *= g
            return mono
        } catch (_: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }
}
