# Batch Transcription

## What It Does

Batch transcription lets a user upload many audio files at once and export a ZIP of structured Markdown transcripts. It is designed for call batches, interviews, and voice-note folders where each source file should become its own transcript artifact.

## Why It Matters

The existing Generate tab upload is a scratchpad flow: one file is uploaded and its text lands in the text area. Batch transcription is a production workflow: many files are processed sequentially, each result is saved separately, and the user leaves with a folder of reusable transcript files.

## Core Rules

- Batch input supports audio files only: mp3, aac, ogg, wav, webm, and m4a.
- Each uploaded audio file becomes one Markdown transcript in the ZIP.
- Markdown filenames are based on the original audio filenames.
- Processing is sequential in v1 to avoid overloading the local STT model.
- Failed files do not abort the whole batch; failures are recorded in the ZIP manifest.
- The first implementation uses plain Moonshine transcription without speaker diarization.
- Speaker diarization is an optional follow-up mode, not a guaranteed identity system.
- Speaker diarization planning is recorded in [Decision 010](../decisions/010-speaker-diarization-as-optional-local-mode.md).

## Speaker Handling

Speaker diarization can separate turns into labels like `Speaker 1` and `Speaker 2`, but it cannot know real names without user input or reference voices. The product should never imply it knows who is speaking unless the user has explicitly renamed the speaker.

The speaker-aware path should be:

1. Detect speaker turns.
2. Label them as `Speaker 1`, `Speaker 2`, etc.
3. Let the user rename labels to `Customer`, `Agent`, `Jess`, or another human-friendly name.
4. Export renamed labels in Markdown.

## Output ZIP

The ZIP contains:

- One `.md` transcript per source audio file.
- `summary.md` with batch-level status and file counts.
- `manifest.json` with machine-readable file status, filenames, and errors.

## Status

🟡 In Progress
