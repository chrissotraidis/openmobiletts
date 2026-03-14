# Expansion Plan: Open Mobile Voice v3.0

<aside>
<img src="https://www.notion.soi" alt="https://www.notion.soi" width="40px" />

**Context:** Open Mobile Voice (formerly Open Mobile TTS) is a production v2.0.0 text-to-speech application. TTS works via Sherpa-ONNX (Kokoro INT8) on Android and Kokoro (PyTorch) on desktop. The app uses a **WebView + NanoHTTPD + SvelteKit** architecture on Android and **FastAPI + SvelteKit** on desktop. This document is the comprehensive product and engineering plan for **v3.0** — expanding the app from a TTS tool into a bidirectional voice↔text platform with speech-to-text (Moonshine v2), document export, audio file import, and project management. Updated March 2026 based on extensive codebase review and product planning. For known risks, see the companion [Open Mobile Voice v3.0 — Risk & Limitations Assessment](https://www.notion.so/Open-Mobile-Voice-v3-0-Risk-Limitations-Assessment-000e5a7305e94285b5756d9356a4b581?pvs=21).

</aside>

---

# Executive Summary

Open Mobile Voice v3.0 transforms the app from a text-to-speech tool into a **bidirectional voice↔text platform**. Users can dictate speech to text, convert text to speech, import audio files for transcription, and export documents — all fully on-device on Android, with more powerful model options on desktop.

**What's new in v3.0:**

- **Speech-to-text** via Moonshine v2 (on-device Android, larger models on desktop)
- **Audio file import** — transcribe mp3, aac, ogg, and wav files
- **Document export** — save as PDF, Markdown, or plain text
- **Project-based storage** — JSON files with configurable auto-cleanup
- **Project metadata export** — backup/portability via JSON export in Settings
- **Unified input screen** — one scratchpad for both voice input and text input
- **Repository rename** — openmobiletts → openmobilevoice

**What's NOT in v3.0:**

- No LLM transcript correction (Moonshine accuracy is sufficient; removes ~500 MB model and llama.cpp dependency)
- No .docx export (complex on Android with no clean library; PDF covers the formal document use case)
- No system-wide keyboard overlay / InputMethodService (deferred to v4.0)
- No server-side anything on Android — fully on-device after model download

---

# What Exists Today (v2.0.0)

**One codebase. One SvelteKit frontend. Two backend targets.**

- **Android build** — Kotlin app. WebView loads the bundled SvelteKit build from an embedded NanoHTTPD server on 127.0.0.1:8080. On-device TTS via Sherpa-ONNX JNI (Kokoro INT8, ~95 MB). Fully local after model download. Generation RTF ~0.80 on flagships.
- **Desktop/web build** — FastAPI serves the same SvelteKit build as static files on port 8000. Server-side Kokoro TTS (PyTorch, FP32). ~35-210x real-time on GPU.

The frontend is **Svelte 5 + SvelteKit** with Tailwind CSS. Three tabs: Generate (text input + TTS), History (past generations), Settings (voice/speed/engine). The Android app has **no Jetpack Compose code**. All UI is in the SvelteKit app. Kotlin handles: TtsHttpServer (NanoHTTPD), TtsManager (Sherpa-ONNX wrapper), ModelDownloader, AacEncoder/WavEncoder (AAC primary, WAV fallback), TextPreprocessor, DocumentExtractor.

The app handles text-to-speech: paste or import text, generate audio, play with synchronized sentence highlighting. **v3.0 adds the reverse direction** (speech-to-text), document export, and audio file import.

---

# Product Vision

Open Mobile Voice is a **scratchpad for voice and text**. Open the app, and you see an empty canvas with two input methods: type/paste text, or tap the mic to dictate. The app doesn't force you to choose a mode — you interact naturally and decide what to do with the result afterward.

**Core user flows:**

1. **Dictate → Edit → Export**: Tap mic, speak, review transcript, export as PDF/MD/TXT
2. **Dictate → Edit → Listen**: Tap mic, speak, review transcript, generate TTS audio to hear it back
3. **Type/Paste → Listen**: Enter text, generate TTS audio (existing v2 flow, preserved)
4. **Import Audio → Transcribe → Export**: Upload an audio file (mp3/aac/ogg/wav), get transcript, export
5. **Import Document → Listen**: Upload PDF/TXT/MD, generate TTS audio (existing v2 flow, preserved)

**Design principles:**

- **Never server-side on Android.** All inference happens on-device. Network is only used for initial model download.
- **The desktop build gets better models.** Same UI, same features, but desktop users can run larger STT models (Moonshine v2 Medium, potentially Whisper Large v3) because they have the compute.
- **Projects are temporary.** Users keep projects for days or weeks, not years. Auto-cleanup keeps the app tidy.
- **Don't break what works.** The existing TTS flow is production-ready. v3 adds to it without modifying the core TTS path.

---

# Feature Specification

## 1. Speech-to-Text (Moonshine v2)

**What:** On-device speech recognition that converts voice input to text.

**STT Model: Moonshine v2 (not Whisper)**

Moonshine is a speech-to-text model family by Useful Sensors, purpose-built for edge/mobile devices. It is NOT a version of Whisper — it's a separate model architecture optimized for on-device inference. sherpa-onnx already supports Moonshine with Kotlin/Java API and pre-built Android libraries.

**Why Moonshine over Whisper:**

- sherpa-onnx's Whisper implementation has known accuracy regressions (GitHub issue #2900)
- whisper.cpp on Android has ongoing crash reports
- Moonshine v2 Small outperforms Whisper Small while being faster and smaller
- Moonshine is designed for exactly this use case — real-time edge inference

**Model options by platform:**

| **Model** | **Params** | **Size** | **Avg WER** | **Latency** | **Platform** |
| --- | --- | --- | --- | --- | --- |
| Moonshine v2 Tiny | 34M | ~26 MB | ~10% | ~50ms | Not used (quality too low) |
| **Moonshine v2 Small** | 123M | **~100 MB** | **7.84%** | **148ms** | **Android default** |
| **Moonshine v2 Medium** | 245M | **~250 MB** | **6.65%** | **258ms** | **Android optional, Desktop default** |
| Whisper Large v3 | 1.55B | ~3 GB | ~5% | ~2s | Desktop optional (future) |

**Android model strategy:**

- **Default download:** Moonshine v2 Small (~100 MB). Good accuracy (7.84% WER), fast (148ms latency). Works well on mid-range devices.
- **Optional download in Settings:** Moonshine v2 Medium (~250 MB). Better accuracy (6.65% WER), still fast (258ms). For users who want maximum quality and have the storage (Pixel 9 Pro has 16 GB RAM — Medium runs comfortably).

**Desktop model strategy:**

- **Default:** Moonshine v2 Medium. Desktop has the RAM and CPU to handle it easily.
- **Future option:** Whisper Large v3 for users who need maximum accuracy and have a GPU.

**Input methods:**

- **Live microphone dictation** (primary use case): Tap mic, speak, text appears. Uses batch transcription — record first, transcribe after. Streaming transcription is a future enhancement if Moonshine performance allows it.
- **Audio file import** (secondary use case): Upload mp3, aac, ogg, or wav file. App decodes the audio and runs it through Moonshine. Returns full transcript.

**Output:** Plain text in the text area. Immediately editable. Can be exported or sent to TTS.

## 2. Document Export

**What:** Save text (whether typed, pasted, or transcribed) as a document file.

**Supported export formats:**

- **PDF** — Android: `android.graphics.pdf.PdfDocument` (built-in API, zero dependencies). Desktop: ReportLab or similar Python library.
- **Markdown (.md)** — Trivial file write. Text content as-is.
- **Plain text (.txt)** — Trivial file write.

**Not supported (by design):**

- **.docx** — Android has no clean, lightweight library for this. Apache POI is bloated (~10 MB JAR). Manual XML construction is fragile. PDF covers the "formal document" use case. If users need .docx, they can export .md and convert externally.

**UX:** After text is in the text area (typed, pasted, or transcribed), user taps an "Export" button. A format picker appears (PDF / MD / TXT). File is saved to device storage or shared via Android share sheet.

## 3. Audio File Import

**What:** Upload an existing audio file and transcribe it using Moonshine.

**Supported formats:** mp3, aac, ogg, wav.

**Implementation:**

- Android: Use `MediaExtractor` + `MediaCodec` to decode any supported audio format to PCM. Feed PCM to Moonshine via SttManager.
- Desktop: Use ffmpeg (already a dependency) to decode to PCM. Feed to Moonshine via Python sherpa-onnx bindings.

**UX:** The existing "Upload Document" button expands to support both document files (PDF, TXT, MD for TTS) and audio files (mp3, aac, ogg, wav for STT). The app detects the file type and routes accordingly — document files go to text extraction (existing), audio files go to Moonshine transcription (new).

## 4. Project Storage

**What:** Persistent local storage for user sessions (text, transcriptions, generation history).

**Format:** JSON files. One folder per project.

```
/projects/
  /proj_20260309_143022/
    project.json        # metadata: title, created, modified, type
    content.txt         # the text content
    /audio/             # generated audio files (optional)
      generation_1.aac
  /proj_20260310_091545/
    project.json
    content.txt
```

**Why JSON over SQLite:**

- Portable — easy to backup, transfer, or inspect
- Human-readable — users can see what's stored
- Cross-platform — no Room/Android-specific abstractions (helps if we ever build iOS)
- Simple — for a project list of 10-20 items, JSON is plenty
- SQLite is built into Android, so we can migrate later if we need queries or indexing

**Auto-cleanup:** Configurable in Settings. Options: 1 week, 2 weeks, 1 month, 3 months, Never. Default: 1 month. App scans project folders on launch, deletes anything older than the configured threshold.

**Project metadata export:** In Settings, an "Export All Projects (JSON)" button dumps all project metadata (titles, dates, text content, generation settings) as a single JSON file. This is a data portability/backup feature, not a document format.

## 5. Unified Input Screen

**What:** A redesigned Generate tab that serves as the app's scratchpad.

**Current Generate tab:** Text area + Upload Document button + voice/language/speed selectors + Generate button.

**v3 Generate tab:** Same text area + Upload button (now supports documents AND audio files) + **mic button** (new) + voice/language/speed selectors + Generate Speech button (existing) + **Export button** (new).

**How the mic button works:**

1. User taps mic button.
2. App requests microphone permission (if not already granted).
3. Text area transforms into a recording view — shows a waveform or audio level indicator and a stop button.
4. User speaks. Audio is captured via WebView MediaRecorder API.
5. User taps stop.
6. Audio is POSTed to `/api/stt/transcribe` (NanoHTTPD on Android, FastAPI on desktop).
7. Moonshine transcribes. Progress indicator shown (reuses GenerationProgress.svelte pattern).
8. Transcribed text appears in the text area, fully editable.
9. From here, user can: edit the text, export it (PDF/MD/TXT), or generate speech from it.

**Navigation stays at three tabs:** Generate (expanded) | History (expanded) | Settings (expanded). No new tabs. The mic button and export button are additions to the existing Generate tab, not a separate mode.

## 6. History (Expanded)

**Current:** Shows TTS generation cards with title, voice, speed, date, and status (Audio ready / Text only).

**v3 additions:** History also shows STT transcription cards and document export cards.

**Card types:**

- **TTS generation** (existing): Title, voice, speed, date, "Audio ready" badge. Tapping opens detail view with Play/Queue/Download and full text.
- **STT transcription** (new): Title (first line of transcript), source (Mic / Imported audio file), original audio duration, date, "Transcribed" badge. Tapping opens detail view with full text, Export button, "Generate Speech" button (sends to TTS).
- **Document export** (new): Title, export format (PDF/MD/TXT), date, "Exported" badge.

**History is backed by the project storage system.** Each history item corresponds to a project folder.

## 7. Settings (Expanded)

**Current:** TTS Defaults (engine, voice, language, speed, auto-play), Server Connection, About, Export Logs.

**v3 additions:**

- **STT Defaults** (new section): STT Model (Moonshine v2 Small / Medium), with a download button for Medium if not yet downloaded. Language selection.
- **Storage** (new section): Auto-cleanup interval (1 week / 2 weeks / 1 month / 3 months / Never). Export All Projects (JSON) button. Storage usage display.
- **About** (updated): App name changes from "Open Mobile TTS" to "Open Mobile Voice". Version changes to 3.0.0.

---

# Architecture

## Android (v3.0)

```
Open Mobile Voice v3.0 — Android
(Kotlin backend + SvelteKit frontend via WebView)

┌───────────────────────────────────────────────────────┐
│                      WebView                            │
│  ┌───────────────────────────────────────────────────┐ │
│  │              SvelteKit Application                  │ │
│  │                                                     │ │
│  │  Generate Tab (expanded)  │ History │ Settings      │ │
│  │  ┌─────────────────────┐  │         │               │ │
│  │  │ Text Area            │  │         │ TTS Defaults  │ │
│  │  │ [Mic] [Upload] btns  │  │  TTS    │ STT Defaults  │ │
│  │  │ Voice/Speed selects  │  │  cards  │ Storage       │ │
│  │  │ [Generate] [Export]  │  │  STT    │ About         │ │
│  │  │                      │  │  cards  │               │ │
│  │  └─────────────────────┘  │         │               │ │
│  └────────────────────────────┴────┬────┴──────────────┘ │
│                                     │                     │
│                          HTTP to 127.0.0.1:8080           │
│                                     │                     │
│  ┌──────────────────────────────────▼──────────────────┐ │
│  │          NanoHTTPD (TtsHttpServer.kt)                 │ │
│  │                                                       │ │
│  │  EXISTING:                     NEW:                   │ │
│  │  /api/tts/stream               /api/stt/transcribe    │ │
│  │  /api/voices                   /api/stt/models        │ │
│  │  /api/documents/upload         /api/export/pdf        │ │
│  │  /api/documents/stream         /api/export/md         │ │
│  │  /api/engines                  /api/export/txt        │ │
│  │  /api/engine/*                 /api/projects          │ │
│  │  /api/health                   /api/projects/export   │ │
│  │  /api/logs/export                                     │ │
│  └──────────────┬──────────────────┬───────────────────┘ │
│                  │                  │                      │
│  ┌───────────┐  │  ┌──────────────┴──┐  ┌────────────┐  │
│  │TtsManager │  │  │  SttManager     │  │ExportManager│  │
│  │(existing) │  │  │  (NEW)          │  │(NEW)        │  │
│  │sherpa-onnx│  │  │  sherpa-onnx    │  │PdfDocument  │  │
│  │Kokoro TTS │  │  │  Moonshine STT  │  │File I/O     │  │
│  └───────────┘  │  └─────────────────┘  └────────────┘  │
│                  │                                        │
│  ┌──────────────┴──────────────────────────────────────┐ │
│  │     ModelManager (extended from ModelDownloader)      │ │
│  │     Manages: TTS model (~95MB) + STT model (~100MB)  │ │
│  │     Sequential download. Both can be loaded in RAM.  │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  ProjectStorage (NEW)                                 │ │
│  │  JSON files in /projects/. CRUD + auto-cleanup.       │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  AudioDecoder (NEW)                                   │ │
│  │  MediaExtractor + MediaCodec → PCM for imported audio │ │
│  └─────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────┘
```

**Key architectural decisions for v3.0:**

- **Both TTS and STT models loaded simultaneously.** Moonshine v2 Small is ~100 MB. Kokoro INT8 is ~95 MB. Total ~195 MB in RAM. A Pixel 9 Pro has 16 GB — this is fine. No model swapping needed, which eliminates the 2-5 second swap latency from the v2 plan.
- **No LLM.** Removes ~500 MB model download, llama.cpp JNI dependency, and the entire LlmManager class. Moonshine's 7.84% WER is good enough for dictation. Users can manually edit transcripts.
- **sherpa-onnx handles both TTS and STT.** Same framework, same .aar library, same Kotlin API pattern. SttManager mirrors TtsManager's structure. No new native dependencies.
- **All UI stays in SvelteKit.** New components (mic button, export picker, STT history cards) are Svelte components. No native Android UI added.
- **Dual build targets preserved.** Android = all local. Desktop = server-side with larger models.
- **Microphone access via WebView.** WebView's MediaRecorder API captures audio in JavaScript, POSTs to NanoHTTPD for transcription. Keeps the shared-UI philosophy. Falls back to native AudioRecord only if WebView capture proves unreliable.
- **Export uses built-in Android APIs.** `android.graphics.pdf.PdfDocument` for PDF (zero dependencies). File I/O for MD/TXT. No new libraries needed.

## Desktop/Web (v3.0)

```
Open Mobile Voice v3.0 — Desktop/Web

┌──────────────────────────────────────────────────────┐
│            Single Python Process (port 8000)            │
│  ┌──────────────────────────────────────────────────┐  │
│  │  FastAPI                                          │  │
│  │                                                   │  │
│  │  EXISTING:                  NEW:                  │  │
│  │  /api/tts/stream            /api/stt/transcribe   │  │
│  │  /api/voices                /api/stt/models       │  │
│  │  /api/documents/*           /api/export/pdf       │  │
│  │  /api/engines               /api/export/md        │  │
│  │  /api/engine/*              /api/export/txt       │  │
│  │  /api/health                /api/projects         │  │
│  │  /api/logs/export           /api/projects/export  │  │
│  │                                                   │  │
│  │  ┌──────────┐  ┌───────────┐  ┌──────────────┐  │  │
│  │  │ Kokoro   │  │ Moonshine │  │ Export       │  │  │
│  │  │ TTS      │  │ STT (NEW) │  │ Manager(NEW) │  │  │
│  │  │ (PyTorch)│  │ (sherpa)  │  │ ReportLab    │  │  │
│  │  └──────────┘  └───────────┘  └──────────────┘  │  │
│  │                                                   │  │
│  │  ┌──────────────────────────────────────────┐    │  │
│  │  │ Project Storage (NEW) - JSON files        │    │  │
│  │  └──────────────────────────────────────────┘    │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌────────────────────────────────────────────────┐    │
│  │  SvelteKit Static Build (same as Android)       │    │
│  └────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

**Desktop-specific notes:**

- STT runs server-side via Python sherpa-onnx bindings. Same API contract as Android NanoHTTPD endpoints.
- Default STT model: Moonshine v2 Medium (~250 MB). Desktop has the RAM for it.
- Future option: Whisper Large v3 for maximum accuracy (requires ~3 GB RAM + optional GPU).
- PDF export via ReportLab (Python library) instead of Android's PdfDocument API.
- Audio file decoding via ffmpeg (already a system dependency for the TTS pipeline).
- SvelteKit frontend is identical — same endpoints, same UI, same components. Zero code duplication.

---

# New API Endpoints

All new endpoints follow the same patterns as existing ones. No authentication. CORS allow-all. [Localhost](http://Localhost) only.

| **Endpoint** | **Method** | **Purpose** | **Request** | **Response** |
| --- | --- | --- | --- | --- |
| `/api/stt/transcribe` | POST | Transcribe audio to text | Audio data (WAV/PCM) as multipart | `{text, duration_ms, model}` |
| `/api/stt/models` | GET | List available STT models and download status | — | `{models: [{name, size, downloaded, active}]}` |
| `/api/stt/models/download` | POST | Download an STT model (e.g., Medium) | `{model: "moonshine-v2-medium"}` | `{status, progress}` |
| `/api/export/pdf` | POST | Export text as PDF | `{text, title}` | PDF file bytes |
| `/api/export/md` | POST | Export text as Markdown | `{text, title}` | `.md` file bytes |
| `/api/export/txt` | POST | Export text as plain text | `{text, title}` | `.txt` file bytes |
| `/api/projects` | GET | List all projects | — | `{projects: [{id, title, type, created, modified}]}` |
| `/api/projects` | POST | Create a new project | `{title, type, content}` | `{id, created}` |
| `/api/projects/:id` | GET | Get project details | — | `{id, title, type, content, created, modified}` |
| `/api/projects/:id` | PUT | Update project content | `{content, title}` | `{modified}` |
| `/api/projects/:id` | DELETE | Delete a project | — | `{deleted: true}` |
| `/api/projects/export` | GET | Export all project metadata as JSON | — | JSON file with all project data |
| `/api/projects/cleanup` | POST | Run auto-cleanup (delete old projects) | `{older_than_days: 30}` | `{deleted_count}` |

---

# New Kotlin Classes (Android)

## SttManager.kt

Mirrors TtsManager.kt's structure. Wraps sherpa-onnx's `OfflineRecognizer` for batch Moonshine transcription.

- Coroutine-based, Mutex-serialized, IO dispatcher (same pattern as TtsManager)
- Accepts PCM audio bytes, returns transcribed text string
- Initializes with Moonshine model files from cache directory
- Supports model hot-swap (Small ↔ Medium) via reinitialize

## AudioDecoder.kt

Decodes imported audio files (mp3, aac, ogg, wav) to PCM for Moonshine input.

- Uses Android's `MediaExtractor` to demux the container
- Uses `MediaCodec` to decode compressed audio to PCM
- Resamples to 16kHz mono (Moonshine's expected input format)
- Streams PCM chunks to avoid loading entire files into memory

## ExportManager.kt

Handles document export to PDF, MD, and TXT.

- **PDF:** Uses `android.graphics.pdf.PdfDocument` (built-in, zero dependencies). Creates pages with text content, basic formatting (title, body text, page numbers).
- **MD/TXT:** Simple file write with appropriate headers/formatting.
- Returns file bytes or saves to device storage via `MediaStore` or share intent.

## ProjectStorage.kt

CRUD operations on JSON project files.

- Each project is a folder in the app's files directory
- `project.json` contains metadata (id, title, type, created, modified, settings)
- `content.txt` contains the text content
- `audio/` subfolder contains any generated audio files
- Auto-cleanup: scans project folders, deletes those older than configured threshold
- Export: serializes all projects to a single JSON file

## ModelManager.kt (extended from ModelDownloader.kt)

Extended to manage multiple models (TTS + STT).

- Downloads TTS model (Kokoro INT8, ~95 MB) and STT model (Moonshine v2 Small, ~100 MB) from GitHub releases
- Optional download of Moonshine v2 Medium (~250 MB) triggered from Settings
- Progress UI for each download (extends existing download UI in MainActivity.kt)
- Both models can be loaded simultaneously in RAM (~195 MB total)

---

# New Python Modules (Desktop)

## stt_[engine.py](http://engine.py)

Moonshine STT via Python sherpa-onnx bindings.

- Creates sherpa-onnx `OfflineRecognizer` with Moonshine model
- Accepts PCM audio (from ffmpeg-decoded files or uploaded WAV), returns text
- Supports model selection (Medium default, Whisper Large v3 future option)

## export_[manager.py](http://manager.py)

Document export for the desktop build.

- **PDF:** ReportLab (new pip dependency) for PDF generation with basic formatting
- **MD/TXT:** Simple file write
- Returns file bytes as StreamingResponse

## project_[storage.py](http://storage.py)

Same JSON project storage as Android, implemented in Python.

- Same folder structure, same JSON schema
- Projects stored in a configurable directory (default: `~/.openmobilevoice/projects/`)
- Auto-cleanup, export, CRUD — same API contract as Android

---

# New SvelteKit Components

## MicButton.svelte

Floating mic button on the Generate tab. Handles the full recording flow.

- Requests microphone permission via WebView
- Uses MediaRecorder API to capture audio as WAV blob
- Shows recording state: waveform visualization or audio level indicator, stop button
- On stop: POSTs audio to `/api/stt/transcribe`, shows progress, populates text area with result

## ExportPicker.svelte

Format selection UI for document export.

- Appears when user taps Export button
- Three options: PDF, Markdown, Plain Text
- Sends text to appropriate `/api/export/*` endpoint
- Triggers file download or Android share intent

## AudioFileUpload.svelte (extension of existing upload)

Extends the existing document upload to support audio files.

- File picker accepts both document formats (pdf, docx, txt, md) AND audio formats (mp3, aac, ogg, wav)
- Detects file type by extension
- Documents → existing text extraction pipeline (TTS path)
- Audio files → new STT transcription pipeline (routes to `/api/stt/transcribe`)

## SttHistoryCard.svelte

History card variant for STT transcriptions.

- Shows: title (first line), source icon (mic or file), original audio duration, date, "Transcribed" badge
- Detail view: full text, Export button, "Generate Speech" button, Edit button

## StorageSettings.svelte

New settings section for project management.

- Auto-cleanup interval selector
- Storage usage display
- Export All Projects button
- STT model selector (Small / Medium) with download button for Medium

## SttProgress.svelte

Progress indicator for STT transcription.

- Reuses pattern from GenerationProgress.svelte
- Shows "Transcribing..." with estimated time based on audio duration

---

# Implementation Plan

## Phase 1: Foundation (2-3 days)

**Goal:** Moonshine STT working end-to-end on Android.

**Step 1.1: SttManager.kt**

- Create SttManager mirroring TtsManager's structure
- Initialize sherpa-onnx OfflineRecognizer with Moonshine v2 Small
- Accept PCM audio bytes, return transcribed text
- Mutex-serialized, coroutine-based (same pattern as TtsManager)
- Unit test: feed known audio, verify transcript

**Step 1.2: ModelManager extension**

- Extend ModelDownloader.kt to download Moonshine model from GitHub releases
- Add model detection: check if STT model exists in cache on launch
- First-launch flow: download TTS model (~95 MB) then STT model (~100 MB)
- Progress UI for both downloads

**Step 1.3: NanoHTTPD STT endpoints**

- Add `/api/stt/transcribe` to TtsHttpServer.kt
- Add `/api/stt/models` for model status
- Follow same patterns as existing `/api/tts/stream` handler

**Step 1.4: WebView microphone permission**

- Override `onPermissionRequest` in WebChromeClient (in MainActivity.kt)
- Add `RECORD_AUDIO` to AndroidManifest.xml
- Handle Android runtime permission request flow
- Test MediaRecorder API works within WebView

## Phase 2: STT UI + Audio Import (2-3 days)

**Goal:** Users can dictate and import audio files for transcription.

**Step 2.1: MicButton.svelte**

- Add mic button to Generate tab
- MediaRecorder API for audio capture
- Recording state UI (waveform/level indicator, stop button)
- POST audio to `/api/stt/transcribe`
- Populate text area with result

**Step 2.2: AudioDecoder.kt**

- Decode mp3/aac/ogg/wav to PCM using MediaExtractor + MediaCodec
- Resample to 16kHz mono for Moonshine
- Stream chunks to avoid memory issues on large files

**Step 2.3: Audio file upload**

- Extend Upload Document button to accept audio files
- Detect file type, route to STT pipeline
- Progress indicator during transcription

**Step 2.4: History expansion**

- Add STT transcription cards to history
- New card type with source, duration, "Transcribed" badge
- Detail view with full text + Export + Generate Speech buttons

## Phase 3: Export + Projects (1-2 days)

**Goal:** Document export and project storage working.

**Step 3.1: ExportManager.kt**

- PDF export via [android.graphics](http://android.graphics).pdf.PdfDocument
- MD and TXT export via file I/O
- Android share intent for file sharing

**Step 3.2: ExportPicker.svelte**

- Export button on Generate tab
- Format picker (PDF / MD / TXT)
- File download or share

**Step 3.3: ProjectStorage.kt**

- JSON project storage with CRUD operations
- Auto-cleanup on app launch
- Project metadata export as JSON

**Step 3.4: StorageSettings.svelte**

- Auto-cleanup interval selector
- Storage usage display
- Export All Projects button
- STT model selector with download option

## Phase 4: Desktop/Web Parity (1-2 days)

**Goal:** Desktop build gets the same STT and export features.

**Step 4.1: stt_[engine.py](http://engine.py)**

- Moonshine STT via Python sherpa-onnx bindings
- FastAPI endpoints matching NanoHTTPD contract

**Step 4.2: export_[manager.py](http://manager.py)**

- PDF export via ReportLab
- MD/TXT export

**Step 4.3: project_[storage.py](http://storage.py)**

- JSON project storage in Python
- Same folder structure as Android

**Step 4.4: [run.py](http://run.py) updates**

- Download Moonshine model on first run (if not present)
- Add ReportLab to requirements.txt

## Phase 5: Polish + Testing (1-2 days)

**Goal:** Everything works reliably, edge cases handled.

**Step 5.1: Testing**

- STT accuracy testing on Pixel 9 Pro with various audio conditions
- Export format verification (PDF renders correctly, MD is valid)
- Audio file import with various formats and durations
- Project storage CRUD and auto-cleanup
- Memory profiling with both models loaded

**Step 5.2: UI polish**

- Consistent loading/progress states across all new features
- Error handling for: mic permission denied, STT model not downloaded, export failure, corrupt audio file
- Smooth transitions between STT → edit → export flow

**Step 5.3: Repository rename**

- Rename GitHub repo from openmobiletts to openmobilevoice
- Update all hardcoded GitHub URLs (ModelDownloader.kt, README, docs)
- Update app name in About section, AndroidManifest.xml, build.gradle
- Update documentation (README, docs/ folder, CHANGELOG)
- Cut new GitHub release under new repo name
- Verify GitHub redirect works for old URLs

**Step 5.4: Documentation**

- Update README for v3.0 features
- Update CHANGELOG
- Update [ROADMAP.md](http://ROADMAP.md)
- Update docs/ technical architecture files

---

# Timeline Summary

<aside>
<img src="https://www.notion.soi" alt="https://www.notion.soi" width="40px" />

**Estimated total: 7-12 days.** This is a larger scope than the original expansion plan because it includes document export, audio file import, project storage, and desktop parity — not just STT. The removal of LLM (llama.cpp + Qwen) saves approximately 2-3 days of work.

</aside>

| **Phase** | **Estimate** | **What** |
| --- | --- | --- |
| Phase 1: Foundation | 2-3 days | SttManager, model download, NanoHTTPD endpoints, mic permission |
| Phase 2: STT UI + Audio Import | 2-3 days | Mic button, audio decoder, file import, history expansion |
| Phase 3: Export + Projects | 1-2 days | PDF/MD/TXT export, project storage, settings |
| Phase 4: Desktop Parity | 1-2 days | Python STT engine, export, project storage |
| Phase 5: Polish + Testing | 1-2 days | Testing, UI polish, repo rename, docs |

---

# Storage Budget

| **Component** | **Size** | **When** |
| --- | --- | --- |
| APK | ~50 MB | Install |
| TTS model (Kokoro INT8) | ~95 MB | First launch |
| STT model (Moonshine v2 Small) | ~100 MB | First launch |
| STT model (Moonshine v2 Medium, optional) | ~250 MB | User downloads in Settings |
| Project data | Variable | Ongoing (auto-cleaned) |
| **Total (default)** | **~245 MB** |  |
| **Total (with Medium STT)** | **~495 MB** |  |

Compared to v2 plan (which included TTS 95 MB + Whisper 74-244 MB + Qwen LLM ~500 MB = up to ~840 MB), v3 is **significantly lighter** by removing the LLM.

---

# Open Questions

1. **Batch vs. streaming STT:** v3 starts with batch transcription (record first, transcribe after). Streaming (text appears as you speak) is more impressive but harder to implement reliably. Test batch first; add streaming as a v3.1 enhancement if latency is acceptable.
2. **Concurrent model loading:** The plan assumes both TTS (~95 MB) and STT (~100 MB) models can be loaded simultaneously. On a Pixel 9 Pro with 16 GB RAM, this is fine. On devices with 4-6 GB RAM, we may need to fall back to sequential loading with model swapping. Test on lower-end devices.
3. **Audio file size limits:** For imported audio files, what's the max duration? A 1-hour podcast at 128kbps is ~60 MB of audio, producing ~15 minutes of Moonshine processing time on-device. Probably need a warning for files over 10 minutes and a hard limit at 30-60 minutes.
4. **PDF export quality:** Android's built-in PdfDocument API is basic — it gives you a Canvas to draw on. For clean PDF output (title, paragraphs, page numbers), we'll need to write a basic text layout engine. ReportLab on desktop is more capable. The two outputs may look slightly different.
5. **Moonshine model hosting:** Currently TTS models are hosted on GitHub releases. STT models need hosting too. Options: same GitHub releases page (simple), or a separate releases repo to keep file counts manageable.

---

# First Action

<aside>
<img src="https://www.notion.soi" alt="https://www.notion.soi" width="40px" />

**Test Moonshine v2 via sherpa-onnx on the Pixel 9 Pro.** sherpa-onnx has pre-built STT example APKs that include Moonshine. Download one, record a minute of speech, check the transcript quality and timing. This confirms the integration path before writing any code. Test both Small and Medium to feel the quality difference firsthand.

</aside>

If both work well: proceed with Phase 1 (SttManager.kt + model download).

If Small quality is poor: default to Medium instead.

If Moonshine crashes: check sherpa-onnx GitHub issues, test with a different model version, fall back to Whisper as last resort.

---

# Future (v4.0 and beyond)

Features explicitly deferred from v3.0:

- **System-wide keyboard overlay / InputMethodService** — Allows users to dictate into any text field on Android via a floating bubble or custom keyboard. Requires AccessibilityService or InputMethodService, both complex. Google has been cracking down on non-accessibility uses of AccessibilityService. An InputMethodService is more legitimate but is essentially building a second app. Deferred until the core app proves itself.
- **Streaming STT** — Text appears in real-time as the user speaks. Requires sherpa-onnx OnlineRecognizer (streaming mode) instead of OfflineRecognizer (batch mode). Depends on Moonshine's streaming performance.
- **.docx export** — If user demand warrants it. Would require Apache POI (bloated) or manual XML construction (fragile).
- **iOS build** — JSON project storage was chosen partly to avoid Room/Android lock-in. If iOS becomes a target, the SvelteKit frontend reuses directly; only the native backend (currently Kotlin) would need a Swift equivalent.
- **Cloud sync** — Intentionally omitted. All data is local. Could add optional sync via user-provided storage (iCloud, Google Drive) in the future.
- **Whisper Large v3 on desktop** — For users who need maximum STT accuracy and have a GPU. Lower priority since Moonshine Medium is already very good.