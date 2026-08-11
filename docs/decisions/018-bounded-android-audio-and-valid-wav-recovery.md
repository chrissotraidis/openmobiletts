# Decision 018: Bounded Android audio and valid WAV recovery

**Date:** 2026-08-11

**Status:** Accepted

**Decided by:** Implementation audit

## Context

Android decoded complete recordings into memory without an enforced duration
or source-size bound. Moonshine v1 then received the entire recording despite
its short-window behavior. The TTS recovery path also concatenated complete
RIFF/WAV files when AAC was unavailable, which does not produce a valid
multi-chunk WAV file.

## Decision

- Limit one Android transcription input to 15 minutes and 256 MiB until target-
  device memory measurements justify a different tier.
- Enforce duration while decoding, not only after a full PCM allocation.
- Decode Moonshine input in 25-second windows with a one-second overlap and
  deterministic word-overlap merging.
- If AAC fails before the first encoded chunk, stop the live transfer, append
  headerless PCM to one disk file, patch a single WAV header at completion,
  and let the client recover the valid file through the existing job endpoint.
- If AAC fails after AAC data has already been written, fail the job rather
  than create a mixed-format file.
- Keep user text and transcript contents out of persistent Android logs.

## Consequences

Longer recordings fail with a clear local error instead of risking an
unbounded allocation. WAV recovery remains disk-backed and valid for any
number of TTS chunks, though it intentionally gives up live transfer and uses
job recovery. The 15-minute boundary is a stabilization limit, not a model
quality or performance claim.
