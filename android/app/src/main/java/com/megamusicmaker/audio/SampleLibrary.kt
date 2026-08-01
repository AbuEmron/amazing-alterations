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

class MelodicBank(val id: String, val label: String, val notes: Array<FloatArray>)

class SampleLibrary(private val assets: AssetManager) {

    val loaded = MutableStateFlow(false)

    var kits: List<DrumKit> = emptyList()
        private set
    var melodic: List<MelodicBank> = emptyList()
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
        val piano = notes("piano")
        val glock = notes("glock")
        val marimba = notes("marimba")
        melodic = listOf(
            MelodicBank("piano", "🎹 Piano", piano),
            MelodicBank("glock", "✨ Bells", glock),
            MelodicBank("marimba", "🪵 Marimba", marimba),
        )
        kits = listOf(
            DrumKit(
                "tr808", "🎛 808",
                arrayOf(
                    wav("samples/tr808/kick.wav"),
                    wav("samples/tr808/snare.wav"),
                    wav("samples/tr808/hat.wav"),
                    wav("samples/tr808/clap.wav"),
                    wav("samples/tr808/tom.wav"),
                    wav("samples/tr808/cowbell.wav"),
                ),
                wav("samples/tr808/hatopen.wav"), "🛸", "Tsss",
                wav("samples/tr808/cymbal.wav"), "💥", "Crash",
            ),
            DrumKit(
                "orch", "🎻 Orchestra",
                arrayOf(
                    wav("samples/orch/timplow.wav"),
                    wav("samples/orch/snare.wav"),
                    wav("samples/orch/xylo.wav"),
                    wav("samples/orch/snare2.wav"),
                    wav("samples/orch/timphigh.wav"),
                    wav("samples/orch/glockc6.wav"),
                ),
                wav("samples/orch/roll.wav"), "🌩", "Rumble",
                glock[7], "✨", "Ting",
            ),
        )
    }

    private fun notes(dir: String) = Array(8) { wav("samples/$dir/n$it.wav") }

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
