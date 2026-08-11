# Settings

## Goal

Settings should make one category easy to find and inspect without scrolling
past every unrelated category. Preserve the app's simple three-destination
navigation while giving Settings its own compact information architecture.

## Categories

1. **Voice** — engine, default language/voice, and auto-play.
2. **Connection** — local server override and connection check. An accessible
   explanation states that the server is the local speech-processing backend,
   when the default is correct, and when an override is appropriate.
3. **Models** — required text-to-speech model and optional speech-to-text model,
   including purpose, first-run behavior, storage size, and download/readiness
   state.
4. **Data** — retention, library size, backup, restore, and deletion boundary.
5. **App** — version/runtime facts, diagnostic-log export, and log clearing.

Only one category panel is visible at a time.

## First-run model clarity

- Text-to-speech is marked **Required** because Generate cannot work without a
  voice model.
- Speech-to-text is marked **Optional** because it is needed only for dictation
  and imported-audio transcription.
- Desktop Kokoro PyTorch downloads automatically on first use; the Models panel
  explains this even while voices are still preparing.
- Android presents its required Kokoro download before opening the shared UI.
  The Models panel then reports that model as ready and offers the optional
  Moonshine download separately.
- Android also offers Kitten Mini v0.8 and Kitten Micro v0.8 as explicit,
  English-only **Experimental** voice downloads. Installed models can be
  activated without reinstalling the app; inactive experimental models can be
  removed. Kokoro remains the stable first-run model and undeletable fallback.
- Android model activation uses a clean inference-process restart. The app
  shows a branded switching transition, then restores Settings > Models with
  the selected card visible and marked active instead of returning to Generate.
- Model names never stand alone as setup instructions. Each card states what
  the model does and whether the user must take action.

## Responsive behavior

- On narrow layouts, all five categories appear in one compact horizontal
  picker above the panel.
- On wider layouts, categories appear in a small left rail beside the panel.
- Category controls are buttons with visible labels, selected state, keyboard
  focus, and a minimum 44-by-44-pixel target.
- Panel changes use restrained motion and no motion when the user requests
  reduced motion.

## Platform behavior

Categories and controls respect `/api/capabilities`. A control that cannot work
on the active backend is hidden or explained; it is not left to fail after a
tap.
