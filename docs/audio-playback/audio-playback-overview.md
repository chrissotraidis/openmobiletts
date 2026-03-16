# Audio Playback

## What It Does

Manages audio playback with a bottom player bar, queue system, history, and synchronized sentence-level text highlighting. Supports play/pause/stop, seeking, download, and queue management. On Android, integrates with system media controls via notification and lock screen.

## Why It Matters

This is the output experience for TTS — how users listen to generated audio. The synchronized highlighting and queue system make it practical for long-form content.

## Core Rules

- Native HTML5 `<audio>` element with blob URLs for playback (code-derived)
- Sentence-level text highlighting synchronized with audio timing metadata (code-derived)
- Queue system: users can queue multiple history items for sequential playback (code-derived)
- Auto-play next item in queue via `handleEnded` (code-derived)
- Audio cached in IndexedDB for offline replay (code-derived)
- History cards show "Audio ready" (cached) vs "Text only" (no cached audio) status (code-derived)
- Reader/detail view with synchronized highlighting and Android back button support via `popstate` (code-derived)
- Opening a history entry auto-plays only if audio is cached (avoids triggering expensive on-device regeneration) (code-derived)

### Android-Specific

- Foreground service notification with two modes: generation progress and media transport controls (code-derived)
- Media controls: play/pause/stop in notification shade, lock screen, and Bluetooth via `MediaSessionCompat` (code-derived)
- Notification → WebView routing via `window.__ttsControl` (code-derived)
- Cancel button sends `POST /api/tts/jobs/{id}/cancel` to server (code-derived)
- Proactive stream abort on `visibilitychange` (backgrounding) triggers job recovery (code-derived)

## v3.0 Changes

History expands to show three card types:
- **TTS generation** (existing): Title, voice, speed, date, "Audio ready" badge
- **STT transcription** (new): Title, source (Mic/File), duration, date, "Transcribed" badge. Detail view with Export and "Generate Speech" buttons.
- **Document export** (new): Title, format (PDF/MD/TXT), date, "Exported" badge

History is backed by the project storage system in v3.0 (see [Project Storage](../project-storage/project-storage-overview.md)).

## Key Components

- `AudioPlayer.svelte` — Bottom player bar with expanded "Now Playing" view, queue toggle
- `AudioHistory.svelte` — History list with play/queue/download/delete
- `GenerationProgress.svelte` — Progress bar, chunk counter, elapsed/estimated time, cancel
- `TextDisplay.svelte` — Synchronized sentence highlighting
- `player.js` — Playback state, streaming protocol parser, Android job recovery
- `history.js` — History refresh events
- `audioCache.js` — IndexedDB cache with `getCachedIds()` for lightweight status queries

## Key References

- **Android notifications:** `docs/ANDROID_ARCHITECTURE.md`, "Notification System"
- **Job recovery:** `docs/technical-architecture.md`, "Android job recovery"
- **Android code:** `android/app/.../TtsService.kt`

## Status

🟢 Implemented
