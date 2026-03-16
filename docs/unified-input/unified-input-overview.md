# Unified Input Screen

## What It Does

Redesigned Generate tab that serves as the app's scratchpad for both voice input and text input. Combines the existing text area with a new mic button and export button — one screen for all input and output.

## Why It Matters

The app's central interaction point. Users shouldn't have to choose a "mode" — they see an empty canvas and interact naturally (type, paste, dictate, upload), then decide what to do with the result (listen, export).

## Core Rules

- Navigation stays at three tabs: Generate (expanded) | History (expanded) | Settings (expanded) — no new tabs (spec-stated)
- Mic button and export button are additions to the existing Generate tab, not a separate mode (spec-stated)
- Text area is shared — typed text, pasted text, and transcribed text all land in the same editable area (spec-stated)
- Upload button accepts both document files (PDF/TXT/MD for TTS) AND audio files (mp3/aac/ogg/wav for STT) (spec-stated)

### Generate Tab Layout (v3.0)

```
┌─────────────────────────┐
│ Text Area               │
│ [Mic] [Upload] buttons  │
│ Voice/Speed selectors   │
│ [Generate] [Export]     │
└─────────────────────────┘
```

### Mic Button Flow

1. User taps mic button
2. App requests microphone permission (if not already granted)
3. Text area transforms into recording view — waveform/audio level indicator + stop button
4. User speaks. Audio captured via WebView MediaRecorder API
5. User taps stop
6. Audio POSTed to `/api/stt/transcribe`
7. Progress indicator shown (reuses GenerationProgress.svelte pattern)
8. Transcribed text appears in text area, fully editable
9. From here: edit, export (PDF/MD/TXT), or generate speech

### After Text Is in the Area

User can:
- **Edit** the text directly
- **Generate Speech** from it (existing TTS flow, unchanged)
- **Export** as PDF/MD/TXT (new)
- **Upload** a document or audio file to replace/append content

## What's Assumed

- Three tabs is sufficient — no need for a fourth tab — Risk if wrong: Low
- Users understand mic button = dictation without explicit mode switching — Risk if wrong: Low

## Key References

- **Source spec:** `docs/EXPANSION-PLAN-OPEN-MOBILE-VOICE.md`, section "Unified Input Screen"
- **Current Generate tab:** `client/src/routes/+page.svelte`, `client/src/lib/components/TextInput.svelte`

## Status

🔵 Not Started
