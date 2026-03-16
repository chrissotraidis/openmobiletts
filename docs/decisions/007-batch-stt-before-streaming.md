# Decision: Batch STT Before Streaming

**Date:** March 2026 (from v3.0 spec, confirmed by user)
**Who Decided:** Per spec, user confirmed
**Status:** Accepted
**Source:** EXPANSION-PLAN-OPEN-MOBILE-VOICE.md, "Open Questions"

## The Situation

STT can work in two modes: batch (record first, transcribe after) or streaming (text appears as you speak in real-time).

## What We Chose

Batch transcription only in v3.0. Streaming STT deferred.

## What We Rejected

- Streaming STT (text appearing in real-time as user speaks)

## Why

- Streaming STT is technically complex and unexplored territory for this project
- Batch is simpler to implement reliably
- Requires sherpa-onnx `OnlineRecognizer` (streaming mode) instead of `OfflineRecognizer` — different API
- Batch quality is generally higher than streaming
- Can add streaming as a v3.1 enhancement after batch proves stable

## Consequences

- Users must finish speaking before seeing transcript (not real-time)
- Simpler implementation and testing
- Streaming STT remains a future enhancement option
