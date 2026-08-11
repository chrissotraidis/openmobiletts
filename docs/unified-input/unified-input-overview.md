# Unified Generate Screen

## Current behavior

Generate is one scratchpad rather than separate TTS and STT modes. Users can
type, paste, dictate, import a document, or import audio into the same editable
text area, then synthesize or export the result.

The application keeps three primary destinations: Generate, History, and
Settings. Backend-specific actions are controlled by the versioned platform
capability response.

## Main flow

1. Enter, paste, record, or import content.
2. Edit the resulting text.
3. Choose a verified voice and generate local speech, or export the text.
4. Playback and synchronized reading live in the persistent player.
5. Completed work appears in the client-owned History library.

## Interaction rules

- Recording, transcription, and generation expose clear busy/error states.
- Unsupported actions are hidden or explained before the user taps them.
- File import distinguishes documents from audio without creating a new mode.
- The editor remains the source for synthesis/export; transcription never
  silently invokes an LLM rewrite.
- Primary actions keep mobile-sized targets and keyboard semantics.

## Status

🟡 Implemented shared surface. Checker-visible accessibility issues pass with
0 errors/warnings. Manual screen-reader/focus/reflow checks, recording conflict
states, long-file limits, and physical Android acceptance remain open.
