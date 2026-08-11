# Decision 020: Experimental Android Kitten models

**Date:** 2026-08-11
**Status:** Accepted
**Decided by:** Project owner

## Context

Android currently requires Kokoro v1.0 through sherpa-onnx. It is the known
working baseline, but its verified archive is 333.2 MiB. KittenTTS v0.8 offers
smaller English-only ONNX packages that the already-pinned sherpa-onnx 1.13.4
runtime can load without adding another native inference stack.

The project owner requested Kitten Mini and Micro as user-installable,
swappable experimental choices. This slice requires functional emulator proof,
not comparative quality or performance benchmarking.

## Decision

- Keep Kokoro v1.0 as the stable Android first-run model and rollback target.
- Offer KittenTTS v0.8 Mini and Micro only in Android **Models** settings.
- Label both Kitten choices **Experimental** and English-only.
- Download each model only after an explicit user action.
- Verify archive byte count, SHA-256, safe extraction, required files, and a
  native non-empty-audio smoke test before activation.
- Persist the active model only after its native engine loads successfully.
- Allow inactive experimental models to be removed. Never remove the active
  model or the required Kokoro fallback from the Models screen.
- Expose the eight upstream voice names with their verified sherpa speaker-row
  mapping.

## Rejected

- Replacing Kokoro as the first-run default without quality and device evidence.
- Bundling experimental weights in the APK.
- Adding a second inference runtime for Kitten.
- Advertising Mini or Micro as faster or better without a controlled benchmark.

## Consequences

Android users can trade storage and model behavior without reinstalling the
app. The model manager and shared UI now need per-model download, activation,
voice, persistence, and deletion state. Simulator generation proves functional
integration only; it does not establish quality, speed, battery, thermal, or
physical-device acceptance.
