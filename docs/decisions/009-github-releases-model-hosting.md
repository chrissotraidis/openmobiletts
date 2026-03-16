# Decision: GitHub Releases for Model Hosting

**Date:** 2026-03-16
**Who Decided:** User
**Status:** Accepted

## The Situation

STT models (Moonshine v2) need a download host. Options: same GitHub releases page as TTS models, a separate releases repo, or a dedicated CDN.

## What We Chose

GitHub releases — same mechanism already used for TTS model downloads.

## What We Rejected

- Separate GitHub releases repo
- Dedicated CDN or cloud storage

## Why

- User stated: "whatever's easier to download, to have a seamless download"
- Already proven — ModelDownloader.kt downloads TTS model from GitHub releases today
- Sequential download on first launch (TTS then STT) using the same infrastructure
- No new hosting accounts or CDN configuration needed

## Consequences

- GitHub releases has a 2 GB per-file limit (our largest model is ~250 MB — well within limit)
- Download speed depends on GitHub's CDN (generally fast worldwide)
- Simple, consistent download UX for users
