# Decision: Speaker Diarization as Optional Local Mode

**Date:** 2026-05-11
**Who Decided:** User requested speaker differentiation; Codex proposed implementation path
**Status:** Accepted for planning
**Source:** User discussion on batch call transcripts; sherpa-onnx speaker diarization docs

## The Situation

Batch call transcripts may include multiple speakers. A plain transcript is useful, but calls become easier to review when turns are grouped by speaker.

Speaker diarization can separate turns, but it does not know real human identities by itself. It can label `Speaker 1`, `Speaker 2`, etc.; user-provided renaming is needed before exported transcripts should contain names like `Jess` or `Agent`.

## What We Chose

Add speaker differentiation as an optional local mode after the core batch workflow is stable.

The planned mode uses sherpa-onnx offline speaker diarization with local segmentation and speaker-embedding models. Batch transcription stays usable without diarization models installed.

## What We Rejected

- Cloud diarization APIs.
- Claiming speaker identities without user confirmation.
- Blocking v1 batch transcript export on diarization.
- Mixing best-effort speaker labels into every transcript by default.

## Why

- The app is local-first and should not require cloud audio processing.
- The current batch use case is immediately useful with one Markdown transcript per audio file.
- Diarization has more uncertainty than transcription: overlapping speech, short turns, call quality, and unknown speaker count can all reduce accuracy.
- sherpa-onnx documents offline speaker diarization support and pre-trained local models, so the path fits the existing local architecture.

## Consequences

- Batch transcript export ships first as plain Markdown.
- A future toggle can add `Speaker 1`, `Speaker 2`, etc. labels when diarization models are installed.
- The UI should eventually let users rename anonymous labels before export.
- The app needs model-status and download handling for diarization models before exposing the mode broadly.
