# Decision: Both TTS and STT Models Loaded Simultaneously

**Date:** March 2026 (from v3.0 spec)
**Who Decided:** Per spec
**Status:** Accepted
**Source:** EXPANSION-PLAN-OPEN-MOBILE-VOICE.md, "Architecture"

## The Situation

The app needs both a TTS model (Kokoro INT8, ~95 MB) and an STT model (Moonshine v2 Small, ~100 MB). Options: load both simultaneously, or swap models on demand.

## What We Chose

Both models loaded simultaneously in RAM (~195 MB total).

## What We Rejected

- Sequential model loading with swap (2-5 second swap latency per switch)
- Loading only the active model

## Why

- Total ~195 MB in RAM — a Pixel 9 Pro has 16 GB, this is trivial
- Eliminates 2-5 second swap latency when switching between TTS and STT
- Simpler code — no model lifecycle management or swap logic
- Users can dictate then immediately generate speech without waiting

## Consequences

- Higher baseline memory usage (~195 MB vs ~100 MB)
- Devices with 4-6 GB RAM may not support this — document minimum requirements transparently
- No model-swapping complexity to maintain
