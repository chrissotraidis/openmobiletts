# Decision 023: Android launcher label is OMTTS

**Date:** 2026-08-11
**Status:** Accepted
**Decided by:** Project owner

## Context

Android launcher and app-drawer labels have limited width. The full product
name, **Open Mobile TTS**, is truncated on common phone layouts and is therefore
less recognizable beside the selected icon.

## Decision

- Use **OMTTS** as the Android application label shown by the launcher, app
  drawer, recent-apps view, and Android system surfaces.
- Keep **Open Mobile TTS** as the full product name inside the application, in
  repository documentation, and on other platforms.
- Do not change the Android package identifier or application data location.

## Consequences

The installed app has a short label that fits beneath the icon without losing
the established full product identity. Existing models, preferences, History,
and signing/update compatibility are unaffected.
