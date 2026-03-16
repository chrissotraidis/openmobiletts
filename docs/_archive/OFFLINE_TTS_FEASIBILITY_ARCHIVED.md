**ARCHIVED 3/16/26 — This document is for historical reference only. It was the feasibility research that led to the current on-device architecture. The recommendation to use Sherpa-ONNX was adopted; Jetpack Compose was not. See `docs/android-app/android-app-overview.md` for what was actually built.**

---

# Can Kokoro TTS Run Entirely On-Device? (Pixel 9 Pro Feasibility Analysis)

## TL;DR

**Yes, it is absolutely feasible — and you are overcomplicating it.** The Kokoro 82M model already runs on Android phones today via multiple open-source projects. The server is unnecessary for a single-user mobile app on a Pixel 9 Pro. The entire FastAPI backend, Docker setup, JWT auth, and client-server streaming protocol can be replaced by an on-device inference engine bundled into a native Android app.

---

## The Core Question

The current architecture looks like this:

```
Phone (SvelteKit PWA) ──HTTP──► Server (FastAPI + Kokoro on CPU/GPU)
```

The question: can it instead be this?

```
Phone (Native App + Kokoro model embedded) ── no network needed
```

**Yes.** Here's the evidence.

---

## 1. Kokoro TTS Model: Mobile-Ready Today

### Model Sizes (ONNX format)

Kokoro has been exported to ONNX in multiple quantization levels:

| Variant | Size | Quality |
|---|---|---|
| FP32 (original) | ~326 MB | Reference quality |
| FP16 | ~163 MB | Negligible quality loss |
| INT8 quantized | ~86-92 MB | Very good — recommended for mobile |
| Voices file | ~5.5 MB | All 54 voices across 8 languages |
| **Total for mobile** | **~95 MB** | **Fits easily on any modern phone** |

For context, a single podcast episode is often 50-100 MB. This model is smaller than most apps on a phone.

### Projects Already Running Kokoro on Android

| Project | Approach | Status |
|---|---|---|
| [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) | Pre-built Android TTS Engine APKs with Kokoro | **Production-ready**, installable APKs available |
| [Kokoro-82M-Android](https://github.com/puff-dayo/Kokoro-82M-Android) | Native Android demo app, INT8 ONNX | Working demo |
| [expo-kokoro-onnx](https://github.com/isaiahbjork/expo-kokoro-onnx) | React Native (Expo) + ONNX Runtime | Working cross-platform app |
| [NimbleEdge](https://www.nimbleedge.com/blog/how-to-run-kokoro-tts-model-on-device/) | C++ transpiled pipeline on Android | Production-grade, benchmarked |

This is not theoretical — people are already doing it.

---

## 2. Pixel 9 Pro Hardware

The Pixel 9 Pro is more than capable:

| Spec | Value | Relevance |
|---|---|---|
| **RAM** | 16 GB LPDDR5X | Kokoro needs ~100-300 MB at runtime — this is <2% of available RAM |
| **CPU** | Tensor G4: 1x Cortex-X4 (3.1 GHz) + 3x A720 (2.6 GHz) + 4x A520 (1.95 GHz) | 8 cores, modern ARM architecture |
| **GPU** | Mali-G715 MP7, ~2.53 TFLOPS | Available via ONNX Runtime GPU delegate |
| **Storage** | 128 GB minimum | ~95 MB model is trivial |
| **NPU/TPU** | Google's on-device TPU (3rd gen) | **Not accessible to third-party apps** — but CPU/GPU are sufficient |

### Key limitation

Google does not expose the Tensor G4's TPU to third-party developers (only Google's own apps like Gemini Nano use it). But this doesn't matter — the CPU alone is sufficient for Kokoro inference, and the GPU can provide additional acceleration via ONNX Runtime's XNNPACK or NNAPI execution providers.

---

## 3. Expected Performance on Pixel 9 Pro

### Inference Speed

| Scenario | Expected Performance | User Experience |
|---|---|---|
| Short text (1-2 sentences) | ~1-3 seconds generation | Feels instant after brief pause |
| Medium text (paragraph) | ~5-8 seconds | Acceptable with streaming playback |
| Long document (page) | Chunk-by-chunk, each chunk ~2-5s | Start playing first chunk while generating next |

The key metric from NimbleEdge's benchmarks: **~8 seconds to generate 10 seconds of audio** on a recent smartphone with INT8 quantization. This is approximately **0.8x real-time** — meaning generation is slightly slower than playback.

### Why This Is Good Enough

For a document reader app, you don't need to generate the entire document before playback starts. The pipeline would be:

1. Split text into sentences/chunks (~250 tokens each)
2. Generate chunk 1 → start playing immediately
3. While chunk 1 plays (~10 seconds of audio), generate chunk 2 in the background
4. Seamless continuous playback with a small buffer

Since generation is ~0.8x real-time and playback is 1x real-time, **the generation catches up after a brief initial delay**. The user experience would be:

- **First sentence delay**: ~2-5 seconds (one-time wait)
- **Continuous playback after that**: seamless, no gaps

This is the same streaming approach you already designed in the server architecture, just running locally.

---

## 4. Recommended Architecture

### Simplest Path: Sherpa-ONNX Android TTS Engine

The absolute easiest approach:

1. **Use [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx)** as the TTS inference engine
2. **Build a native Android app** (Kotlin) that bundles the INT8 Kokoro model (~95 MB)
3. **No server, no auth, no network** — everything runs locally

Sherpa-ONNX provides:
- Pre-built Kotlin/Java API via JNI
- Pre-built Android TTS Engine APKs you can study or fork
- ONNX Runtime with XNNPACK acceleration built-in
- Support for all 54 Kokoro voices
- Apache 2.0 license (same as Kokoro)

### App Architecture

```
┌─────────────────────────────────────────┐
│          Android App (Kotlin)           │
│                                         │
│  ┌──────────────┐   ┌───────────────┐  │
│  │  UI Layer    │   │ Document      │  │
│  │  (Compose)   │   │ Processor     │  │
│  │              │   │ (PDF/DOCX/TXT)│  │
│  └──────┬───────┘   └───────┬───────┘  │
│         │                   │          │
│         ▼                   ▼          │
│  ┌──────────────────────────────────┐  │
│  │   Sherpa-ONNX TTS Engine        │  │
│  │   (Kokoro INT8, ~95 MB)         │  │
│  │   via JNI / C++ native lib      │  │
│  └──────────────────────────────────┘  │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │   Audio Playback (MediaPlayer /  │  │
│  │   ExoPlayer with chunked queue)  │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────────┘

Model bundled in app assets (~95 MB)
or downloaded on first launch
```

### What You Can Drop

| Current Component | Status in New Architecture |
|---|---|
| FastAPI server | **Eliminated** |
| Docker / Dockerfile | **Eliminated** |
| JWT authentication | **Eliminated** (single-user device app) |
| CORS configuration | **Eliminated** |
| Client-server streaming protocol | **Eliminated** |
| SvelteKit PWA | **Replaced** by native Android app |
| Server deployment / VPS | **Eliminated** |
| MP3 encoding pipeline | **Simplified** (direct PCM/WAV playback, no transcoding needed) |
| Python dependency stack | **Eliminated** |

### What You Keep (ported to Kotlin/native)

| Component | How It Carries Over |
|---|---|
| Text preprocessing (abbreviations, numbers, chunking) | Port to Kotlin — relatively straightforward |
| Document extraction (PDF, DOCX) | Android has native PDF rendering; use Apache POI or similar for DOCX |
| Voice selection & speed control | Sherpa-ONNX API supports both |
| Audio player with sentence highlighting | Native Android MediaPlayer/ExoPlayer + timing metadata from TTS |

---

## 5. Alternative Approaches

### Option A: Sherpa-ONNX Native Kotlin App (Recommended)

- **Effort**: Medium — Sherpa-ONNX handles the hard part (inference), you build the UI and document processing
- **Quality**: Highest (Kokoro INT8 is near-reference quality)
- **Offline**: Fully offline after model download
- **Model size**: ~95 MB

### Option B: React Native with expo-kokoro-onnx

- **Effort**: Lower if you prefer JavaScript — existing open-source project to fork
- **Quality**: Same (Kokoro via ONNX Runtime)
- **Trade-off**: React Native adds ~10-15 MB bundle overhead; slightly less native feel

### Option C: Flutter with sherpa_onnx Package

- **Effort**: Medium — official `sherpa_onnx` Flutter package exists on pub.dev
- **Quality**: Same (Kokoro via Sherpa-ONNX)
- **Benefit**: Cross-platform (Android + iOS) from one codebase

### Option D: Hybrid — Keep PWA, Add Offline Mode

- **Effort**: Highest — would need to run ONNX in WebAssembly (possible via [Kokoro Web](https://kokoroweb.app/) approach)
- **Quality**: Same but WebAssembly inference is slower than native
- **Benefit**: Reuses your existing SvelteKit frontend

---

## 6. Known Challenges

### Phonemizer (Misaki) Porting

Kokoro uses the Misaki phonemizer, which depends on Python libraries (numpy, NLTK, spaCy). On mobile, this needs to be handled differently:

- **Sherpa-ONNX solves this** by using espeak-ng as the phonemizer backend (bundled as a native library)
- **NimbleEdge re-implemented** the phonemizer in C++ for their deployment
- **expo-kokoro-onnx** implemented a JavaScript-based tokenizer/phonemizer

If using Sherpa-ONNX, this is a non-issue — it's already handled.

### First-Launch Model Download

The ~95 MB model could be:
- **Bundled in the APK** (increases install size but works immediately)
- **Downloaded on first launch** (smaller APK, one-time download required)

For a personal-use app, bundling in the APK is simplest.

### Thermal Throttling

Generating audio for very long documents will keep the CPU busy. The Pixel 9 Pro aggressively throttles to manage thermals. In practice, this means sustained generation might slow down slightly over time, but the chunk-by-chunk playback approach naturally introduces idle periods between chunks that help with thermal management.

---

## 7. Conclusion

**The server is unnecessary.** Here's the bottom line:

| Factor | Server Architecture | On-Device Architecture |
|---|---|---|
| **Complexity** | FastAPI + Docker + JWT + CORS + streaming protocol + deployment | Single native app with embedded model |
| **Dependencies** | Python + Node.js + server hosting | Kotlin + Sherpa-ONNX |
| **Network requirement** | Always needs server connection | Fully offline |
| **Latency** | Network round-trip + server processing | Direct on-device, ~2-5s first-chunk delay |
| **Privacy** | Text sent to server | Everything stays on phone |
| **Cost** | Server hosting (VPS/cloud) | Free (runs on phone) |
| **Generation speed** | Faster (GPU: 35-210x real-time) | Slower but sufficient (~0.8x real-time) |
| **User experience** | Seamless streaming | Brief initial delay, then seamless |

For a personal-use mobile TTS app on a Pixel 9 Pro, the on-device approach is simpler, cheaper, more private, and fully sufficient in terms of quality and speed. The server architecture makes sense for multi-user deployments or when you need to support many concurrent requests — but for a single user on a modern flagship phone, it's over-engineering the problem.

**Recommended next step**: Build a proof-of-concept native Android app using Sherpa-ONNX + Kokoro INT8. Start with the [Sherpa-ONNX Android TTS Engine example](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTtsEngine) as a reference, add your document processing and UI on top.
