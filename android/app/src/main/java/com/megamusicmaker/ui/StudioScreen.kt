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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.megamusicmaker.audio.AudioEngine
import com.megamusicmaker.audio.MicSampler
import com.megamusicmaker.audio.Synth
import com.megamusicmaker.audio.WavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/* ---------- palette & data ---------- */

private val BgTop = Color(0xFF1A0B3D)
private val BgMid = Color(0xFF2D1B69)
private val BgBot = Color(0xFF0F2557)
private val CardBg = Color.White.copy(alpha = 0.08f)

private class PadDef(val emoji: String, val label: String, val color: Color, val sample: FloatArray)

private val pads = listOf(
    PadDef("🥁", "Boom", Color(0xFFE74C3C), Synth.kick),
    PadDef("🪘", "Bap", Color(0xFFE67E22), Synth.snare),
    PadDef("🎩", "Tss", Color(0xFFF1C40F), Synth.hat),
    PadDef("👏", "Clap", Color(0xFF2ECC71), Synth.clap),
    PadDef("🍅", "Dum", Color(0xFF1ABC9C), Synth.tom),
    PadDef("🔔", "Ding", Color(0xFF3498DB), Synth.bell),
    PadDef("⚡", "Zap", Color(0xFF9B59B6), Synth.zap),
    PadDef("🐸", "Boing", Color(0xFFE84393), Synth.boing),
    PadDef("🫧", "Pop", Color(0xFFFD79A8), Synth.pop),
    PadDef("🐦", "Tweet", Color(0xFF00B894), Synth.whistle),
    PadDef("🌾", "Shake", Color(0xFFFDCB6E), Synth.shaker),
    PadDef("🤖", "Robot", Color(0xFF636E72), Synth.robot),
)

private val trackEmojis = listOf(
    "🥁", "🪘", "🎩", "👏",
    "🍅", "🔔", "🦖", "🐱",
)

private val trackColors = listOf(
    Color(0xFFE74C3C), Color(0xFFE67E22), Color(0xFFF1C40F), Color(0xFF2ECC71),
    Color(0xFF1ABC9C), Color(0xFF3498DB), Color(0xFF16A085), Color(0xFFE84393),
)

private val keyColors = listOf(
    Color(0xFFFF5F5F), Color(0xFFFF9F43), Color(0xFFFFD93D), Color(0xFF4CD964),
    Color(0xFF34C8C8), Color(0xFF4C8DFF), Color(0xFF9B59FF), Color(0xFFFF6BD6),
)

private val keyEmojis = listOf(
    "🎈", "🍓", "🌞", "🍀",
    "🐬", "🫐", "🍇", "🌸",
)

private val micEmojis = listOf("🦖", "🐱", "🚀", "🎉")

/* ---------- main screen ---------- */

@Composable
fun StudioScreen(engine: AudioEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var patternVersion by remember { mutableStateOf(0) }
    var micVersion by remember { mutableStateOf(0) }
    var bpm by remember { mutableStateOf(110f) }
    var swing by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val songs = remember { mutableStateListOf<Pair<String, Uri>>() }
    val currentStep by engine.stepFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgMid, BgBot)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "🎵 Mega Music Maker 🎵",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD93D),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Tap anything. Make music. You can't get it wrong! 🌟",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Section("🥁 Tap the Sound Pads!") {
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

        Section("🤖 Beat Machine — paint your beat!") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BigButton(
                    if (isPlaying) "⏸ Stop" else "▶ Play",
                    if (isPlaying) Color(0xFFF06D1A) else Color(0xFF14A04A),
                ) { isPlaying = engine.togglePlay() }
                BigButton("✨ Magic", Color(0xFF7A2FF0)) {
                    magicBeat(engine, micVersion)
                    patternVersion++
                    if (!isPlaying) isPlaying = engine.togglePlay()
                }
                BigButton("🧹 Clear", Color(0xFF3C4A66)) {
                    for (t in 0 until AudioEngine.TRACKS) engine.pattern[t].fill(false)
                    patternVersion++
                }
            }
            Spacer(Modifier.height(10.dp))
            LabeledSlider("🐢", "🐇", bpm, 60f..180f) {
                bpm = it
                engine.bpm = it.toInt()
            }
            LabeledSlider("🤖", "😎", swing, 0f..0.3f) {
                swing = it
                engine.swing = it
            }
            Spacer(Modifier.height(6.dp))
            SequencerGrid(engine, currentStep, patternVersion, micVersion) { patternVersion++ }
            Text(
                "🦖 and 🐱 rows play YOUR recorded sounds!",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        Section("🌈 Rainbow Piano — every note sounds great!") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(150.dp),
            ) {
                for (i in 0 until 8) {
                    Box(Modifier.weight(1f)) {
                        PianoKey(keyEmojis[i], keyColors[i], (150 - i * 9).dp) {
                            engine.play(Synth.piano[i], 0.9f)
                        }
                    }
                }
            }
        }

        Section("🎤 My Sounds — record YOUR voice!") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until AudioEngine.MIC_SLOTS) {
                    Box(Modifier.weight(1f)) {
                        MicPad(engine, i, micEmojis[i], micVersion) { micVersion++ }
                    }
                }
            }
            Text(
                "Hold a pad and make a silly sound 🗣 — let go, then tap it to play!",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Section("💿 Record Your Song!") {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BigButton(
                    if (isRecording) "⏹ Stop & Save" else "🔴 Start Recording",
                    if (isRecording) Color(0xFFAA0000) else Color(0xFFD1114A),
                ) {
                    if (!isRecording) {
                        engine.startRecording()
                        isRecording = true
                    } else {
                        isRecording = false
                        val data = engine.stopRecording()
                        val name = "My Song #${songs.size + 1}"
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
                    Text("💿 $name", fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
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
                    "Saved in your Music folder 📁 — they never leave your device.",
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
            "🔒 100% offline · no internet permission · your sounds stay yours",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ---------- pieces ---------- */

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .padding(14.dp),
    ) {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        )
        content()
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
    left: String,
    right: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(left, fontSize = 22.sp)
        Slider(
            value = value,
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
        Text(right, fontSize = 22.sp)
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
                    trackEmojis[t],
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(34.dp)
                        .pointerInput(t) {
                            detectTapGestures { engine.play(engine.trackSample(t)) }
                        },
                )
                for (s in 0 until AudioEngine.STEPS) {
                    val on = engine.pattern[t][s]
                    val cellBg = when {
                        on && hasSample -> trackColors[t]
                        on -> trackColors[t].copy(alpha = 0.35f)
                        s % 4 == 0 -> Color.White.copy(alpha = 0.18f)
                        else -> Color.White.copy(alpha = 0.10f)
                    }
                    Box(
                        Modifier
                            .padding(2.dp)
                            .size(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(cellBg)
                            .then(
                                if (currentStep == s) {
                                    Modifier.border(2.dp, Color.White, RoundedCornerShape(7.dp))
                                } else Modifier
                            )
                            .pointerInput(t, s) {
                                detectTapGestures {
                                    engine.pattern[t][s] = !engine.pattern[t][s]
                                    if (engine.pattern[t][s]) engine.play(engine.trackSample(t))
                                    onToggled()
                                }
                            },
                    )
                }
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
        Text(emoji, fontSize = 20.sp)
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
            Text(emoji, fontSize = 28.sp)
            Text(
                when {
                    recordingNow -> "🎙 listening…"
                    hasSample -> "tap to play!"
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
