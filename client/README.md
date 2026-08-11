# Open Mobile TTS Shared Client

The client is the shared Svelte 5/SvelteKit interface used by both runtime
shapes:

- desktop/local web, served by FastAPI; and
- Android, bundled into a WebView and served by NanoHTTPD.

It is not currently a supported offline PWA. A manifest and real icon family
exist, but the service worker clears caches and unregisters itself and model
inference still requires the local backend.

## Current experience

- New Audio/Create editor
- microphone dictation and audio/document upload
- voice/language selection
- TTS generation progress and text highlighting
- compact/expanded player and queue
- history/reader flow
- capability-gated batch transcription UI on the desktop backend
- PDF/Markdown/text export UI
- settings for TTS, STT, connection, storage, backup, and logs

The same UI does not mean every backend supports every route. Both backends now
publish `/api/capabilities`; the client uses that response to hide unsupported
actions instead of presenting controls that will fail. Android declares batch
transcription and engine switching unsupported.

The STT Settings panel consumes truthful model metadata from either backend,
starts or resumes model installation, renders byte progress and errors, and
offers Pause for Android's WorkManager-backed download.

Voice and Models Settings consume the shared `/api/models/catalog`
metadata for runtime, integrity, language, and license details. The Android
shell always uses its in-process loopback backend and ignores stale remote-
server preferences; desktop retains the explicit development connection tool.

Settings uses five responsive categories—Voice, Connection, Models, Data,
and App—so only one panel is visible at a time instead of one long scroll.

## Development

```sh
npm ci
npm run dev
```

`package-lock.json` is tracked and is the source of truth for installs used by
the launcher, Docker, and CI. Node 18, 20, or 22+ is supported by the current
Vite toolchain; Node 20 or 22 LTS is recommended.

Vite serves the development interface and proxies `/api/*` to the local Python
server according to `vite.config.js`.

Production build:

```sh
npm run build
```

Quality check:

```sh
npm run check
```

At the 2026-08-11 Slice 4 checkpoint, both the production build and quality
check pass with 0 errors and 0 warnings. The checker-visible native-bridge,
event/FileReader, handler, dialog, and keyboard issues were corrected. Manual
screen-reader, focus-flow, zoom/reflow, contrast, and reduced-motion acceptance
remain separate device/browser checks.

## Architecture

```text
src/routes/+page.svelte
  navigation, Settings, high-level screen state

src/lib/components/
  editor/import, player, generation, history, waveform, reading/highlighting

src/lib/stores/
  draft, settings, player, playlist, history, batch state

src/lib/services/
  API requests and IndexedDB audio cache
```

The visible History library is authoritative: metadata/text use localStorage
and generated audio/timing use IndexedDB. Backup/restore covers History and
portable preferences but intentionally excludes audio blobs and the
device-specific server URL. Backend `/api/projects*` routes are legacy
compatibility surfaces, not the visible library backup.

## UI modernization direction

Preserve the simple Create/History/Settings mental model. Remaining UI work
includes:

- first-run/model-download states;
- dense Create actions through progressive disclosure;
- dedicated repair/update/delete model management;
- deeper platform-aware status and model-management UX;
- real progressive playback or corrected wording;
- accessible dialogs, sliders, focus, zoom, contrast, screen readers, and
  reduced motion; and
- selected, reference-driven motion and visual design.

Do not begin the reskin from prose alone. Capture the current desktop and
Android flows, generate three visual directions, choose one, and compare the
implementation to that reference.

See [the Phase Zero plan](../docs/PHASE_ZERO_MODERNIZATION_PLAN.md).
