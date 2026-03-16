# Audio Import

## What It Does

Upload an existing audio file and transcribe it using Moonshine STT. Supports mp3, aac, ogg, and wav formats. The audio is decoded to PCM and fed through the STT engine, producing editable text.

## Why It Matters

Users have existing audio content (voice memos, recordings, interviews) they want transcribed. This completes the "any audio → text" story alongside live microphone dictation.

## Core Rules

- Supported formats: mp3, aac, ogg, wav (spec-stated)
- Maximum duration: 2 hours (user-stated)
- Audio decoded to 16kHz mono PCM (Moonshine's expected input format) (spec-stated)
- Android: `MediaExtractor` + `MediaCodec` for decoding (zero external dependencies) (spec-stated)
- Desktop: ffmpeg (already a system dependency) for decoding (spec-stated)
- Streams PCM chunks to avoid loading entire files into memory (spec-stated)
- File type detection by extension — routes audio files to STT, document files to text extraction (spec-stated)
- Transcribed text appears in the text area, fully editable (spec-stated)

## New Code

### Android (Kotlin)

- **AudioDecoder.kt** — Decodes imported audio files to PCM using `MediaExtractor` (demux) + `MediaCodec` (decode). Resamples to 16kHz mono. Streams PCM chunks to SttManager.

### Desktop (Python)

- Audio decoding via ffmpeg subprocess (already available). Pipes decoded PCM to Moonshine.

### Frontend (SvelteKit)

- **AudioFileUpload.svelte** (extension of existing upload) — File picker accepts both document formats AND audio formats. Detects file type, routes to appropriate pipeline.

## Integration with Upload Button

The existing Upload Document button is extended — not replaced. When a user selects a file:
1. App checks file extension
2. Document files (pdf, docx, txt, md) → existing text extraction → TTS path
3. Audio files (mp3, aac, ogg, wav) → new PCM decode → STT transcription path

## What's Assumed

- 2-hour max covers the vast majority of use cases (voice memos, meetings, lectures) — Risk if wrong: Low
- Android's MediaExtractor/MediaCodec handle all listed formats reliably — Risk if wrong: Low (these are well-supported system APIs)
- Memory usage stays reasonable when streaming PCM chunks for long files — Risk if wrong: Medium

## Key References

- **Source spec:** `docs/EXPANSION-PLAN-OPEN-MOBILE-VOICE.md`, section "Audio File Import"
- **Related:** [STT](../stt/stt-overview.md) for the transcription engine

## Status

🔵 Not Started
