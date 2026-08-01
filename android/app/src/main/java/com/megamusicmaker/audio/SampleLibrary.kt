package com.megamusicmaker.audio

import android.content.res.AssetManager
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Loads the bundled premium sample banks from assets into playable buffers.
 *
 * The banks (see CREDITS.md at the repo root):
 *  - TR-808: Michael Fischer's 1994 recordings of a real TR-808 (CC0)
 *  - Grand piano: Salamander Grand Piano, a real Yamaha C5 (CC-BY 3.0, Alexander Holm)
 *  - Glockenspiel / marimba / orchestra percussion: VSCO 2 Community Edition (CC0)
 *
 * Everything ships inside the APK - nothing is ever downloaded.
 */
class DrumKit(
    val id: String,
    val label: String,
    /** kick, snare, hat, clap, tom, bell - same order as the sequencer rows. */
    val tracks: Array<FloatArray>,
    val bonus1: FloatArray, val bonus1Emoji: String, val bonus1Label: String,
    val bonus2: FloatArray, val bonus2Emoji: String, val bonus2Label: String,
)

class MelodicBank(
    val id: String,
    val label: String,
    val notes: Array<FloatArray>,
    /** One octave C4..B4 (degrees 0..6) for Magic Chords accompaniment. */
    val chordNotes: Array<FloatArray>,
)

class SampleLibrary(private val assets: AssetManager) {

    val loaded = MutableStateFlow(false)

    var kits: List<DrumKit> = emptyList()
        private set
    var melodic: List<MelodicBank> = emptyList()
        private set

    /** Salamander anchors across the 88-key range: sorted (midi, sample). */
    var pianoAnchors: List<Pair<Int, FloatArray>> = emptyList()
        private set

    fun loadAsync() {
        Thread({
            try {
                load()
            } catch (_: Exception) {
                // Missing/corrupt assets: the synth engine still covers everything
            }
            loaded.value = true
        }, "SampleLoader").start()
    }

    private fun load() {
        loadPianoAnchors()
        val piano = notes("piano")
        val glock = notes("glock")
        val marimba = notes("marimba")
        melodic = listOf(
            MelodicBank("piano", "Piano", piano, chordOctave(piano[0])),
            MelodicBank("glock", "Bells", glock, chordOctave(glock[0])),
            MelodicBank("marimba", "Marimba", marimba, chordOctave(marimba[0])),
        )
        kits = listOf(
            DrumKit(
                "tr808", "808",
                arrayOf(
                    wav("samples/tr808/kick.wav"),
                    wav("samples/tr808/snare.wav"),
                    wav("samples/tr808/hat.wav"),
                    wav("samples/tr808/clap.wav"),
                    wav("samples/tr808/tom.wav"),
                    wav("samples/tr808/cowbell.wav"),
                ),
                wav("samples/tr808/hatopen.wav"), "🎩", "Open Hat",
                wav("samples/tr808/cymbal.wav"), "💥", "Cymbal",
            ),
            DrumKit(
                "orch", "Orchestra",
                arrayOf(
                    wav("samples/orch/timplow.wav"),
                    wav("samples/orch/snare.wav"),
                    wav("samples/orch/xylo.wav"),
                    wav("samples/orch/snare2.wav"),
                    wav("samples/orch/timphigh.wav"),
                    wav("samples/orch/glockc6.wav"),
                ),
                wav("samples/orch/roll.wav"), "🌩", "Roll",
                glock[7], "✨", "Glock",
            ),
        )
    }

    private fun notes(dir: String) = Array(8) { wav("samples/$dir/n$it.wav") }

    private val anchorMidis = intArrayOf(21, 24, 30, 36, 42, 48, 54, 60, 66, 72, 78, 84, 90, 96, 102, 108)

    private fun loadPianoAnchors() {
        val loaded = ArrayList<Pair<Int, FloatArray>>()
        for (midi in anchorMidis) {
            val s = try {
                wav("samples/piano88/m$midi.wav")
            } catch (_: Exception) {
                FloatArray(0)
            }
            if (s.isNotEmpty()) loaded.add(midi to s)
        }
        pianoAnchors = loaded
    }

    companion object {
        /** Nearest-anchor lookup: returns the sample and varispeed rate for [midi]. */
        fun noteFor(anchors: List<Pair<Int, FloatArray>>, midi: Int): Pair<FloatArray, Float>? {
            if (anchors.isEmpty()) return null
            val (aMidi, sample) = anchors.minByOrNull { kotlin.math.abs(it.first - midi) }!!
            val rate = Math.pow(2.0, (midi - aMidi) / 12.0).toFloat()
            return sample to rate
        }
    }

    /** Semitone offsets from the bank's C5 sample down to C4..B4. */
    private val chordSemis = intArrayOf(-12, -10, -8, -7, -5, -3, -1)

    private fun chordOctave(c5: FloatArray) = Array(7) { resample(c5, chordSemis[it]) }

    /** Varispeed pitch shift by linear interpolation - fine for a chord bed. */
    private fun resample(src: FloatArray, semis: Int): FloatArray {
        val ratio = Math.pow(2.0, semis / 12.0)
        val n = (src.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        for (i in out.indices) {
            val pos = i * ratio
            val i0 = pos.toInt()
            val frac = (pos - i0).toFloat()
            val a = if (i0 < src.size) src[i0] else 0f
            val b = if (i0 + 1 < src.size) src[i0 + 1] else 0f
            out[i] = a + (b - a) * frac
        }
        return out
    }

    /** Minimal RIFF parser for our own bundled files (44.1 kHz mono 16-bit PCM). */
    private fun wav(path: String): FloatArray {
        val bytes = assets.open(path).use { it.readBytes() }
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = (bytes[pos + 4].toInt() and 0xFF) or
                ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 7].toInt() and 0xFF) shl 24)
            if (id == "data") {
                val n = (size / 2).coerceAtMost((bytes.size - pos - 8) / 2)
                val out = FloatArray(n)
                var b = pos + 8
                for (i in 0 until n) {
                    val lo = bytes[b].toInt() and 0xFF
                    val hi = bytes[b + 1].toInt()
                    out[i] = ((hi shl 8) or lo) / 32768f
                    b += 2
                }
                return out
            }
            pos += 8 + size + (size and 1)
        }
        return FloatArray(0)
    }
}
