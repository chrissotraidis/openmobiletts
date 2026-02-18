# Implementation Plan: On-Device Android TTS App (v2)

## Overview

Build a standalone native Android app that runs Kokoro TTS entirely on-device using Sherpa-ONNX. No server, no network, no authentication. This is the **v2** goal.

The desktop web app (SvelteKit + FastAPI) remains available for computer users. v2 adds a native Android app alongside it — it does not replace the desktop version.

**Tech stack:** Kotlin, Jetpack Compose, Sherpa-ONNX (Kokoro INT8), AudioTrack, Room DB

---

## Phase 1: Project Scaffolding & TTS Engine Integration

The foundation — get Kokoro generating audio on the phone.

### 1.1 Create Android Project

- New Android project: `com.openmobiletts.app`
- Min SDK 26 (Android 8.0 — covers 95%+ of active devices, required for some AudioTrack features)
- Target SDK 34
- Kotlin + Jetpack Compose + Material 3
- Build system: Gradle with Kotlin DSL

**Project structure:**

```
android/
  app/
    src/main/
      java/com/openmobiletts/app/
        MainActivity.kt
        App.kt                          # Application class
        tts/
          TtsManager.kt                 # Sherpa-ONNX wrapper
          TtsChunker.kt                 # Text → chunks for TTS
        audio/
          AudioPlaybackManager.kt       # AudioTrack streaming playback
        document/
          DocumentProcessor.kt          # PDF/DOCX/TXT extraction
        text/
          TextPreprocessor.kt           # Abbreviations, numbers, cleanup
        data/
          AppDatabase.kt               # Room database
          HistoryDao.kt
          HistoryEntry.kt              # Entity
          SettingsRepository.kt        # DataStore preferences
        ui/
          theme/
            Theme.kt                   # Dark theme, colors, typography
          screens/
            GenerateScreen.kt          # Text input + generate
            HistoryScreen.kt           # Past generations
            SettingsScreen.kt          # Voice, speed, preferences
          components/
            PlayerBar.kt              # Bottom audio player bar
            VoiceSelector.kt
            SpeedSlider.kt
        navigation/
          AppNavigation.kt            # Bottom nav + routes
      assets/
        kokoro-int8/                   # Model files (downloaded or bundled)
          model.int8.onnx
          voices.bin
          tokens.txt
          espeak-ng-data/
      res/
        ...
    build.gradle.kts
  build.gradle.kts
  settings.gradle.kts
```

### 1.2 Integrate Sherpa-ONNX Native Libraries

- Download pre-built `sherpa-onnx-*-android.tar.bz2` from [GitHub Releases](https://github.com/k2-fsa/sherpa-onnx/releases)
- Extract `arm64-v8a/` JNI libs (`libsherpa-onnx-jni.so`, `libonnxruntime.so`) into `app/src/main/jniLibs/arm64-v8a/`
- Copy `Tts.kt` from `sherpa-onnx/kotlin-api/Tts.kt` into the project (the Kotlin API wrapper)
- Verify native library loads with `System.loadLibrary("sherpa-onnx-jni")`

### 1.3 Bundle Kokoro Model

- Download Kokoro INT8 ONNX model files (~92 MB model + ~5.5 MB voices + tokens.txt + espeak-ng-data)
- Place in `app/src/main/assets/kokoro-int8/`
- Total addition to APK: ~100 MB

**Alternative:** Download model on first launch to keep APK smaller. For a personal-use app, bundling is simpler.

### 1.4 Build TtsManager

Wrapper around Sherpa-ONNX that handles initialization and generation:

```kotlin
// tts/TtsManager.kt
class TtsManager(private val context: Context) {
    private var tts: OfflineTts? = null

    fun init() {
        // Copy espeak-ng-data from assets to filesystem (required by espeak-ng C library)
        val dataDir = copyAssetsToFilesystem(context, "kokoro-int8/espeak-ng-data")

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "kokoro-int8/model.int8.onnx",
                    voices = "kokoro-int8/voices.bin",
                    tokens = "kokoro-int8/tokens.txt",
                    dataDir = dataDir,
                ),
                numThreads = 4,  // use performance cores
            ),
            maxNumSentences = 1,  // stream sentence by sentence
        )

        tts = OfflineTts(assetManager = context.assets, config = config)
    }

    fun sampleRate(): Int = tts!!.sampleRate()
    fun numSpeakers(): Int = tts!!.numSpeakers()

    // Streaming generation — calls back with audio chunks
    fun generateStreaming(
        text: String,
        speakerId: Int,
        speed: Float,
        onChunk: (FloatArray) -> Unit,
        shouldStop: () -> Boolean
    ) {
        tts!!.generateWithCallback(text, speakerId, speed) { samples ->
            onChunk(samples)
            if (shouldStop()) 0 else 1
        }
    }

    fun release() { tts?.release(); tts = null }
}
```

### 1.5 Milestone: "Hello World" Audio

- Wire a single button to call `tts.generate("Hello world", sid=0, speed=1.0f)`
- Play the resulting `FloatArray` through `AudioTrack`
- Confirm Kokoro produces audio on the Pixel 9 Pro
- Measure time-to-first-audio and total generation time

---

## Phase 2: Audio Playback Engine

Streaming playback that starts as soon as the first chunk is ready.

### 2.1 Build AudioPlaybackManager

```kotlin
// audio/AudioPlaybackManager.kt
class AudioPlaybackManager {
    private var audioTrack: AudioTrack? = null
    private val sampleChannel = Channel<FloatArray>(capacity = 64)

    // Lifecycle
    fun init(sampleRate: Int) { /* create AudioTrack in MODE_STREAM */ }
    fun release() { /* stop and release AudioTrack */ }

    // Playback control
    fun play()
    fun pause()
    fun stop()
    fun isPlaying(): Boolean

    // Feed audio data (called from TTS generation coroutine)
    suspend fun feedSamples(samples: FloatArray)

    // Internal: consumer coroutine that reads from channel and writes to AudioTrack
    private fun startPlaybackLoop(scope: CoroutineScope)
}
```

**Key design:**
- Producer coroutine: runs TTS generation, pushes `FloatArray` chunks to a `Channel`
- Consumer coroutine: reads from channel, writes to `AudioTrack` (WRITE_BLOCKING)
- This decouples generation speed from playback speed
- `AudioTrack` uses `ENCODING_PCM_FLOAT`, mono, at model's sample rate (22050 Hz)
- Supports pause/resume by pausing the AudioTrack

### 2.2 Background Audio Support

- Use a foreground `Service` with a persistent notification for audio playback
- Implement `MediaSession` for lock screen controls (play/pause/stop)
- Audio focus handling (`AudioManager.requestAudioFocus`)
- This gives Android-native background playback — the key advantage over the PWA approach

### 2.3 Playback State Management

- Expose playback state as Compose `State`: idle / generating / playing / paused / error
- Track current position (samples played / total samples) for progress bar
- Track which text chunk is currently being spoken (for highlighting)

---

## Phase 3: Text Processing Pipeline

Port the server's text preprocessing and chunking logic to Kotlin.

### 3.1 TextPreprocessor

Port from `server/src/text_preprocessor.py`:

```kotlin
// text/TextPreprocessor.kt
class TextPreprocessor {
    fun preprocess(text: String): String {
        return text
            .normalizeUnicode()          // NFKC normalization (java.text.Normalizer)
            .removePdfArtifacts()        // strip page numbers, excessive whitespace
            .expandAbbreviations()       // Dr. → Doctor, etc. (regex map)
            .convertNumbersToWords()     // 5 → five (use ICU4J or simple Kotlin impl)
            .cleanWhitespace()           // collapse multiple spaces/newlines
    }
}
```

**Key porting notes:**
- Unicode normalization: `java.text.Normalizer.normalize(text, Normalizer.Form.NFKC)`
- Abbreviation expansion: direct port of the 36 regex patterns
- Number-to-words: Use Android's built-in `ICU4J` (available since API 24) or port the simple num2words logic for 1-4 digit numbers
- All regex patterns port 1:1 from Python to Kotlin

### 3.2 TtsChunker

Port sentence splitting and chunk grouping from `text_preprocessor.py`:

```kotlin
// tts/TtsChunker.kt
class TtsChunker(private val maxTokens: Int = 250) {
    fun chunkText(text: String): List<TextChunk> {
        val sentences = splitSentences(text)
        return groupIntoChunks(sentences, maxTokens)
    }

    // Returns chunks with text and character offsets (for highlighting)
    data class TextChunk(
        val text: String,
        val startOffset: Int,  // character offset in original text
        val endOffset: Int,
    )
}
```

- Split at sentence boundaries (`.!?` followed by whitespace)
- Group sentences into chunks ≤ 250 tokens (~4 chars = 1 token)
- Preserve character offsets for text highlighting during playback

---

## Phase 4: Document Processing

### 4.1 PDF Extraction

- Use **Android's built-in `PdfRenderer`** for basic text extraction, or
- Use **Apache PDFBox for Android** (`com.tom-roush:pdfbox-android`) for better multi-column text extraction
- Strip markdown/formatting artifacts (same cleanup as the Python version)

### 4.2 DOCX Extraction

- Use **Apache POI** (`org.apache.poi:poi-ooxml`) for DOCX paragraph extraction
- Extract paragraph text, skip images/tables

### 4.3 TXT Support

- Direct file reading with charset detection

### 4.4 File Picker Integration

- Use Android's `ActivityResultContracts.OpenDocument` for file selection
- Support MIME types: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`
- Read file via `ContentResolver.openInputStream(uri)`

---

## Phase 5: UI — Core Screens

Dark theme (matching the current design), Material 3, Jetpack Compose.

### 5.1 App Theme

- Dark background: `#0a0c10` (matching current PWA)
- Blue accent gradient
- Material 3 dark color scheme
- Monospace/system fonts

### 5.2 Navigation Shell

- `Scaffold` with bottom `NavigationBar` (3 tabs: Generate, History, Settings)
- Top `TopAppBar` with app name
- Content area switches between screens

### 5.3 Generate Screen

The main screen. Two sub-states: **input mode** and **playback mode**.

**Input mode:**
- Large text input field (multiline `TextField`)
- "Upload Document" button (opens file picker)
- "Generate" button
- Voice selector dropdown (shows available Kokoro voices)
- Speed selector (slider, 0.5x–2.0x)

**Playback mode** (after generation starts):
- Full text displayed with **sentence-level highlighting** (current sentence highlighted as audio plays)
- Player controls at the bottom (see PlayerBar below)
- "Stop" button to cancel generation

### 5.4 PlayerBar Component

Sticky bottom bar visible during/after generation:

- Play/Pause button
- Stop button
- Progress bar (time elapsed / estimated total)
- Current voice name
- Speed indicator

### 5.5 History Screen

- `LazyColumn` list of past generations
- Each entry: first ~50 chars of text, voice used, date, duration
- Tap to replay (audio saved to local storage)
- Swipe to delete
- Stored in Room database

### 5.6 Settings Screen

- **Default Voice**: dropdown with all Kokoro voice options
- **Default Speed**: slider (0.5x–2.0x, step 0.1x)
- **Auto-play**: toggle switch
- **About section**: engine info, model version, app version
- Persisted via Jetpack DataStore

---

## Phase 6: Sentence Highlighting & Timing

The key UX feature — highlight the sentence being spoken.

### 6.1 Timing Strategy

Since Sherpa-ONNX's callback delivers audio chunk by chunk (roughly sentence by sentence when `maxNumSentences = 1`), timing can be derived from the chunks:

1. Before generation, split text into chunks using `TtsChunker` (preserving character offsets)
2. As each chunk generates, calculate its duration: `samples.size / sampleRate` seconds
3. Maintain a cumulative timeline: chunk N starts at the sum of all prior chunk durations
4. As `AudioTrack` plays, query `audioTrack.playbackHeadPosition` to get current sample position
5. Map current sample position → which chunk is active → highlight that sentence in the UI

### 6.2 UI Implementation

- Display full text in a scrollable `Text` composable (or `AnnotatedString` with `SpanStyle`)
- Active sentence gets a highlight background color
- Auto-scroll to keep the active sentence visible
- When playback pauses, highlighting pauses too

---

## Phase 7: Data Persistence

### 7.1 Room Database

```kotlin
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,           // original input text
    val voiceId: Int,           // speaker ID
    val voiceName: String,      // display name
    val speed: Float,
    val createdAt: Long,        // timestamp
    val durationSeconds: Float,
    val audioFilePath: String,  // path to saved WAV file
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun getAll(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Delete
    suspend fun delete(entry: HistoryEntry)
}
```

### 7.2 Audio File Storage

- Save generated audio as WAV files to app-internal storage (`context.filesDir`)
- Use `GeneratedAudio.save()` from Sherpa-ONNX for WAV writing
- Clean up old files when history entries are deleted
- For long documents, save individual chunks and a manifest file

### 7.3 Settings Persistence

- Jetpack DataStore (Preferences) for: default voice, default speed, auto-play toggle
- Expose as Kotlin `Flow` for reactive UI updates

---

## Phase 8: Polish & Production Readiness

### 8.1 Generation Pipeline Orchestration

The complete flow when the user taps "Generate":

```
1. User enters text (or uploads document)
2. TextPreprocessor.preprocess(text)
3. TtsChunker.chunkText(preprocessedText) → List<TextChunk>
4. For each chunk:
   a. TtsManager.generateStreaming(chunk.text, voiceId, speed) → FloatArray callbacks
   b. AudioPlaybackManager.feedSamples(samples)
   c. Update timing metadata (which chunk, cumulative position)
5. AudioTrack plays samples as they arrive
6. UI highlights current sentence based on playback position
7. When complete, save audio + metadata to history
```

### 8.2 Error Handling

- Model loading failure → clear error message, option to retry
- Out of memory → unlikely with 95 MB model on 16 GB RAM, but handle gracefully
- Generation failure → stop playback, show error
- File parsing failure → show error with supported format list

### 8.3 Performance Optimizations

- Initialize TTS engine at app startup (model loading takes a few seconds)
- Use 4 threads for inference (utilizes performance cores on Tensor G4)
- Pre-chunk all text before starting generation (avoid pauses between chunks)
- Buffer 2-3 chunks ahead of playback position

### 8.4 Notifications & Media Controls

- Foreground service notification during playback
- `MediaSession` integration for:
  - Lock screen controls
  - Bluetooth headset play/pause
  - Google Assistant "pause" voice command
- Audio focus: duck/pause other media when TTS plays

### 8.5 App Icon & Splash Screen

- Use the existing PWA icons (`icon-192.png`, `icon-512.png`) as adaptive icon sources
- Splash screen via `SplashScreen` API (Android 12+)

---

## Phase 9: Cleanup & Coexistence

The desktop web app (server/ + client/) is **kept** for computer users. The native Android app lives alongside it.

| Directory/File | Action |
|---|---|
| `server/` | **Keep** — desktop users still use the web app |
| `client/` | **Keep** — desktop web UI + Capacitor fallback |
| `client/android/` (Capacitor) | **Remove** — replaced by native `android/` |
| `android/` (new, top-level) | **New** — native Kotlin app |
| `docs/` | **Update** — add native Android docs, update architecture |
| `README.md` | **Update** — document both desktop and Android paths |

---

## Dependency Summary

| Dependency | Purpose | Size Impact |
|---|---|---|
| Sherpa-ONNX native libs | TTS inference | ~15 MB (arm64-v8a JNI libs) |
| Kokoro INT8 model + voices | TTS model | ~100 MB (in assets) |
| Jetpack Compose + Material 3 | UI framework | ~5 MB |
| Room | Local database | ~1 MB |
| DataStore | Settings persistence | <1 MB |
| Apache PDFBox Android | PDF text extraction | ~3 MB |
| Apache POI | DOCX text extraction | ~5 MB |
| **Total APK size** | | **~130–140 MB** |

For comparison: Spotify is ~120 MB, Google Maps is ~150 MB. This is a normal app size.

---

## Implementation Order & Estimated Phases

| Phase | What | Depends On |
|---|---|---|
| **1** | Project scaffolding + Sherpa-ONNX integration + "Hello World" audio | Nothing |
| **2** | Streaming audio playback engine | Phase 1 |
| **3** | Text preprocessing + chunking (port from Python) | Nothing (can parallel with 2) |
| **4** | Document processing (PDF/DOCX/TXT) | Nothing (can parallel with 2-3) |
| **5** | UI screens (Generate, History, Settings) | Phase 2 |
| **6** | Sentence highlighting + timing sync | Phase 2, 3, 5 |
| **7** | Data persistence (Room + file storage) | Phase 5 |
| **8** | Polish (notifications, media controls, error handling) | Phase 5, 6, 7 |
| **9** | Cleanup (remove server/client dirs, update docs) | Phase 8 |

Phases 1-4 can be partially parallelized. The critical path is: **1 → 2 → 5 → 6 → 8**.
