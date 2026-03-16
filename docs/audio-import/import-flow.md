# Audio Import Flow

## Who

A user who has an existing audio file (voice memo, recording, podcast clip) and wants it transcribed to text.

## The Happy Path

1. User is on the Generate tab
2. User taps the **Upload** button (same button used for document import)
3. File picker opens — accepts both document formats (pdf, docx, txt, md) AND audio formats (mp3, aac, ogg, wav)
4. User selects an audio file (e.g., `meeting-notes.mp3`)
5. App detects file type by extension → routes to STT pipeline (not document extraction)
6. **AudioDecoder** decodes the file to 16kHz mono PCM:
   - Android: `MediaExtractor` + `MediaCodec`
   - Desktop: ffmpeg subprocess
7. PCM audio is fed to Moonshine via `/api/stt/transcribe`
8. **Progress indicator** appears — "Transcribing... (estimated X minutes)" based on audio duration
9. Moonshine processes the audio in segments, returns full transcript
10. Transcribed text appears in the text area, **fully editable**
11. User can: edit, export (PDF/MD/TXT), or generate speech from the transcript

## What Could Go Wrong

### Unsupported file format
- **When:** User selects a file with an unsupported extension (e.g., .flac, .wma, .m4a)
- **What happens:** App shows error: "Unsupported audio format. Supported: mp3, aac, ogg, wav"
- **Recovery:** User converts the file externally and re-uploads

### Corrupt or unreadable audio file
- **When:** File has the right extension but is corrupt, truncated, or has an unexpected internal codec
- **What happens:** AudioDecoder fails during decoding. App shows error: "Could not read audio file. The file may be corrupt."
- **Recovery:** User tries a different file or re-encodes the original

### File exceeds 2-hour limit
- **When:** User selects an audio file longer than 2 hours
- **What happens:** App checks duration after decoding headers. Shows error: "Audio file is too long (max 2 hours). Consider splitting the file."
- **Recovery:** User splits the file externally (e.g., Audacity, ffmpeg)

### STT model not downloaded
- **When:** User tries to import audio but Moonshine model isn't available
- **What happens:** Same as dictation flow — prompt to download model
- **Recovery:** Download model, then re-upload the file

### Large file memory pressure
- **When:** A 2-hour audio file at high bitrate produces a large PCM stream
- **What happens:** AudioDecoder streams PCM chunks (doesn't load entire file into memory). Moonshine processes segments. Memory stays bounded.
- **Recovery:** If memory pressure is detected, log a warning. Processing continues.

### App backgrounded during transcription (Android)
- **When:** User switches apps while a long audio file is being transcribed
- **What happens:** SttManager runs on Kotlin IO dispatcher — transcription continues in background
- **Recovery:** Result is ready when user returns

## Acceptance Criteria

- [ ] Upload button accepts audio files (mp3, aac, ogg, wav) alongside document files
- [ ] File type is correctly detected by extension and routed to STT (not document extraction)
- [ ] Audio is decoded to 16kHz mono PCM before transcription
- [ ] Progress indicator shows estimated transcription time
- [ ] Transcribed text appears in text area and is editable
- [ ] Files over 2 hours are rejected with a clear error message
- [ ] Corrupt/unreadable files show a user-friendly error (not a crash)
- [ ] Unsupported formats show a clear error listing supported formats
- [ ] 5-minute audio file transcribes successfully on Pixel 9 Pro
- [ ] 30-minute audio file transcribes successfully without OOM

## Related

- See: [Audio Import Overview](audio-import-overview.md) for technical details
- See: [STT Overview](../stt/stt-overview.md) for Moonshine model specs
- See: [Document Import](../document-import/document-import-overview.md) for the shared Upload button
