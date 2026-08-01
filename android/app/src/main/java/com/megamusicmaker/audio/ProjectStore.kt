package com.megamusicmaker.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Project persistence - like any real DAW, the studio never loses work.
 * The whole session (all four patterns, accents, mixer, FX, tempo, kit and
 * instrument selection, plus the S1-S4 samples) is saved to app-private
 * storage when the app goes to background and restored on launch.
 */
object ProjectStore {

    /** UI selections mirrored here so they persist alongside engine state. */
    @Volatile var kitId = "boombap"
    @Volatile var melodicId = "piano"

    private fun packPattern(p: Array<BooleanArray>): JSONArray {
        val rows = JSONArray()
        for (row in p) {
            val sb = StringBuilder()
            for (v in row) sb.append(if (v) '1' else '0')
            rows.put(sb.toString())
        }
        return rows
    }

    private fun unpackPattern(rows: JSONArray, into: Array<BooleanArray>) {
        for (t in 0 until minOf(rows.length(), into.size)) {
            val s = rows.getString(t)
            for (i in 0 until minOf(s.length, into[t].size)) into[t][i] = s[i] == '1'
        }
    }

    fun save(context: Context, engine: AudioEngine) {
        try {
            val o = JSONObject()
            o.put("bpm", engine.bpm)
            o.put("swing", engine.swing.toDouble())
            o.put("currentPattern", engine.currentPattern)
            o.put("chain", engine.chain)
            o.put("metronome", engine.metronome)
            o.put("reverb", engine.reverbMix.toDouble())
            o.put("delay", engine.delayMix.toDouble())
            o.put("knock", engine.bassEnhance.toDouble())
            o.put("kit", kitId)
            o.put("melodic", melodicId)
            val gains = JSONArray()
            for (g in engine.trackGain) gains.put(g.toDouble())
            o.put("gains", gains)
            val mutes = JSONArray()
            for (m in engine.trackMute) mutes.put(m)
            o.put("mutes", mutes)
            val pats = JSONArray()
            for (p in engine.patterns) pats.put(packPattern(p))
            o.put("patterns", pats)
            val accs = JSONArray()
            for (a in engine.accents) accs.put(packPattern(a))
            o.put("accents", accs)
            val basses = JSONArray()
            for (b in engine.basslines) {
                val row = JSONArray()
                for (n in b) row.put(n)
                basses.put(row)
            }
            o.put("basslines", basses)
            o.put("bassBank", engine.bassBank)
            o.put("bassGain", engine.bassGain.toDouble())
            File(context.filesDir, "project.json").writeText(o.toString())

            for (i in 0 until AudioEngine.MIC_SLOTS) {
                val f = File(context.filesDir, "sample$i.wav")
                val s = engine.micSamples[i]
                if (s != null) WavWriter.writeFile(f, s) else f.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun load(context: Context, engine: AudioEngine) {
        try {
            val f = File(context.filesDir, "project.json")
            if (f.exists()) {
                val o = JSONObject(f.readText())
                engine.bpm = o.optInt("bpm", 110)
                engine.swing = o.optDouble("swing", 0.0).toFloat()
                engine.currentPattern = o.optInt("currentPattern", 0)
                    .coerceIn(0, AudioEngine.PATTERNS - 1)
                engine.chain = o.optBoolean("chain", false)
                engine.metronome = o.optBoolean("metronome", false)
                engine.reverbMix = o.optDouble("reverb", 0.0).toFloat()
                engine.delayMix = o.optDouble("delay", 0.0).toFloat()
                engine.bassEnhance = o.optDouble("knock", 0.45).toFloat()
                kitId = o.optString("kit", "boombap")
                melodicId = o.optString("melodic", "piano")
                o.optJSONArray("gains")?.let { g ->
                    for (i in 0 until minOf(g.length(), engine.trackGain.size)) {
                        engine.trackGain[i] = g.optDouble(i, 1.0).toFloat()
                    }
                }
                o.optJSONArray("mutes")?.let { m ->
                    for (i in 0 until minOf(m.length(), engine.trackMute.size)) {
                        engine.trackMute[i] = m.optBoolean(i, false)
                    }
                }
                o.optJSONArray("patterns")?.let { pats ->
                    for (i in 0 until minOf(pats.length(), engine.patterns.size)) {
                        unpackPattern(pats.getJSONArray(i), engine.patterns[i])
                    }
                }
                o.optJSONArray("accents")?.let { accs ->
                    for (i in 0 until minOf(accs.length(), engine.accents.size)) {
                        unpackPattern(accs.getJSONArray(i), engine.accents[i])
                    }
                }
                o.optJSONArray("basslines")?.let { basses ->
                    for (i in 0 until minOf(basses.length(), engine.basslines.size)) {
                        val row = basses.getJSONArray(i)
                        for (s in 0 until minOf(row.length(), engine.basslines[i].size)) {
                            engine.basslines[i][s] = row.optInt(s, -1)
                        }
                    }
                }
                engine.bassBank = o.optString("bassBank", "808")
                engine.bassGain = o.optDouble("bassGain", 1.0).toFloat()
                engine.patternFlow.value = engine.currentPattern
            }
        } catch (_: Exception) {
        }
        for (i in 0 until AudioEngine.MIC_SLOTS) {
            val wav = File(context.filesDir, "sample$i.wav")
            if (wav.exists()) {
                WavWriter.readFile(wav)?.let { engine.micSamples[i] = it }
            }
        }
    }
}
