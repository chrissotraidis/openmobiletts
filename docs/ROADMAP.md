# Open Mobile TTS — Roadmap

## Version Summary

| Version | Android | Desktop | Status |
|---------|---------|---------|--------|
| **v1.0** | Capacitor WebView (connects to server over WiFi) | SvelteKit + FastAPI + Kokoro (PyTorch) | **Current** |
| **v2.0** | Native Kotlin app with on-device Sherpa-ONNX TTS | SvelteKit + FastAPI + choice of Kokoro (PyTorch) or Sherpa-ONNX | Planned |

---

## v1.0 — Capacitor Android + Server (Current)

### Architecture

```
Desktop:
  Browser ──localhost──► python run.py (FastAPI + Kokoro + SvelteKit UI)
  One process. One port. No network.

Android:
  Phone (Capacitor WebView) ──WiFi──► Computer (python run.py)
  Phone is a thin client. Server does all TTS work.
```

### What's Included

- **Desktop web app**: `python run.py` → everything at localhost:8000
- **Android app**: Same SvelteKit UI in a Capacitor WebView shell
- **Configurable server URL**: Settings > Server Connection > enter computer IP
- **CORS middleware**: Allows cross-origin requests from Android WebView
- **All existing features**: Text input, document upload, 11 voices, streaming playback, history, text highlighting

### Limitations

- Android app requires a computer running the server on the same WiFi
- TTS model (Kokoro, 320MB PyTorch) cannot run on the phone
- Not a standalone mobile app — it's a remote client

### Files

```
server/                    FastAPI + Kokoro TTS (runs on computer)
client/                    SvelteKit UI
client/capacitor.config.ts Capacitor configuration
client/android/            Android project (open in Android Studio)
```

---

## v2.0 — Native On-Device Android + Desktop Preserved

### Goals

1. **Android**: Standalone native app with on-device TTS. No server, no WiFi, no computer.
2. **Desktop**: Add Sherpa-ONNX as an alternative TTS engine alongside the original PyTorch Kokoro. Users choose which backend to use.

### Architecture

```
Desktop (v2 — dual engine):
  Browser ──localhost──► python run.py (FastAPI + SvelteKit UI)
                              │
                              ├── Engine: Kokoro (PyTorch)  ← original, best GPU perf
                              └── Engine: Sherpa-ONNX       ← lighter deps, same quality

Android (v2 — fully standalone):
  ┌─────────────────────────────────────────┐
  │          Native Kotlin App              │
  │                                         │
  │  Jetpack Compose UI                     │
  │  ├── Generate Screen                    │
  │  ├── History Screen                     │
  │  └── Settings Screen                    │
  │                                         │
  │  Sherpa-ONNX TTS Engine                 │
  │  ├── Kokoro INT8 model (~95 MB)         │
  │  ├── espeak-ng phonemizer (bundled)     │
  │  └── JNI native libs (~15 MB)           │
  │                                         │
  │  Text Preprocessor (ported from Python) │
  │  Document Processor (PDF/DOCX/TXT)      │
  │  AudioTrack streaming playback          │
  │  Room DB for history                    │
  └─────────────────────────────────────────┘
  No network. No server. Everything on-device.
```

### Desktop Dual-Engine Design

Both engines produce the same quality audio from the same Kokoro model — they differ in inference runtime and dependencies.

| | Kokoro (PyTorch) | Sherpa-ONNX |
|---|---|---|
| **Model format** | PyTorch (.pth, ~320 MB) | ONNX INT8 (~95 MB) |
| **Dependencies** | torch, numpy, kokoro Python package | sherpa-onnx Python package |
| **GPU support** | CUDA (NVIDIA), MPS (Apple Silicon) | CPU-optimized (XNNPACK) |
| **GPU speed** | 35-210x real-time | N/A (CPU only on desktop) |
| **CPU speed** | 2-5x real-time | ~2-5x real-time (comparable) |
| **Install size** | ~400 MB (PyTorch + deps) | ~50 MB (much lighter) |
| **Best for** | Users with NVIDIA GPU | Users who want minimal deps |

**Implementation**: The `tts_engine.py` module gets an engine abstraction. A setting (env var or UI toggle) selects which backend to use. Both implement the same interface: `generate(text, voice, speed) → audio chunks`.

```python
# tts_engine.py — v2 concept
class TTSEngine:
    def __init__(self, engine="kokoro"):  # or "sherpa-onnx"
        if engine == "sherpa-onnx":
            self.backend = SherpaOnnxBackend()
        else:
            self.backend = KokoroBackend()  # current implementation
```

The rest of the server (FastAPI, streaming protocol, text preprocessing, document processing) stays the same regardless of which engine is selected.

### What Changes from v1

| Component | v1 | v2 |
|-----------|----|----|
| Android app | Capacitor WebView (thin client) | Native Kotlin (standalone) |
| TTS on Android | Server (PyTorch, runs on computer) | On-device (Sherpa-ONNX INT8) |
| Android network | Required (WiFi to server) | Not required |
| Desktop web app | SvelteKit + FastAPI | Same architecture, kept for computer users |
| Desktop TTS engine | Kokoro (PyTorch) only | Choice of Kokoro (PyTorch) **or** Sherpa-ONNX |
| `server/src/tts_engine.py` | Single PyTorch backend | Engine abstraction with pluggable backends |
| `client/android/` (Capacitor) | Active | Removed — replaced by native app |
| `android/` (native) | Does not exist | New top-level directory |

### What Gets Kept

- `server/` — Desktop users still use the web app
- `client/` — SvelteKit UI for desktop (Capacitor no longer needed)
- `docs/` — Updated for both platforms
- `run.py`, `Dockerfile`, `docker-compose.yml` — Desktop deployment

### What Gets Removed

- `client/android/` — Capacitor Android project (replaced by native)
- `client/capacitor.config.ts` — No longer needed
- Capacitor dependencies from `client/package.json`

### What Gets Added

```
android/                              New top-level native Android project
├── app/src/main/
│   ├── java/com/openmobiletts/app/
│   │   ├── MainActivity.kt
│   │   ├── tts/
│   │   │   ├── TtsManager.kt        Sherpa-ONNX wrapper
│   │   │   └── TtsChunker.kt        Text → chunks
│   │   ├── audio/
│   │   │   └── AudioPlaybackManager.kt  AudioTrack streaming
│   │   ├── document/
│   │   │   └── DocumentProcessor.kt     PDF/DOCX/TXT
│   │   ├── text/
│   │   │   └── TextPreprocessor.kt      Port from Python
│   │   ├── data/
│   │   │   ├── AppDatabase.kt           Room DB
│   │   │   ├── HistoryDao.kt
│   │   │   └── SettingsRepository.kt    DataStore
│   │   └── ui/
│   │       ├── theme/Theme.kt
│   │       ├── screens/
│   │       │   ├── GenerateScreen.kt
│   │       │   ├── HistoryScreen.kt
│   │       │   └── SettingsScreen.kt
│   │       └── components/
│   │           ├── PlayerBar.kt
│   │           ├── VoiceSelector.kt
│   │           └── SpeedSlider.kt
│   ├── assets/
│   │   └── kokoro-int8/              Model files (~95 MB)
│   │       ├── model.int8.onnx
│   │       ├── voices.bin
│   │       ├── tokens.txt
│   │       └── espeak-ng-data/
│   └── res/
└── build.gradle.kts
```

### Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| UI | Jetpack Compose + Material 3 | Native Android UI |
| TTS inference | Sherpa-ONNX (JNI) | Runs Kokoro INT8 on-device |
| Phonemizer | espeak-ng (bundled native lib) | Text-to-phoneme conversion |
| Audio | AudioTrack (MODE_STREAM) | Streaming playback |
| Database | Room | History persistence |
| Settings | Jetpack DataStore | User preferences |
| PDF | Apache PDFBox Android | PDF text extraction |
| DOCX | Apache POI | DOCX text extraction |

### Expected Performance (Pixel 9 Pro)

| Metric | Value |
|--------|-------|
| Model size in APK | ~95 MB (INT8 ONNX + voices + espeak-ng data) |
| Total APK size | ~130-140 MB |
| RAM usage | ~100-300 MB at runtime |
| Short text (1-2 sentences) | ~1-3 seconds |
| Paragraph | ~5-8 seconds |
| Generation speed | ~0.8x real-time (generates slightly slower than playback) |
| First chunk delay | ~2-5 seconds, then seamless streaming |

### Implementation Phases

#### Android (native app)

| Phase | What | Depends On |
|-------|------|------------|
| **A1** | Project scaffolding + Sherpa-ONNX + "Hello World" audio | Nothing |
| **A2** | Streaming AudioTrack playback engine | A1 |
| **A3** | Text preprocessor + chunker (port from Python) | Nothing (parallel with A2) |
| **A4** | Document processor (PDF/DOCX/TXT) | Nothing (parallel with A2-A3) |
| **A5** | UI screens (Generate, History, Settings) | A2 |
| **A6** | Sentence highlighting + timing sync | A2, A3, A5 |
| **A7** | Data persistence (Room + file storage) | A5 |
| **A8** | Polish (notifications, media controls, background audio) | A5, A6, A7 |
| **A9** | Cleanup (remove Capacitor, update docs) | A8 |

**Android critical path**: A1 → A2 → A5 → A6 → A8

#### Desktop (dual engine)

| Phase | What | Depends On |
|-------|------|------------|
| **D1** | Add Sherpa-ONNX Python backend to `tts_engine.py` | Nothing |
| **D2** | Engine selection (env var + Settings UI toggle) | D1 |
| **D3** | Test parity (both engines produce same output for same input) | D1 |

Desktop phases can run in parallel with Android phases. D1 shares Sherpa-ONNX knowledge with A1.

### Key Dependencies

| Dependency | Size | License |
|-----------|------|---------|
| Sherpa-ONNX native libs | ~15 MB | Apache 2.0 |
| Kokoro INT8 model + voices | ~95 MB | Apache 2.0 |
| Jetpack Compose + Material 3 | ~5 MB | Apache 2.0 |
| Room | ~1 MB | Apache 2.0 |
| Apache PDFBox Android | ~3 MB | Apache 2.0 |
| Apache POI | ~5 MB | Apache 2.0 |

### Known Challenges

1. **Phonemizer**: Kokoro uses Misaki (Python). Sherpa-ONNX solves this by bundling espeak-ng as a native library — non-issue if using Sherpa-ONNX.
2. **Thermal throttling**: Long document generation keeps CPU busy. Chunk-by-chunk playback introduces natural idle periods that help.
3. **Model bundling vs download**: Bundling in APK is simpler (~140 MB total APK). Alternative: download on first launch for smaller install.
4. **Text preprocessing port**: 36 abbreviation regex patterns + number-to-words + Unicode normalization need porting from Python to Kotlin. Straightforward but tedious.

### Reference Projects

| Project | What it does |
|---------|-------------|
| [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) | Pre-built Android TTS with Kokoro — the foundation |
| [Sherpa-ONNX Android TTS Engine](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTtsEngine) | Example app to study/fork |
| [Kokoro-82M-Android](https://github.com/puff-dayo/Kokoro-82M-Android) | Native Android demo with INT8 |
| [expo-kokoro-onnx](https://github.com/isaiahbjork/expo-kokoro-onnx) | React Native approach (alternative) |

---

## Multi-Language Support (v2)

Kokoro-82M natively supports 9 languages. Sherpa-ONNX ships a multilingual ONNX model with 103 speakers across all of them. The model doesn't get bigger — same ~95 MB. The hard part isn't the TTS engine; it's the text preprocessing, language detection, and UI around it.

### Language & Voice Inventory

| Code | Language | Voices | Phonemizer | Notes |
|------|----------|--------|------------|-------|
| `a` | American English | 11 | Misaki (custom) | Current default |
| `b` | British English | 8 | Misaki (custom) | |
| `e` | Spanish | 2 | espeak-ng | |
| `f` | French | 1 | espeak-ng | |
| `h` | Hindi | 4 | espeak-ng | |
| `i` | Italian | 2 | espeak-ng | |
| `j` | Japanese | 5 | Misaki JAG2P | No spaces between words — custom chunking needed |
| `p` | Brazilian Portuguese | 3 | espeak-ng | |
| `z` | Mandarin Chinese | 8 | Misaki ZHG2P | No spaces between words — custom chunking needed |

**Sherpa-ONNX models**: `kokoro-multi-lang-v1_1` (103 speakers, all languages) replaces the English-only model on Android with no size increase.

### Three Layers of Complexity

**Layer 1 — Engine config (straightforward)**: `tts_engine.py` already accepts a lang_code and voice name. Extending from 2 language codes to 9 is a data change. Sherpa-ONNX just needs a speaker ID. Both backends use espeak-ng for phonemization, which supports all 9 languages.

**Layer 2 — Text preprocessing (bulk of the work)**: `text_preprocessor.py` has 36 English abbreviation patterns, English number-to-words, English sentence splitting. Every language needs its own version. Japanese and Chinese don't use spaces between words, so sentence splitting and chunking work completely differently. `num2words` supports most languages via a `lang` parameter, but abbreviation expansion and punctuation rules need per-language handling.

**Layer 3 — Language detection and mixing (hardest)**: Users might paste multi-language text or not know which language to select. Options range from manual selection (simplest) to automatic per-sentence detection (best UX, hardest). Mixed-language text (e.g., English with Japanese proper nouns) is a known weak spot for all TTS systems.

### Multi-Language Phases

#### Phase M1: Language selection + voice routing (high value, contained scope)

- Add language selector dropdown to UI (defaults to English)
- Extend voice map in `tts_engine.py` to include all 9 languages
- Filter voice picker to show only voices for the selected language
- Pass correct `lang_code` to Kokoro / speaker ID to Sherpa-ONNX
- Install `misaki[ja]` and `misaki[zh]` as optional deps for Japanese/Chinese on desktop
- On Android, swap to `kokoro-multi-lang-v1_1` model instead of English-only

**This alone gets working multilingual TTS.** Users pick a language, pick a voice, type text, get audio. Text preprocessing will be imperfect for non-English, but the TTS engine still produces intelligible output because espeak-ng handles phonemization.

| Depends On | Desktop | Android |
|-----------|---------|---------|
| D1 (Sherpa-ONNX backend) | Voice map + lang selector | — |
| A1 (project scaffolding) | — | Model swap + voice routing |

#### Phase M2: Per-language text preprocessing

- Make `text_preprocessor.py` language-aware with a strategy pattern
- Port abbreviation dictionaries per language (start with top 3-4 languages)
- Wire `num2words(lang=...)` to use selected language
- Adjust sentence splitting for CJK (Chinese/Japanese don't use periods the same way)
- Adjust chunking token limits (CJK characters map to phonemes differently than Latin text)

**Recommended approach**: Tackle language-by-language, not all at once. Start with whichever languages matter most. Japanese and Chinese need the most custom work due to fundamentally different text structure.

#### Phase M3: Automatic language detection (optional, quality-of-life)

- Add lightweight offline language detection (`langdetect` or `lingua-py`)
- Auto-detect at document level, pre-select the language dropdown
- Optionally detect per-chunk and switch voice/lang_code mid-stream (complex, may not sound great at language boundaries)

#### Phase M4: UI localization (separate concern)

- Translate the app's own interface strings (not TTS — the buttons, labels, etc.)
- Only worth doing if there's a non-English user base
- SvelteKit i18n libraries or simple JSON dictionary

### Recommended Sequencing

```
M1 is the clear starting point:
  Contained changes (voice map data, one dropdown, deps, model swap)
  Immediately unlocks 9 languages
  Users tolerate imperfect preprocessing over no language support

M2 is where the most time goes:
  Do it language-by-language, not all at once
  Japanese and Chinese need the most custom work (no word boundaries)
  Latin-script languages (Spanish, French, Italian, Portuguese) share patterns

M3 is nice-to-have:
  A dropdown works fine for most users
  Auto-detection adds complexity for marginal UX gain

M4 is separate:
  Only if non-English users become a real audience
```

### Multi-Language Phase Dependencies

```
v2 Core (A1-A9, D1-D3)
    │
    ├── M1 (language selection + voice routing)
    │     Depends on: A1 or D1 (engine must exist first)
    │     │
    │     ├── M2 (per-language preprocessing)
    │     │     Depends on: M1 + A3 or text_preprocessor.py existing
    │     │     │
    │     │     └── M3 (auto language detection)
    │     │           Depends on: M2 (detection is useless without preprocessing)
    │     │
    │     └── M4 (UI localization)
    │           Independent — can happen anytime
    │
```

---

## Detailed Reference Docs

- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** — Full phase-by-phase v2 implementation with code examples
- **[OFFLINE_TTS_FEASIBILITY.md](OFFLINE_TTS_FEASIBILITY.md)** — Research: model sizes, device benchmarks, architecture options
- **[ANDROID_APP_GUIDE.md](ANDROID_APP_GUIDE.md)** — v1 Capacitor Android setup and usage
