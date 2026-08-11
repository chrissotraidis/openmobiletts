# Decision 021: Process-isolated Android TTS switching

**Date:** 2026-08-11
**Status:** Accepted
**Decided by:** Project owner and Codex regression investigation

## Context

Physical Pixel testing found that Kitten Mini, Kitten Micro, and then Kokoro
produced extremely short or incorrect audio after models were installed and
switched in one app process. The downloaded archives, checksums, and required
files were valid. Native logs showed that a temporary staged Mini instance
generated normally, but synthesis became truncated after that instance was
released. Releasing an old `OfflineTts` after constructing a replacement also
damaged the replacement's shared phonemizer state.

The previous non-empty-waveform smoke check did not detect the fault: a broken
engine could still return a small, non-empty sample array.

## Decision

- Keep exactly one sherpa `OfflineTts` instance for the lifetime of an Android
  app process.
- Do not construct or release temporary TTS engines inside model-download
  workers.
- Validate downloaded TTS packages with pinned byte count, SHA-256, safe
  extraction, and required non-empty files before installation.
- Activating another installed TTS model synchronously persists the selection,
  then uses a short-lived foreground restart Activity in a separate process to
  relaunch the app before that model is loaded.
- Treat the first normal generation after a clean activation as the functional
  native test. Physical acceptance requires intelligible, full-duration audio;
  a non-empty buffer alone is insufficient.
- Keep Kokoro installed as the stable fallback. Returning to Kokoro uses the
  same clean-restart boundary.

This decision supersedes the in-process TTS smoke-load and hot-swap portions of
Decisions 015 and 020. STT retains its independent staged recognizer load test.

## Rejected

- Releasing and recreating `OfflineTts` inside one process.
- Accepting any non-empty waveform as proof of usable synthesis.
- Removing Kitten solely because the lifecycle defect also affected Kokoro.
- Shipping separate Android processes or inference services for each model.

## Consequences

Model changes briefly restart the local Android app, but preserve its model
files, History, draft, and settings. Downloads no longer exercise native TTS
before installation, so the model catalog and physical-device regression pass
must provide the functional evidence. Inference remains one local app process
and one TTS engine; the transient restart process loads no model or server and
adds no inference runtime.
