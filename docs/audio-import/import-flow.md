# Audio Import Flow

## Happy path

1. The user opens Generate and chooses Upload.
2. The shared picker accepts supported document and audio extensions.
3. The client routes an audio file to `/api/stt/transcribe`.
4. Desktop uses ffmpeg; Android uses `MediaExtractor`/`MediaCodec` or its
   direct PCM WAV parser.
5. Audio becomes 16 kHz mono float PCM.
6. Moonshine v1 Base English INT8 transcribes the audio locally. Android uses
   overlapping 25-second windows and deterministic overlap merging.
7. A non-empty transcript enters the editable text area; empty output does not
   erase existing text.
8. The user can edit, export, save, or synthesize the transcript.

## Android stabilization limits

- Maximum source file: 256 MiB.
- Maximum decoded duration: 15 minutes.
- The decoder rejects reported or observed duration beyond the limit while it
  is processing, before an unbounded PCM allocation can grow.
- The current implementation still owns one bounded final float array; peak
  memory must be measured with 5- and 15-minute files on target phones.

## Recovery

- **Unsupported/corrupt codec:** keep existing text and show the decoder error.
- **Model absent:** direct the user to Models Settings; do not start a large
  implicit STT download.
- **Input above a bound:** explain the 15-minute/256-MiB limit and ask the user
  to split or compress the recording.
- **Empty result:** keep existing editable text.
- **Background/process interruption:** not yet a durable STT job; physical-
  device acceptance remains.

## Acceptance status

- [x] Shared Upload routes supported audio separately from documents.
- [x] Android decodes to 16 kHz mono and enforces source/duration bounds.
- [x] Android Moonshine inference uses bounded windows.
- [x] Returned text remains editable.
- [ ] Error copy is reviewed across representative codecs and corrupt files.
- [ ] Five- and 15-minute files pass peak-memory and cancellation checks.
- [ ] Physical Android background/interruption behavior is accepted.

## Related

- [Audio import overview](audio-import-overview.md)
- [Audio import edge cases](edge-cases.md)
- [STT overview](../stt/stt-overview.md)
- [Decision 018](../decisions/018-bounded-android-audio-and-valid-wav-recovery.md)
