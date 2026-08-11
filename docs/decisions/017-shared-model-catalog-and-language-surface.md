# Decision 017: Shared model catalog and verified language surface

**Date:** 2026-08-11

**Status:** Accepted

**Decided by:** Project owner and implementation audit

## Context

Desktop and Android duplicated model URLs, sizes, checksums, labels, runtime
versions, and required files. Those copies had already drifted once. The
selected sherpa Kokoro package contains 53 speaker embeddings, but upstream
sherpa documentation only implements English and Chinese text support for this
export. The application exposed every speaker prefix as a supported language,
including Spanish, French, Hindi, Italian, Japanese, and Portuguese.

## Decision

Use `models/model-catalog.v1.json` as the repository-owned source of model
identity, integrity, runtime, language, distribution, and license metadata.
Python loads its managed STT specification from the catalog. Android packages
and reads the same file for TTS/STT download and API metadata.

Expose only the English US and UK speaker ranges for the current sherpa Kokoro
surface. Keep Chinese recorded as upstream-supported but hidden until product
normalization and hands-on language acceptance are complete. Do not infer
language support from a speaker-name prefix.

## Consequences

- Archive sizes, hashes, required paths, source, and license text have one
  reviewable owner.
- Android assembly fails if the catalog is missing or invalid.
- The native voice list drops from 53 package speakers to 28 exposed English
  speakers.
- The package can still be benchmarked for Chinese without advertising it.
- Adding or promoting a model requires a catalog revision and validation test,
  not independent Python/Kotlin constant changes.
