**ARCHIVED 3/16/26 — This document is for historical reference only. The v2 implementation is complete. See `docs/android-app/android-app-overview.md` for current architecture and `docs/status.md` for project status.**

---

# Implementation Plan: Android WebView + Native TTS Bridge (v2)

## Overview

The Android app loads the **same SvelteKit web app** in a WebView, backed by an embedded NanoHTTPD HTTP server that bridges API calls to the on-device Sherpa-ONNX TTS engine. Zero UI duplication — the web app is the single source of truth.

**Tech stack:** Kotlin, WebView, NanoHTTPD, Sherpa-ONNX (Kokoro INT8)

---

## Architecture

```
Desktop:  Browser → http://localhost:8000 → FastAPI (Python) → Kokoro TTS
Android:  WebView → http://localhost:8080 → NanoHTTPD (Kotlin) → Sherpa-ONNX TTS
```

Both platforms use the identical SvelteKit frontend. The only difference is the backend.

## Kotlin Files (8 total)

| File | Purpose |
|------|---------|
| `MainActivity.kt` | WebView host + programmatic model download UI |
| `TtsHttpServer.kt` | NanoHTTPD: API endpoints + static file serving from assets |
| `TtsManager.kt` | Sherpa-ONNX JNI wrapper, generates PCM FloatArray |
| `VoiceRegistry.kt` | Voice name → SID mapping for all 53 Kokoro voices |
| `WavEncoder.kt` | PCM FloatArray → WAV bytes (44-byte header + 16-bit PCM) |
| `ModelDownloader.kt` | Downloads Kokoro INT8 model from GitHub (~95 MB) |
| `TtsService.kt` | Foreground notification to keep process alive during generation |
| `OpenMobileTtsApp.kt` | Application singleton: TtsManager + server lifecycle |

## API Endpoints (NanoHTTPD)

The embedded server mirrors the Python FastAPI server:

| Endpoint | Method | Response |
|----------|--------|----------|
| `/api/voices` | GET | JSON array of 53 voices (name, language, gender, display_name) |
| `/api/health` | GET | `{"status": "healthy", "version": "2.0.0"}` |
| `/api/engines` | GET | Single engine: sherpa-onnx |
| `/api/engine` | GET | `{"engine": "sherpa-onnx"}` |
| `/api/engine/switch` | POST | No-op (single engine) |
| `/api/tts/stream` | POST | Streaming TIMING/AUDIO protocol with WAV chunks |
| `/*` | GET | Static files from `assets/webapp/` with SPA fallback |

### Streaming Protocol

Same TIMING/AUDIO framing as the Python server:
```
TIMING:{"text":"Hello world.","start":0,"end":1.5,"chunk_index":0}\n
AUDIO:72044\n
{WAV bytes}
TIMING:{"text":"How are you?","start":1.5,"end":2.8,"chunk_index":1}\n
AUDIO:62444\n
{WAV bytes}
```

Audio format difference: WAV (16-bit PCM, 24kHz) instead of MP3 (64kbps CBR). The web client's `new Audio(blobUrl)` plays both formats natively.

## Build Process

```bash
# 1. Build SvelteKit and copy to Android assets:
./android/copy-webapp.sh

# 2. Build APK in Android Studio (or Gradle):
cd android && ./gradlew assembleDebug
```

The `copy-webapp.sh` script runs `npm run build` in `client/` and copies the output to `android/app/src/main/assets/webapp/`.

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| UI | WebView (not Compose) | Zero duplication, automatic feature parity |
| API bridge | Embedded HTTP server | Full POST body support, same-origin, exact protocol match |
| HTTP server | NanoHTTPD | Lightweight, proven on Android, streaming support |
| Audio format | WAV (not MP3) | Trivial encoding (header + PCM), no native MP3 library needed |
| Server binding | 127.0.0.1 only | Security: not accessible from network |
| Static serving | From Android assets | Bundled at build time, no network needed |
| Download screen | Programmatic Views | Simple (progress + button), no Compose or XML layout deps |

## Dependencies

```kotlin
// build.gradle.kts
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-process:2.7.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("androidx.webkit:webkit:1.10.0")
implementation("org.nanohttpd:nanohttpd:2.3.1")
implementation("org.apache.commons:commons-compress:1.26.1")
// + Sherpa-ONNX JNI libs in jniLibs/ + Kotlin API in source
```

No Compose. No Room. No DataStore. No Material3. Minimal dependency footprint.
