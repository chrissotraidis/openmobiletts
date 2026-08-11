# Decision 015: WorkManager model delivery and TTS-first start

**Date:** 2026-08-11

**Status:** Accepted

**Decided by:** Project owner and Codex modernization implementation

## Context

Android currently downloads roughly 600 MB of TTS and STT data inside an
Activity coroutine or an in-process NanoHTTPD thread. Rotation, process death,
or app dismissal can abandon the transfer. Downloads cannot resume, and first
run requires the optional experimental STT model before the TTS app can open.

## Decision

- Use AndroidX WorkManager 2.10.5 unique work for model transfers. It is the
  newest stable line compatible with the project's Kotlin 1.9 compiler;
  WorkManager 2.11.x requires Kotlin 2.1 metadata and is a separate toolchain
  upgrade.
- Require a connected network, retry transient failures with exponential
  backoff, expose durable progress, and run long transfers as foreground work
  with a visible notification.
- Keep partial archives in app-private storage and resume with HTTP Range when
  the server supports it.
- Cancellation keeps a valid partial archive for a later explicit retry; hash
  failure deletes it.
- First run downloads only the required Kokoro TTS package. Moonshine STT
  remains an explicit Settings download.
- Keep the old active model until a candidate passes size, checksum, safe
  extraction, required-file validation, and native load smoke testing.

## Rejected alternatives

### Continue Activity/thread downloads

Rejected because progress and work lifetime disappear with the process and do
not satisfy a large mobile download flow.

### Require TTS and STT on first run

Rejected because STT is experimental and optional. It adds a 239.2 MiB archive
before users can access the stable TTS core.

### Delete partial data on every interruption

Rejected because it wastes bandwidth and makes large downloads fragile.

## Consequences

- Android adds WorkManager and data-sync foreground-service declarations.
- The first-run UI observes work rather than owning the download coroutine.
- STT status comes from durable WorkInfo instead of process-local atomics.
- Final physical-device acceptance must cover cancellation, resume, process
  death, notification denial, metered/unmetered behavior, and retry.
