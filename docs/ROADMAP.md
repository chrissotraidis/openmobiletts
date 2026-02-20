# Open Mobile TTS — Roadmap

## Version Summary

| Version | Android | Desktop | Status |
|---------|---------|---------|--------|
| **v2.0** | WebView + NanoHTTPD + on-device Sherpa-ONNX TTS | SvelteKit + FastAPI + choice of Kokoro (PyTorch) or Sherpa-ONNX | **Current** |

---

## v2.0 — Android WebView + Desktop Dual-Engine

### Goals

1. **Android**: Load the same SvelteKit web app in a WebView, with an embedded HTTP server bridging to on-device Sherpa-ONNX TTS. Zero UI duplication.
2. **Desktop**: Sherpa-ONNX available as an alternative TTS engine alongside the original PyTorch Kokoro. Users choose which backend to use.

### Architecture

```
Desktop (v2 — dual engine):
  Browser ──localhost:8000──► python run.py (FastAPI + SvelteKit UI)
                                    │
                                    ├── Engine: Kokoro (PyTorch)  ← original, best GPU perf
                                    └── Engine: Sherpa-ONNX       ← lighter deps, same quality

Android (v2 — WebView + native TTS bridge):
  ┌─────────────────────────────────────────┐
  │          Android Phone                  │
  │                                         │
  │  WebView → http://localhost:8080        │
  │  NanoHTTPD (static files + API)         │
  │  Sherpa-ONNX TTS Engine (JNI)          │
  │  Kokoro INT8 model (~95 MB)            │
  │                                         │
  │  Same SvelteKit UI as desktop.          │
  │  No external network. Fully offline.    │
  └─────────────────────────────────────────┘
```

### Why WebView (not Compose)?

The SvelteKit web app is already mobile-first with responsive layouts and 44x44px touch targets. Loading it in a WebView means:

- **Zero UI duplication** — one codebase for all platforms
- **Automatic feature parity** — every web UI change works on Android immediately
- **8 Kotlin files** instead of 24 — only native TTS engine + HTTP server bridge

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

### Android Project Structure

```
android/
├── copy-webapp.sh                    Bundles SvelteKit build into assets
├── app/src/main/
│   ├── java/com/openmobiletts/app/
│   │   ├── MainActivity.kt          WebView host + model download UI
│   │   ├── TtsHttpServer.kt         NanoHTTPD: API endpoints + static files
│   │   ├── TtsManager.kt            Sherpa-ONNX wrapper (JNI)
│   │   ├── AacEncoder.kt            PCM → AAC audio (hardware MediaCodec)
│   │   ├── WavEncoder.kt            PCM → WAV bytes (fallback)
│   │   ├── VoiceRegistry.kt         Voice name → SID mapping (53 voices)
│   │   ├── ModelDownloader.kt       Downloads Kokoro model (~95 MB)
│   │   ├── TtsService.kt            Foreground notification for keep-alive
│   │   ├── AppLog.kt                In-app log ring buffer
│   │   └── OpenMobileTtsApp.kt      Application singleton
│   ├── assets/webapp/                SvelteKit build (bundled at build time)
│   └── jniLibs/                      Sherpa-ONNX native libs
└── app/build.gradle.kts
```

### Android Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| UI | WebView (SvelteKit) | Same web app as desktop — zero duplication |
| HTTP server | NanoHTTPD | Serves static files + API endpoints |
| TTS inference | Sherpa-ONNX (JNI) | Runs Kokoro INT8 on-device |
| Audio format | AAC (ADTS, hardware MediaCodec) | ~6x smaller than WAV, no external libs |
| Audio playback | WebView HTML5 Audio | Native audio element in WebView |
| History/settings | WebView localStorage/IndexedDB | Same as desktop web app |

### Build Workflow

```bash
# 1. Bundle web app into Android assets:
./android/copy-webapp.sh

# 2. Open android/ in Android Studio → Build → Run
```

See [ANDROID_ARCHITECTURE.md](ANDROID_ARCHITECTURE.md) for full details.

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

- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** — Full phase-by-phase implementation with code examples
