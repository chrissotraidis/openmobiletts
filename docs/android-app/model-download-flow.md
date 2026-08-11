# Android Model Download Flow

## First launch

1. `MainActivity` checks the app-private model directory.
2. If Kokoro TTS is missing, it shows one explicit **Download Voice Model
   (333.2 MB)** action.
3. The app checks space for the TTS archive, staged extraction, and installed
   candidate.
4. A unique WorkManager job starts with a connected-network constraint and a
   foreground data-sync notification.
5. Progress survives Activity recreation. Transient I/O failures use
   exponential backoff.
6. The downloader validates response metadata, archive size, SHA-256, safe
   paths, and required non-empty files.
7. TTS packages are installed after structural and integrity validation without
   creating a temporary native engine in the download process. STT separately
   initializes its staged recognizer.
8. The candidate is activated without deleting the previous working model.
9. The WebView opens after TTS is available.

Speech-to-text is not part of first launch. It is an optional 239.2 MiB model
requested later from Settings.

## Pause, resume, and process death

- Pausing cancels the unique work and retains a valid `.part` archive under
  app-private `.model-downloads/` storage.
- Retrying enqueues the same model's unique work and requests the remaining
  bytes with HTTP Range.
- If the host returns a full response, the downloader overwrites the partial
  file instead of appending incompatible data.
- Process or Activity loss does not make the Activity own the transfer state;
  the UI observes WorkInfo on return.
- A checksum failure removes the invalid partial archive. Network interruption
  and user cancellation do not.

## Optional STT download

Settings reports the exact Moonshine v1 Base English INT8 model, installed and
archive sizes, readiness, durable progress, and errors. Download/retry starts
WorkManager; Pause cancels the current work. The model initializes lazily when
transcription starts.

## Experimental TTS models

After Kokoro first-run setup, Android Models settings lists Kitten Mini v0.8
and Kitten Micro v0.8 as optional English-only experiments. Each uses its own
unique WorkManager job and the same resume, hash, extraction, and required-file
checks as Kokoro. A successful download does not silently change the active
voice model.

**Use this model** persists the installed candidate and restarts Android into a
clean process before loading it. The eight Kitten voices are then exposed to
Voice settings. Users can remove an inactive experimental model, but cannot
remove the active model or the required Kokoro fallback.

## Current model sizes

| Role | Archive | Installed planning size | Required timing |
|---|---:|---:|---|
| Kokoro TTS | 333.2 MiB | up to 384 MiB reserved | First use |
| Kitten Mini v0.8 TTS | 64.4 MiB | 94.9 MiB | Optional from Settings |
| Kitten Micro v0.8 TTS | 42.4 MiB | 59.8 MiB | Optional from Settings |
| Moonshine v1 Base STT | 239.2 MiB | 273.6 MiB | Optional from Settings |

## Acceptance status

- [x] First run requires only TTS.
- [x] Unique foreground WorkManager jobs, network constraint, progress, retry,
  pause, and partial resume are implemented.
- [x] Archive size/hash, storage preflight, safe extraction, required-file
  validation, staging, and rollback activation are implemented.
- [x] TTS download validation avoids temporary native engines; model changes
  use a clean process restart. STT retains staged native load validation.
- [x] Kitten Mini and Micro download, validate, activate, generate normal
  24 kHz audio, return to Kokoro, and persist selection on the API 34 ARM64
  emulator.
- [x] Physical Pixel 9 Pro XL clean-process generation passed for Kokoro,
  Kitten Mini, Kitten Micro, and Kokoro again after the complete model cycle.
- [x] The Models button automatically relaunched the emulator in a new process,
  showed a branded selected-model transition, and restored the active model
  card with matching voices and an **Active and ready** confirmation.
- [x] Android strict Kotlin compilation passes with WorkManager 2.10.5.
- [ ] A fresh required Kokoro first-run download passes on a physical phone.
- [ ] Pause/resume, process death, retry, and notification behavior pass on a
  physical phone and emulator.
- [ ] Metered-network preference and model repair/delete controls are designed.

See decisions [015](../decisions/015-workmanager-model-delivery-and-tts-first-start.md),
[020](../decisions/020-experimental-android-kitten-models.md), and
[021](../decisions/021-process-isolated-android-tts-switching.md), plus the
[model provenance ledger](../MODEL_PROVENANCE.md).
