# Decision 019: Lazy optional STT initialization

**Date:** 2026-08-11

**Status:** Accepted; supersedes Decision 006

**Decided by:** Implementation audit

## Context

Decision 006 assumed a 95 MB INT8 Kokoro model and a roughly 100 MB Moonshine
v2 model, for about 195 MB resident together. The actual Android packages are
a 333.2 MiB Kokoro FP32 archive and a 239.2 MiB Moonshine v1 INT8 archive, with
larger installed and runtime footprints. STT is optional and many users only
use text-to-speech.

## Decision

Do not initialize Moonshine during Android server startup merely because its
files are installed. Load STT on the first transcription request. Keep the
loaded recognizer for subsequent requests in the same process; do not add a
model-swapping subsystem until target-device memory evidence requires it.

TTS remains the required first-run model and initializes on first synthesis.
STT remains a separately requested Settings download.

## Consequences

- TTS-only launches avoid unnecessary STT model initialization and resident
  memory.
- The first transcription after a process start includes model-load latency.
- Users who exercise both features can still have both models resident; this
  needs physical-device peak-memory and low-memory-kill acceptance.
- Any future unload/swap policy requires measured evidence and a new decision.
