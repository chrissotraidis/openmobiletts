# Decision 016: Two-panel waveform icon

**Date:** 2026-08-11

**Status:** Accepted

**Decided by:** Project owner

## Context

The previous Android icon was a placeholder circle/speaker treatment and the
web icon files were empty. The project needs one recognizable mark for Android,
future iOS, the local web interface, repository presentation, and small
favicons without tying the symbol to a future product rename.

Three grounded ImageGen directions were produced from the current dark-blue UI
and existing icon family. The project owner selected the first displayed
direction.

## Decision

Use the selected two-panel mark: facing text planes with six negative-space
waveform bars, rendered in electric blue through violet on a near-black navy
surface.

- Keep the mark letter-free.
- Derive all platform sizes from one reviewed raster master.
- Use a transparent colored foreground plus solid navy background for Android
  adaptive icons.
- Supply a white-alpha monochrome foreground for Android themed icons.
- Use the opaque master for legacy Android, web, README, and future iOS sizes.

## Rejected alternatives

- The circular protected-signal direction was less specific to text-to-speech.
- The speech-bubble direction risked reading as a generic chat application.
- Independent platform redraws were rejected because they would drift.

## Consequences

The repository now owns real Android legacy/adaptive/monochrome icons, web
favicon/manifest/apple-touch sizes, a README mark/banner, and a 1024-pixel
iOS-ready master. Any future brand revision must update the reviewed master and
regenerate every platform size together.
