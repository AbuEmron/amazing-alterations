# Pocket Studio

A full music production studio that anyone can pick up in seconds — and powerful enough to actually make songs. **100% offline. 100% private. Yours.**

Two versions live in this repo:

| Version | Where | How to use |
|---|---|---|
| 📱 **Native Android app (Kotlin)** | [`android/`](android/) | Open in Android Studio and press Run — see [android/README.md](android/README.md) |
| 🌐 **Web version** | [`index.html`](index.html) | Open the file in any browser — works instantly, even offline |

## What's inside (both versions)

| Studio section | What it does |
|---|---|
| **Pads** | 12 performance pads — kick, snare, hats, FX. Switch between the synth kit, a real TR-808, and orchestral percussion. |
| **Step Sequencer** | 16 steps × 8 tracks × **4 chainable patterns (A–D)** with BPM, swing, per-step **accents** (long-press), and a metronome. **Chain** mode plays patterns in sequence — song arrangement on one screen. |
| **Keys** | Pentatonic quick keys or a **full 88-key piano** (A0–C8, real Salamander samples across the range), playable as grand piano, glockenspiel, marimba, synth, or **808-style sub bass** — with **Smart Chords** auto-harmonization. |
| **Bass Station** | Six bass instruments (808, Sub, Reese, Square, Pluck, Growl) with a 16-step pitch sequencer per pattern, mono note choking, and **Auto-Bass** — one tap writes a bassline locked to your kick. |
| **Slicer** | **Import any audio file** (MP3, WAV, M4A...) or use a mic recording, chop it into 4/8/16 playable slices, perform them like an MPC, and long-press slices into S1/S2 to sequence them into the beat. |
| **Mixer & FX** | Per-track level and mute, master **reverb**, and **tempo-synced delay** — all feeding the auto-master chain. |
| **Sampler** | Hold-to-record mic sampler; two sample slots feed the sequencer as extra tracks. |
| **Voice Booth** | Record vocals up to 5 minutes; every take is **auto-mastered** (noise gate, 80 Hz cleanup, compression, presence EQ, loudness), saved as WAV, and can be sent to the Sampler/Slicer. |
| **Record** | One-button recording of the **auto-mastered** master bus (rumble filter → program compressor → makeup gain → soft limiter) to WAV. |

## The Android app goes further

- **Sample-accurate real-time audio engine** — sequencer hits are scheduled at exact frame offsets, tighter than any timer-based toy app
- **Your voice in the beat** — mic recordings become sequencer tracks (S1/S2)
- **Swing control** for humanized rhythm
- **WAV export** straight into your Music folder
- **Zero internet permission** — the OS itself guarantees nothing ever leaves your device

## Never loses work

The whole session — all four patterns, accents, mixer, FX, tempo, kit selection, and your recorded samples — is saved automatically when the app goes to background and restored on launch, like any professional DAW.

## Why it's easy

- Everything on one scrollable screen — no menus, no modes to get lost in
- Pentatonic keys + Smart Chords + Auto-Beat mean every jam sounds musical
- No accounts, no ads, no in-app purchases, no network — ever
