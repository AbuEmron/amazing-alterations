package com.megamusicmaker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.megamusicmaker.audio.AudioEngine
import com.megamusicmaker.audio.ChordBrain
import com.megamusicmaker.audio.MicSampler
import com.megamusicmaker.audio.ProjectStore
import com.megamusicmaker.audio.SampleLibrary
import com.megamusicmaker.audio.Synth
import com.megamusicmaker.audio.VocalMaster
import com.megamusicmaker.audio.WavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.random.Random

/* ---------- palette & data ---------- */

private val BgTop = Color(0xFF1A0B3D)
private val BgMid = Color(0xFF2D1B69)
private val BgBot = Color(0xFF0F2557)
private val CardBg = Color.White.copy(alpha = 0.08f)

private data class PadDef(val emoji: String, val label: String, val color: Color, val sample: FloatArray)

private val basePads = listOf(
    PadDef("🥁", "Kick", Color(0xFFE74C3C), Synth.kick),
    PadDef("🪘", "Snare", Color(0xFFE67E22), Synth.snare),
    PadDef("🎩", "Hi-Hat", Color(0xFFF1C40F), Synth.hat),
    PadDef("👏", "Clap", Color(0xFF2ECC71), Synth.clap),
    PadDef("🛢", "Tom", Color(0xFF1ABC9C), Synth.tom),
    PadDef("🔔", "Bell", Color(0xFF3498DB), Synth.bell),
    PadDef("⚡", "Zap", Color(0xFF9B59B6), Synth.zap),
    PadDef("〰", "Boing", Color(0xFFE84393), Synth.boing),
    PadDef("🫧", "Pop", Color(0xFFFD79A8), Synth.pop),
    PadDef("📯", "Whistle", Color(0xFF00B894), Synth.whistle),
    PadDef("🎚", "Shaker", Color(0xFFFDCB6E), Synth.shaker),
    PadDef("🤖", "Robot", Color(0xFF636E72), Synth.robot),
)

private val trackLabels = listOf(
    "🥁", "🪘", "🎩", "👏",
    "🛢", "🔔", "S1", "S2",
)

private val trackColors = listOf(
    Color(0xFFE74C3C), Color(0xFFE67E22), Color(0xFFF1C40F), Color(0xFF2ECC71),
    Color(0xFF1ABC9C), Color(0xFF3498DB), Color(0xFF16A085), Color(0xFFE84393),
)

private val keyColors = listOf(
    Color(0xFFFF5F5F), Color(0xFFFF9F43), Color(0xFFFFD93D), Color(0xFF4CD964),
    Color(0xFF34C8C8), Color(0xFF4C8DFF), Color(0xFF9B59FF), Color(0xFFFF6BD6),
)

private val keyLabels = listOf(
    "C", "D", "E", "G",
    "A", "C", "D", "E",
)

private val micLabels = listOf("S1", "S2", "S3", "S4")

/** Scale degree (0..6 = C..B) of each rainbow key - pentatonic C D E G A C D E. */
private val keyDegrees = intArrayOf(0, 1, 2, 4, 5, 0, 1, 2)

/** Simple-mode key MIDI notes for the sub bass bank (C2 pentatonic). */
private val subSimpleMidis = intArrayOf(36, 38, 40, 43, 45, 48, 50, 52)

private fun isBlackKey(midi: Int) = when (midi % 12) {
    1, 3, 6, 8, 10 -> true
    else -> false
}

/** Pitch class -> C-major scale degree, or -1 for non-diatonic notes. */
private val pitchClassDegree = intArrayOf(0, -1, 1, -1, 2, 3, -1, 4, -1, 5, -1, 6)

/* ---------- main screen ---------- */

@Composable
fun StudioScreen(engine: AudioEngine, library: SampleLibrary) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var patternVersion by remember { mutableStateOf(0) }
    var micVersion by remember { mutableStateOf(0) }
    var bpm by remember { mutableStateOf(engine.bpm.toFloat()) }
    var swing by remember { mutableStateOf(engine.swing) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var kitId by remember { mutableStateOf(ProjectStore.kitId) }
    var melodicId by remember { mutableStateOf(ProjectStore.melodicId) }
    var patternSel by remember { mutableStateOf(engine.currentPattern) }
    var chainOn by remember { mutableStateOf(engine.chain) }
    var clickOn by remember { mutableStateOf(engine.metronome) }
    var mixerVersion by remember { mutableStateOf(0) }

    // Voice Booth state lives at screen level so collapsing the section can
    // never interrupt an active recording or lose takes
    val vocalSampler = remember { MicSampler() }
    var vocalRecording by remember { mutableStateOf(false) }
    var vocalProcessing by remember { mutableStateOf(false) }
    var vocalElapsed by remember { mutableStateOf(0) }
    val vocalTakes = remember { mutableStateListOf<Triple<String, Uri, FloatArray>>() }
    val vocalPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(vocalRecording) {
        vocalElapsed = 0
        while (vocalRecording) {
            delay(1000)
            vocalElapsed++
        }
    }
    var magicChords by remember { mutableStateOf(false) }
    var chordLabel by remember { mutableStateOf("") }
    var fullKeys by remember { mutableStateOf(false) }
    var sliceSrc by remember { mutableStateOf(0) }
    var sliceCount by remember { mutableStateOf(8) }
    val songs = remember { mutableStateListOf<Pair<String, Uri>>() }
    val currentStep by engine.stepFlow.collectAsState()
    val libLoaded by library.loaded.collectAsState()
    val livePattern by engine.patternFlow.collectAsState()

    // While chaining, follow the playing pattern in the grid
    LaunchedEffect(livePattern) {
        if (engine.chain && patternSel != livePattern) {
            patternSel = livePattern
            patternVersion++
        }
    }
    // Restore the saved kit selection once samples finish loading
    LaunchedEffect(libLoaded) {
        if (libLoaded) {
            engine.kitTracks = library.kits.find { it.id == kitId }?.tracks
        }
    }
    // Keep persisted UI selections in sync
    LaunchedEffect(kitId) { ProjectStore.kitId = kitId }
    LaunchedEffect(melodicId) { ProjectStore.melodicId = melodicId }

    val currentKit = if (libLoaded) library.kits.find { it.id == kitId } else null
    val melodicNotes = if (libLoaded) library.melodic.find { it.id == melodicId }?.notes else null
    val pads = remember(currentKit) {
        if (currentKit == null) basePads
        else basePads.mapIndexed { i, p ->
            when (i) {
                in 0..5 -> p.copy(sample = currentKit.tracks[i])
                6 -> p.copy(
                    emoji = currentKit.bonus1Emoji, label = currentKit.bonus1Label,
                    sample = currentKit.bonus1,
                )
                7 -> p.copy(
                    emoji = currentKit.bonus2Emoji, label = currentKit.bonus2Label,
                    sample = currentKit.bonus2,
                )
                else -> p
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgMid, BgBot)))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Pocket Studio",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD93D),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Tap · Loop · Record — fully offline",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Section("Pads") {
            if (libLoaded && library.kits.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                ) {
                    Chip("Synth", kitId == "synth") {
                        kitId = "synth"
                        engine.kitTracks = null
                    }
                    for (kit in library.kits) {
                        Chip(kit.label, kitId == kit.id) {
                            kitId = kit.id
                            engine.kitTracks = kit.tracks
                        }
                    }
                }
            }
            for (row in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 4) {
                        val pad = pads[row * 4 + col]
                        Box(Modifier.weight(1f)) {
                            SoundPad(pad.emoji, pad.label, pad.color) {
                                engine.play(pad.sample)
                            }
                        }
                    }
                }
                if (row < 2) Spacer(Modifier.height(8.dp))
            }
        }

        Section("Step Sequencer") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BigButton(
                    if (isPlaying) "⏸ Stop" else "▶ Play",
                    if (isPlaying) Color(0xFFF06D1A) else Color(0xFF14A04A),
                ) { isPlaying = engine.togglePlay() }
                BigButton("Auto-Beat", Color(0xFF7A2FF0)) {
                    magicBeat(engine, micVersion)
                    patternVersion++
                    if (!isPlaying) isPlaying = engine.togglePlay()
                }
                BigButton("Clear", Color(0xFF3C4A66)) {
                    for (t in 0 until AudioEngine.TRACKS) engine.pattern[t].fill(false)
                    patternVersion++
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                for (p in 0 until AudioEngine.PATTERNS) {
                    Chip(('A' + p).toString(), patternSel == p) {
                        engine.currentPattern = p
                        patternSel = p
                        patternVersion++
                    }
                }
                Chip("Chain", chainOn) {
                    chainOn = !chainOn
                    engine.chain = chainOn
                }
                Chip("Click", clickOn) {
                    clickOn = !clickOn
                    engine.metronome = clickOn
                }
            }
            LabeledSlider("BPM", "${bpm.toInt()}", bpm, 60f..180f) {
                bpm = it
                engine.bpm = it.toInt()
            }
            LabeledSlider("Swing", "${(swing * 100).toInt()}%", swing, 0f..0.3f) {
                swing = it
                engine.swing = it
            }
            Spacer(Modifier.height(6.dp))
            SequencerGrid(engine, currentStep, patternVersion, micVersion) { patternVersion++ }
            Text(
                "Tap a step to place it, long-press an active step to accent it. " +
                    "A-D are patterns; Chain plays them in sequence as a song. " +
                    "S1 & S2 rows play your recorded samples.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        Section("Mixer & FX", initiallyExpanded = false) {
            @Suppress("UNUSED_EXPRESSION") mixerVersion
            for (t in 0 until AudioEngine.TRACKS) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        trackLabels[t],
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(34.dp),
                    )
                    Chip("M", engine.trackMute[t]) {
                        engine.trackMute[t] = !engine.trackMute[t]
                        mixerVersion++
                    }
                    Slider(
                        value = engine.trackGain[t],
                        onValueChange = {
                            engine.trackGain[t] = it
                            mixerVersion++
                        },
                        valueRange = 0f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = trackColors[t],
                            activeTrackColor = trackColors[t],
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            var reverb by remember { mutableStateOf(engine.reverbMix) }
            var delayFx by remember { mutableStateOf(engine.delayMix) }
            var knock by remember { mutableStateOf(engine.bassEnhance) }
            LabeledSlider("Reverb", "${(reverb * 100).toInt()}%", reverb, 0f..1f) {
                reverb = it
                engine.reverbMix = it
            }
            LabeledSlider("Delay", "${(delayFx * 100).toInt()}%", delayFx, 0f..1f) {
                delayFx = it
                engine.delayMix = it
            }
            LabeledSlider("Knock", "${(knock * 100).toInt()}%", knock, 0f..1f) {
                knock = it
                engine.bassEnhance = it
            }
            Text(
                "Per-track level and mute. Delay is tempo-synced (dotted 8th). " +
                    "Knock synthesizes audible harmonics of the sub band so 808s " +
                    "hit even on phone speakers. " +
                    "Everything still runs through the auto-master chain.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        Section("Keys") {
            // Route a MIDI note through the right bank, with optional Smart Chords
            val playMidi: (Int) -> Unit = { midi ->
                when {
                    melodicId == "sub" -> SampleLibrary.noteFor(Synth.subAnchors, midi)
                        ?.let { (s, r) -> engine.play(s, 1f, 0, r) }
                    melodicId == "piano" && libLoaded && library.pianoAnchors.isNotEmpty() ->
                        SampleLibrary.noteFor(library.pianoAnchors, midi)
                            ?.let { (s, r) -> engine.play(s, 0.9f, 0, r) }
                    else -> {
                        val base = melodicNotes?.get(0) ?: Synth.piano[0]  // bank's C5 = MIDI 72
                        engine.play(base, 0.9f, 0, 2.0.pow((midi - 72) / 12.0).toFloat())
                    }
                }
                if (magicChords && melodicId != "sub") {
                    val degree = pitchClassDegree[((midi % 12) + 12) % 12]
                    if (degree >= 0) {
                        val chord = ChordBrain.pick(degree)
                        val bank = (if (libLoaded) {
                            library.melodic.find { it.id == melodicId }?.chordNotes
                        } else null) ?: Synth.chordNotes
                        chord.tones.forEachIndexed { k, deg -> engine.play(bank[deg], 0.45f, k * 30) }
                        chordLabel = chord.display
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Chip("Synth", melodicId == "synth") { melodicId = "synth" }
                if (libLoaded) {
                    for (bank in library.melodic) {
                        Chip(bank.label, melodicId == bank.id) { melodicId = bank.id }
                    }
                }
                Chip("Sub Bass", melodicId == "sub") { melodicId = "sub" }
                Chip("88 Keys", fullKeys) { fullKeys = !fullKeys }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Chip(
                    if (magicChords) "Smart Chords ON" else "Smart Chords",
                    magicChords,
                ) {
                    magicChords = !magicChords
                    chordLabel = ""
                    if (magicChords) ChordBrain.reset()
                }
                if (magicChords && chordLabel.isNotEmpty()) {
                    Text(
                        chordLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD93D),
                    )
                }
            }
            if (fullKeys) {
                FullKeyboard(playMidi)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(150.dp),
                ) {
                    for (i in 0 until 8) {
                        Box(Modifier.weight(1f)) {
                            PianoKey(keyLabels[i], keyColors[i], (150 - i * 9).dp) {
                                if (melodicId == "sub") {
                                    playMidi(subSimpleMidis[i])
                                } else {
                                    engine.play(melodicNotes?.get(i) ?: Synth.piano[i], 0.9f)
                                    if (magicChords) {
                                        val chord = ChordBrain.pick(keyDegrees[i])
                                        val bank = (if (libLoaded) {
                                            library.melodic.find { it.id == melodicId }?.chordNotes
                                        } else null) ?: Synth.chordNotes
                                        chord.tones.forEachIndexed { k, deg ->
                                            engine.play(bank[deg], 0.45f, k * 30)
                                        }
                                        chordLabel = chord.display
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (magicChords) {
                Text(
                    "Each note is auto-harmonized with the best-fitting chord.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }

        Section("Slicer", initiallyExpanded = false) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                for (i in 0 until AudioEngine.MIC_SLOTS) {
                    Chip("S${i + 1}", sliceSrc == i) { sliceSrc = i }
                }
                for (n in listOf(4, 8, 16)) {
                    Chip("$n", sliceCount == n) { sliceCount = n }
                }
            }
            @Suppress("UNUSED_EXPRESSION") micVersion
            val srcSample = engine.micSamples[sliceSrc]
            if (srcSample == null) {
                Text(
                    "Record something into S${sliceSrc + 1} in the Sampler below, then chop it here into $sliceCount playable slices.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val slices = remember(sliceSrc, sliceCount, micVersion) {
                    val len = (srcSample.size / sliceCount).coerceAtLeast(1)
                    Array(sliceCount) { i ->
                        val from = i * len
                        val to = if (i == sliceCount - 1) srcSample.size else (i + 1) * len
                        srcSample.copyOfRange(from.coerceAtMost(srcSample.size - 1), to.coerceAtMost(srcSample.size))
                    }
                }
                for (rowStart in 0 until sliceCount step 8) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in rowStart until minOf(rowStart + 8, sliceCount)) {
                            val hue = trackColors[i % trackColors.size]
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(hue, hue.copy(alpha = 0.6f))))
                                    .pointerInput(i, sliceSrc, sliceCount) {
                                        detectTapGestures(onPress = {
                                            engine.play(slices[i])
                                            tryAwaitRelease()
                                        })
                                    },
                            ) {
                                Text(
                                    "${i + 1}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "Tap slices to perform — resample your own sounds into new rhythms.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Section("Sampler") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until AudioEngine.MIC_SLOTS) {
                    Box(Modifier.weight(1f)) {
                        MicPad(engine, i, micLabels[i], micVersion) { micVersion++ }
                    }
                }
            }
            Text(
                "Hold a pad to record from the mic, release to stop, tap to play. S1 & S2 feed the sequencer.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Section("Voice Booth", initiallyExpanded = false) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BigButton(
                    when {
                        vocalRecording -> "⏹ Stop  ${vocalElapsed}s"
                        vocalProcessing -> "Mastering…"
                        else -> "🎙 Record Vocal"
                    },
                    if (vocalRecording) Color(0xFFAA0000) else Color(0xFF7A2FF0),
                ) {
                    if (!vocalProcessing) {
                        if (!vocalRecording) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                vocalPerm.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (vocalSampler.start()) {
                                vocalRecording = true
                            }
                        } else {
                            vocalRecording = false
                            vocalProcessing = true
                            scope.launch(Dispatchers.IO) {
                                val raw = vocalSampler.stop(300.0)
                                val mastered = raw?.let { VocalMaster.process(it) }
                                val ok = mastered != null && mastered.size > Synth.SR / 10
                                val uri = if (ok) {
                                    WavWriter.save(
                                        context,
                                        "vocal-${System.currentTimeMillis()}.wav",
                                        mastered!!,
                                    )
                                } else null
                                withContext(Dispatchers.Main) {
                                    vocalProcessing = false
                                    if (ok && uri != null) {
                                        vocalTakes.add(
                                            0,
                                            Triple("Vocal ${vocalTakes.size + 1}", uri, mastered!!),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for ((name, uri, sample) in vocalTakes) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(10.dp),
                ) {
                    Text(
                        "$name · ${sample.size / Synth.SR}s",
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Chip("▶ Play", false) {
                        try {
                            MediaPlayer.create(context, uri)?.apply {
                                setOnCompletionListener { it.release() }
                                start()
                            }
                        } catch (_: Exception) {
                        }
                    }
                    Chip("→ S4", false) {
                        engine.micSamples[3] = sample
                        micVersion++
                    }
                }
            }
            Text(
                "Every take is auto-mastered on stop: noise gate, 80 Hz cleanup, compression, presence EQ, loudness. " +
                    "Saved as WAV to your Music folder. '→ S4' sends it to the Sampler for slicing. " +
                    "Use headphones to record over a playing beat.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Section("Record") {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BigButton(
                    if (isRecording) "⏹ Stop & Save" else "● Record",
                    if (isRecording) Color(0xFFAA0000) else Color(0xFFD1114A),
                ) {
                    if (!isRecording) {
                        engine.startRecording()
                        isRecording = true
                    } else {
                        isRecording = false
                        val data = engine.stopRecording()
                        val name = "Take ${songs.size + 1}"
                        scope.launch(Dispatchers.IO) {
                            val uri = WavWriter.save(
                                context, "my-song-${System.currentTimeMillis()}.wav", data
                            )
                            if (uri != null) {
                                withContext(Dispatchers.Main) { songs.add(0, name to uri) }
                            }
                        }
                    }
                }
            }
            for ((name, uri) in songs) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(10.dp),
                ) {
                    Text(name, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                    BigButton("▶ Play", Color(0xFF1A7FD4)) {
                        try {
                            MediaPlayer.create(context, uri)?.apply {
                                setOnCompletionListener { it.release() }
                                start()
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            if (songs.isNotEmpty()) {
                Text(
                    "Saved as WAV in Music/MegaMusicMaker — nothing leaves your device.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }

        Text(
            "100% offline · no internet permission · your audio stays yours",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ---------- pieces ---------- */

@Composable
private fun Section(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(title) { detectTapGestures { expanded = !expanded } }
                .padding(bottom = if (expanded) 10.dp else 0.dp),
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "▾" else "▸",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        if (expanded) content()
    }
}

@Composable
private fun SoundPad(emoji: String, label: String, color: Color, onHit: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "padScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f))))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    onHit()
                    tryAwaitRelease()
                    pressed = false
                })
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 30.sp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Color(0xFFFFD93D).copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.12f)
            )
            .pointerInput(text) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF1A0B3D) else Color.White,
        )
    }
}

@Composable
private fun BigButton(text: String, color: Color, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "btnScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f))))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    onClick()
                    tryAwaitRelease()
                    pressed = false
                })
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.width(52.dp),
        )
        Slider(
            value = sliderValue,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD93D),
                activeTrackColor = Color(0xFFFFD93D),
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD93D),
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun SequencerGrid(
    engine: AudioEngine,
    currentStep: Int,
    patternVersion: Int,
    micVersion: Int,
    onToggled: () -> Unit,
) {
    // patternVersion / micVersion are read so toggles and new recordings recompose the grid
    @Suppress("UNUSED_EXPRESSION") patternVersion
    @Suppress("UNUSED_EXPRESSION") micVersion
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        for (t in 0 until AudioEngine.TRACKS) {
            val hasSample = engine.trackSample(t) != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    trackLabels[t],
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(34.dp)
                        .pointerInput(t) {
                            detectTapGestures { engine.play(engine.trackSample(t)) }
                        },
                )
                for (s in 0 until AudioEngine.STEPS) {
                    val on = engine.pattern[t][s]
                    val accented = on && engine.accent[t][s]
                    val cellBg = when {
                        on && hasSample -> trackColors[t]
                        on -> trackColors[t].copy(alpha = 0.35f)
                        s % 4 == 0 -> Color.White.copy(alpha = 0.18f)
                        else -> Color.White.copy(alpha = 0.10f)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(2.dp)
                            .size(34.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(cellBg)
                            .then(
                                if (currentStep == s) {
                                    Modifier.border(2.dp, Color.White, RoundedCornerShape(7.dp))
                                } else Modifier
                            )
                            .pointerInput(t, s) {
                                detectTapGestures(
                                    onTap = {
                                        engine.pattern[t][s] = !engine.pattern[t][s]
                                        if (!engine.pattern[t][s]) engine.accent[t][s] = false
                                        if (engine.pattern[t][s]) engine.play(engine.trackSample(t))
                                        onToggled()
                                    },
                                    onLongPress = {
                                        if (engine.pattern[t][s]) {
                                            engine.accent[t][s] = !engine.accent[t][s]
                                            if (engine.accent[t][s]) {
                                                engine.play(engine.trackSample(t), 1.25f)
                                            }
                                            onToggled()
                                        }
                                    },
                                )
                            },
                    ) {
                        if (accented) {
                            Text("●", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullKeyboard(playMidi: (Int) -> Unit) {
    val density = LocalDensity.current
    val whiteW = 42.dp
    val scroll = rememberScrollState()
    val whiteMidis = remember { (21..108).filterNot { isBlackKey(it) } }
    LaunchedEffect(Unit) {
        // open the keyboard centered near middle C
        val c4Index = whiteMidis.indexOf(60)
        scroll.scrollTo(with(density) { (whiteW * (c4Index - 4)).toPx() }.toInt().coerceAtLeast(0))
    }
    Box(Modifier.horizontalScroll(scroll)) {
        Row {
            for (midi in whiteMidis) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .width(whiteW)
                        .height(170.dp)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(Color(0xFFF4F1E8))
                        .pointerInput(midi) {
                            detectTapGestures(onPress = {
                                playMidi(midi)
                                tryAwaitRelease()
                            })
                        },
                ) {
                    if (midi % 12 == 0) {
                        Text(
                            "C${midi / 12 - 1}",
                            fontSize = 10.sp,
                            color = Color(0xFF8A8577),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
        whiteMidis.forEachIndexed { wIdx, midi ->
            val black = midi + 1
            if (black <= 108 && isBlackKey(black)) {
                Box(
                    modifier = Modifier
                        .offset(x = whiteW * (wIdx + 1) - 13.dp)
                        .width(26.dp)
                        .height(104.dp)
                        .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                        .background(Color(0xFF17151F))
                        .pointerInput(black) {
                            detectTapGestures(onPress = {
                                playMidi(black)
                                tryAwaitRelease()
                            })
                        },
                )
            }
        }
    }
}

@Composable
private fun PianoKey(emoji: String, color: Color, height: androidx.compose.ui.unit.Dp, onHit: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "keyScale")
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.7f))))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    onHit()
                    tryAwaitRelease()
                    pressed = false
                })
            }
            .padding(bottom = 8.dp),
    ) {
        Text(emoji, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun MicPad(
    engine: AudioEngine,
    slot: Int,
    emoji: String,
    micVersion: Int,
    onChanged: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") micVersion
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sampler = remember { MicSampler() }
    var recordingNow by remember { mutableStateOf(false) }
    val hasSample = engine.micSamples[slot] != null

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val borderColor = when {
        recordingNow -> Color(0xFFFF4D4D)
        hasSample -> Color(0xFF2FD66C)
        else -> Color.White.copy(alpha = 0.4f)
    }
    val bg = when {
        recordingNow -> Color(0xFFFF3C3C).copy(alpha = 0.25f)
        hasSample -> Color(0xFF2FD66C).copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(3.dp, borderColor, RoundedCornerShape(18.dp))
            .pointerInput(slot) {
                detectTapGestures(
                    onTap = { engine.play(engine.micSamples[slot]) },
                    onLongPress = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (sampler.start()) {
                            recordingNow = true
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (recordingNow) {
                            recordingNow = false
                            scope.launch(Dispatchers.IO) {
                                val clip = sampler.stop()
                                withContext(Dispatchers.Main) {
                                    if (clip != null) {
                                        engine.micSamples[slot] = clip
                                        engine.play(clip)
                                        onChanged()
                                    }
                                }
                            }
                        }
                    },
                )
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                when {
                    recordingNow -> "recording…"
                    hasSample -> "tap to play"
                    else -> "hold to record"
                },
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* ---------- magic beat ---------- */

private fun magicBeat(engine: AudioEngine, @Suppress("UNUSED_PARAMETER") micVersion: Int) {
    val chance: List<(Int) -> Double> = listOf(
        { s -> if (s % 4 == 0) 0.95 else if (s % 2 == 0) 0.2 else 0.05 },  // kick on the beat
        { s -> if (s % 8 == 4) 0.95 else 0.06 },                            // snare on 2 & 4
        { s -> if (s % 2 == 0) 0.8 else 0.25 },                             // hats drive it
        { s -> if (s % 8 == 6) 0.5 else 0.05 },                             // claps as spice
        { s -> if (s % 4 == 3) 0.3 else 0.04 },                             // tom fills
        { s -> if (s % 8 == 2) 0.35 else 0.03 },                            // bell sparkle
        { s -> if (s % 8 == 7) 0.4 else 0.05 },                             // your dino roar
        { s -> if (s % 16 == 12) 0.5 else 0.04 },                           // your cat sound
    )
    for (t in 0 until AudioEngine.TRACKS) {
        val hasSample = engine.trackSample(t) != null || t < 6
        for (s in 0 until AudioEngine.STEPS) {
            engine.pattern[t][s] = hasSample && Random.nextDouble() < chance[t](s)
        }
    }
}
