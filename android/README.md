# Pocket Studio — Native Android App (Kotlin)

A full music production studio with a one-screen interface, running **100% offline and 100% privately** on your own device.

## 🔒 Privacy by construction

- The app requests **zero internet permission** — Android itself blocks it from ever touching the network. Check `AndroidManifest.xml`: the only permission is the microphone, used solely for recording your own sounds.
- Every drum, zap, and piano note is **synthesized from pure math** at startup (`Synth.kt`) — no sample downloads, no accounts, no analytics, no ads.
- Your voice recordings live only in app memory; songs you save are standard WAV files in your own Music folder.

## 🚀 What makes it stand out

- **Sample-accurate audio engine** (`AudioEngine.kt`): a dedicated real-time thread renders into a low-latency `AudioTrack`, and sequencer hits are scheduled at exact frame offsets inside each buffer — tighter timing than timer-triggered apps.
- **Your voice as an instrument**: mic recordings become sampler pads *and* two dedicated sequencer tracks (S1/S2).
- **Swing control** shifts off-beats for real humanized rhythm; **Smart Chords** auto-harmonizes the keys.
- **What-you-hear-is-what-you-get recording**: the song recorder taps the master mix bus and writes standard 16-bit WAV files.
- **Auto-Beat** generator: one tap always produces a musical groove.
- **Pentatonic keys**: every melody stays in key.

## 🛠 Getting the APK

**Easiest**: every push to GitHub builds a signed APK automatically (see
`.github/workflows/build-apk.yml`). Grab `pocket-studio.apk` from the
repository's **Releases** page ("Pocket Studio APK (latest build)") or from
the workflow run's artifacts, copy it to your phone, and install.

Note: the committed signing keystore is a convenience key for personal
sideloading only — generate a private key before any store distribution.

**Or build locally**: open the `android/` folder in Android Studio (Ladybug
or newer) and press Run — or from the command line:

```bash
cd android
# point local.properties at your SDK, then:
./gradlew assembleDebug     # or: gradle assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+ and Android SDK 35. Min supported device: Android 7.0 (API 24).

## 🗂 Code map

| File | What it does |
|---|---|
| `audio/Synth.kt` | Generates the entire drum kit + pentatonic piano from math (biquad-filtered noise, phase-accumulated pitch sweeps) |
| `audio/AudioEngine.kt` | Real-time mixer, sample-accurate 8-track × 16-step sequencer with swing, master-bus song capture |
| `audio/MicSampler.kt` | Records mic clips into playable, normalized samples |
| `audio/WavWriter.kt` | Saves songs as WAV via MediaStore (`Music/MegaMusicMaker`) |
| `ui/StudioScreen.kt` | The whole kid-friendly Jetpack Compose UI |
| `MainActivity.kt` | Entry point; engine lifecycle |
