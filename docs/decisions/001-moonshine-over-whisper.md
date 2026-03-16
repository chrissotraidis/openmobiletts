# Decision: Moonshine v2 Over Whisper for STT

**Date:** March 2026 (from v3.0 spec)
**Who Decided:** Per spec
**Status:** Accepted
**Source:** EXPANSION-PLAN-OPEN-MOBILE-VOICE.md, "Speech-to-Text (Moonshine v2)"

## The Situation

The app needs on-device speech-to-text. Two viable model families exist: OpenAI's Whisper (via whisper.cpp or sherpa-onnx) and Useful Sensors' Moonshine v2 (via sherpa-onnx).

## What We Chose

Moonshine v2 via sherpa-onnx for all STT inference.

## What We Rejected

- Whisper via sherpa-onnx — known accuracy regressions (GitHub issue #2900)
- whisper.cpp on Android — ongoing crash reports
- Whisper Large v3 — deferred as a future desktop-only option

## Why

1. sherpa-onnx's Whisper implementation has known accuracy regressions
2. whisper.cpp on Android has ongoing crash reports
3. Moonshine v2 Small outperforms Whisper Small while being faster and smaller
4. Moonshine is purpose-built for real-time edge/mobile inference
5. sherpa-onnx already supports Moonshine with Kotlin/Java API and pre-built Android libraries

## Consequences

- Locked to Moonshine model family for STT (acceptable — quality is good)
- Whisper Large v3 remains a future desktop option if maximum accuracy is needed
- Same sherpa-onnx framework for both TTS and STT (simplifies dependencies)
