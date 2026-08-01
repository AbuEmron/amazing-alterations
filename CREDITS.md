# 🎼 Sample Library Credits

Mega Music Maker bundles world-class, legally-redistributable sample libraries.
Everything ships inside the app — nothing is ever downloaded at runtime.

## Roland TR-808 — Michael Fischer sample set (1994)

- **What**: 116 recordings taken directly from an actual Roland TR-808
  (Serial No. 103852) by Michael Fischer in 1994 — the most iconic drum
  machine in music history.
- **Source**: [tidalcycles/sounds-tr808-fischer](https://github.com/tidalcycles/sounds-tr808-fischer)
- **License**: [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (public domain)
- Used for: the 🎛 808 drum kit (kick, snare, hats, clap, tom, cowbell, cymbal).

## Salamander Grand Piano — Alexander Holm

- **What**: A real Yamaha C5 grand piano, recorded at 48 kHz/24-bit with two
  AKG C414 microphones ~12 cm above the strings. A legend among free
  instruments.
- **Source**: [sfzinstruments/SalamanderGrandPiano](https://github.com/sfzinstruments/SalamanderGrandPiano)
- **License**: [CC-BY 3.0](https://creativecommons.org/licenses/by/3.0/) —
  © Alexander Holm. This app's 🎹 Piano bank uses eight notes (velocity
  layer 10), pitch-shifted where needed to complete the pentatonic scale,
  resampled to 44.1 kHz/16-bit mono.

## VSCO 2 Community Edition — Versilian Studios / Sam Gossner

- **What**: A full orchestral sample library recorded by Versilian Studios
  and released to the public domain.
- **Source**: [sgossner/VSCO-2-CE](https://github.com/sgossner/VSCO-2-CE)
- **License**: [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (public domain)
- Used for: ✨ Bells (glockenspiel), 🪵 Marimba, and the 🎻 Orchestra kit
  (timpani, concert snare, xylophone, timpani roll).

## Processing

All samples were mixed to mono, resampled to 44.1 kHz/16-bit, trimmed,
faded, and peak-normalized (see the processing summary in this repo's
history). Pitch-shifted variants were produced by varispeed resampling.

Everything else in the app — the 🤖 Synth kit, FX pads, and synth piano —
is generated from pure math at startup (`android/.../audio/Synth.kt`).
