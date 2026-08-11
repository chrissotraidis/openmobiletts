# Dictation Flow

## Happy path

1. The user opens Generate and taps the microphone action.
2. The browser or Android WebView requests microphone permission when needed.
3. Recording begins and the interface shows an active recording state.
4. The user stops recording.
5. The client uploads the captured audio to `/api/stt/transcribe` on the local
   FastAPI or NanoHTTPD server.
6. Moonshine v1 Base English INT8 performs batch transcription.
7. The returned text is inserted into the editable text area.
8. The user can correct, export, or synthesize the text.

No cloud request or LLM correction is part of this flow.

## Optional model setup

If the STT model is absent, Settings shows the exact 239.2 MiB download instead
of blocking Android first run. Desktop starts a checksum-pinned background
install. Android enqueues unique WorkManager work with a notification,
progress, retry, pause, and partial-file resume when supported. The mic remains
unavailable until the required files pass validation.

## Failure and recovery

- **Permission denied:** explain that microphone access is required. A permanent
  Android denial still needs a direct link to system app settings.
- **Model absent:** link the user to Settings without starting an implicit large
  download.
- **Download interrupted:** retain the app-private partial archive and resume on
  explicit retry when the server supports ranges.
- **No speech:** do not replace the current text with an empty transcript.
- **Noisy or accented audio:** return the result for manual editing; do not hide
  uncertainty behind automatic rewriting.
- **Long audio:** Android rejects inputs over 15 minutes or 256 MiB and runs
  Moonshine in overlapping 25-second windows. Desktop and device performance
  acceptance remain separate.

## Acceptance status

- [x] Model absence is reported through the shared UI.
- [x] The current model identity and size are reported truthfully.
- [x] Desktop transcription works with the pinned Moonshine v1 archive.
- [x] Android exposes native recording/transcription endpoints and model status.
- [ ] Permanent Android permission denial opens system app settings.
- [ ] Silence and minimum-duration behavior are explicitly tested.
- [ ] A 30-second quiet recording passes a representative accuracy review.
- [x] Android has explicit duration/source-size bounds and windowed inference.
- [ ] Five- and 15-minute recordings pass memory/cancellation acceptance on a
  target phone.
- [ ] Fresh-download and transcription acceptance passes on a physical phone.

## Related

- [STT overview](stt-overview.md)
- [STT edge cases](edge-cases.md)
- [Decision 002: no LLM transcript correction](../decisions/002-no-llm-transcript-correction.md)
