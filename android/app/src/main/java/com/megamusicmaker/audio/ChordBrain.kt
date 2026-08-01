package com.megamusicmaker.audio

import kotlin.random.Random

/**
 * The music-theory brain behind Magic Chords: given the melody note a kid
 * just played, it picks the chord that best harmonizes it.
 *
 * Scoring combines two ideas from harmony:
 *  - chord tones: a chord containing the melody note wins strongly
 *  - voice leading: chords that naturally follow the previous chord
 *    (I-IV-V-vi progressions and friends) get a flow bonus
 * A pinch of randomness keeps the accompaniment alive, so the same melody
 * harmonizes a little differently each pass - like a real accompanist.
 */
object ChordBrain {

    /** [tones] are scale degrees 0..6 (C D E F G A B) into a chord-note bank. */
    class Chord(val name: String, val display: String, val tones: IntArray)

    val chords = listOf(
        Chord("C", "🌞 C major", intArrayOf(0, 2, 4)),
        Chord("Dm", "🌙 D minor", intArrayOf(1, 3, 5)),
        Chord("Em", "🌙 E minor", intArrayOf(2, 4, 6)),
        Chord("F", "🌞 F major", intArrayOf(3, 5, 0)),
        Chord("G", "🌞 G major", intArrayOf(4, 6, 1)),
        Chord("Am", "🌙 A minor", intArrayOf(5, 0, 2)),
    )

    /** How naturally chord `j` follows chord `i` (rows = from, cols = to). */
    private val flow = arrayOf(
        //          C  Dm Em F  G  Am
        intArrayOf(1, 1, 1, 3, 3, 2),  // from C
        intArrayOf(1, 0, 0, 1, 3, 1),  // from Dm
        intArrayOf(1, 1, 0, 2, 1, 2),  // from Em
        intArrayOf(3, 1, 0, 1, 3, 1),  // from F
        intArrayOf(3, 0, 1, 1, 1, 2),  // from G
        intArrayOf(1, 2, 1, 3, 2, 1),  // from Am
    )

    private var last = 0

    fun reset() {
        last = 0
    }

    /** Pick the best chord for a melody note (scale degree 0..6). */
    fun pick(melodyDegree: Int): Chord {
        var bestIdx = 0
        var bestScore = Int.MIN_VALUE
        for (i in chords.indices) {
            var s = flow[last][i] + Random.nextInt(2)
            if (chords[i].tones.contains(melodyDegree)) s += 5
            if (i == last) s -= 1
            if (s > bestScore) {
                bestScore = s
                bestIdx = i
            }
        }
        last = bestIdx
        return chords[bestIdx]
    }
}
