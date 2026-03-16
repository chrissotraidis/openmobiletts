# Decision: No LLM for Transcript Correction

**Date:** March 2026 (from v3.0 spec)
**Who Decided:** Per spec, confirmed by user
**Status:** Accepted
**Source:** EXPANSION-PLAN-OPEN-MOBILE-VOICE.md, "Executive Summary"

## The Situation

Earlier expansion plans included running a local LLM (e.g., Qwen via llama.cpp) to post-process STT transcripts and fix errors. This would require an additional ~500 MB model download and llama.cpp JNI integration.

## What We Chose

No LLM. Users manually edit transcripts if needed.

## What We Rejected

- Qwen LLM (~500 MB) for automatic transcript correction
- llama.cpp JNI integration on Android
- Any cloud-based correction API

## Why

- Moonshine v2's 7.84% WER (Small) / 6.65% WER (Medium) is sufficient for dictation
- Removes ~500 MB model download
- Eliminates llama.cpp JNI dependency and the entire LlmManager class
- Simpler architecture, faster first-launch experience
- Users can edit text directly — manual correction is adequate

## Consequences

- Some transcription errors will remain uncorrected
- Users must proofread and manually fix any errors
- Significantly lighter storage footprint (~245 MB default vs ~745 MB with LLM)
