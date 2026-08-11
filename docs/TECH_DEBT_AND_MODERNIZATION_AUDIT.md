# Open Mobile TTS: Technical Debt and Modernization Audit

**Audit date:** 2026-08-10

**Repository snapshot:** `main` at `d8fea88248531dfd0d320d5f4107fe263525da2e`

**Scope:** local web/desktop app, Android app, model stack, model delivery, product UX, repository presentation, and a future iOS path

**Mode:** audit only; this document does not authorize or include a reskin, model replacement, icon replacement, or iOS implementation

> **Implementation update, 2026-08-10:** The first Phase Zero slice corrected
> model/product/version labels, added a pinned and checksum-verified desktop STT
> Settings installer with real progress and safe activation, changed the server
> default to loopback with local-only CORS defaults, and redacted content logs
> by default. The server suite now has 48 passing tests; the client production
> build still passes and its pre-existing quality baseline remains 49 errors and
> 13 warnings. Historical findings below describe the audited starting point.
>
> **Slice 2 update:** npm and universal hash-pinned Python locks are tracked;
> launcher/Docker/CI use locked installs; ReportLab is declared and verified;
> the Hugging Face cache and Docker volume are corrected; launcher builds use
> source hashes; and a Python 3.10-3.12 desktop CI matrix is defined. A clean
> Python 3.11 lock installation passed 48 tests and real PDF export. Docker Hub
> timed out during base-image metadata validation, so a full image build is not
> claimed.
>
> **Slice 4 update, 2026-08-11:** FastAPI and NanoHTTPD now expose a versioned
> platform-capability schema, unknown API routes return JSON, and the shared UI
> hides unsupported Android batch/engine actions. The Svelte check now passes
> with 0 errors and 0 warnings and is blocking in desktop CI. The default
> launcher creates a repository `.venv`; a clean launch initialized real Kokoro
> voices. Server tests now total 53, and Android rebuilt successfully with the
> new contract. Manual accessibility acceptance and unified user-data ownership
> remain open.

## Executive conclusion

Open Mobile TTS has more capability than its public presentation suggests. It is no longer only a text-to-speech demo: the repository contains TTS, batch STT, document and audio import, export, history, projects, Android background generation, recovery, media controls, and two desktop TTS backends.

The product's main problem is not that it is too simple. Its main problem is
that the implementation, documentation, model identities, platform contracts,
and release story drifted apart. Several public claims and cross-platform
controls still need correction. Slice 3 has since restored a reproducible
Android debug build; production release acceptance remains open.

The recommended strategy is:

1. Make the current product truthful, reproducible, safe, and testable.
2. Benchmark a small model shortlist on real devices; do not replace Kokoro or Moonshine because a newer model exists.
3. Restructure the shared UI around the real product: create, transcribe, listen, manage models, and manage files.
4. Modernize Android around a versioned model manager and a narrower native bridge.
5. Build iOS only after the model manifest, API contract, storage rules, and platform-capability boundary are shared and tested.

## What the product actually is

There are two shipping runtime shapes, not two unrelated products.

| Surface | UI | Native/runtime layer | TTS | STT | Network after setup |
|---|---|---|---|---|---|
| Desktop/local web | SvelteKit served by FastAPI | Python | Kokoro-82M through PyTorch by default; optional sherpa-onnx Kokoro | sherpa-onnx Moonshine v1 Base English INT8 | Local loopback for UI/API; inference is local |
| Android | The same SvelteKit UI inside a WebView | Kotlin, NanoHTTPD, JNI, MediaCodec, foreground service | sherpa-onnx Kokoro `kokoro-multi-lang-v1_0` | sherpa-onnx Moonshine v1 Base English INT8 | Model download initially; inference is local |
| iOS | Not implemented | Not implemented | Not selected | Not selected | Not applicable |

The Android app is not a separate native UI. It serves the shared built web app from an in-process server at `127.0.0.1:8080`, while Kotlin owns model inference, file access, audio encoding, notifications, lock-screen controls, and the JavaScript bridge. This is a reasonable reuse strategy and means most visual modernization can be performed once in the shared frontend.

There is no language model or transcript-correction LLM in the application. The transcript `polish_transcript` step is deterministic formatting. That simplicity is a privacy, size, latency, and hallucination advantage and should remain the default.

## Current model inventory: corrected

### TTS

| Platform/mode | Actual model | Runtime | Observed/official footprint | Current truth |
|---|---|---|---|---|
| Desktop default | `hexgrad/Kokoro-82M` | PyTorch via the `kokoro` Python package | about 314-327 MB model cache | Sound baseline; seven US voices are exposed by the default `lang_code`, not all 11 documented English voices |
| Desktop optional | `kokoro-multi-lang-v1_0` | sherpa-onnx | 383 MB observed extracted; upstream model and voice files alone are about 336 MB | Available locally, but setup is manual and language claims exceed upstream's documented English/Chinese support |
| Android | `kokoro-multi-lang-v1_0` | sherpa-onnx JNI | roughly 350-383 MB extracted | Not INT8 and not approximately 95 MB, despite the Android README and older planning documents |

Primary sources: [Kokoro-82M model card](https://huggingface.co/hexgrad/Kokoro-82M) and [sherpa-onnx Kokoro package documentation](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html).

### STT

Both desktop and Android hardcode:

```text
sherpa-onnx-moonshine-base-en-int8
```

That is the older English-only Moonshine v1 Base INT8 export. It is not Moonshine v2 Medium. Its local ONNX files total roughly 272-274 MB. At the audited starting point, the server, Android comments, API responses, settings UI, transcript metadata, status documents, and architecture decisions repeatedly labeled it `moonshine-v2-medium`; the first Phase Zero slice has corrected the active code surfaces.

Primary source: [sherpa-onnx Moonshine v1 model documentation](https://k2-fsa.github.io/sherpa/onnx/moonshine/models.html).

This mismatch is the highest-priority model issue. Correct the identity before comparing quality or presenting upgrade choices.

## Verified audit evidence

- The local app served HTTP 200 at `localhost:8000`.
- A real Kokoro TTS request returned a 22,076-byte framed stream with timing data and 2.74275 seconds of generated audio.
- A bundled Moonshine test WAV transcribed successfully, but the response falsely reported model `moonshine-v2-medium`.
- Server test suite at audit start: **39 passed** with deprecation warnings; **53 pass** after the current Phase Zero slices.
- Client production build: **passes**.
- Client Svelte quality check: **passes with 0 errors and 0 warnings** after Slice 4; manual accessibility acceptance remains.
- At the audit starting point, Android could not be built from the checkout
  because its wrapper, JNI payload, and bundled web assets were absent. Slice 3
  later replaced those hidden inputs and passed an isolated source-only build.

The live visual browser was available to the user during this audit, but the audit environment did not expose a working screenshot-control channel. Visual findings below are therefore grounded in the current UI source, the live app behavior that could be verified through APIs, and the inspected icon assets. They are not a screenshot-based WCAG certification or a complete hands-on Android acceptance pass.

## Priority map

### P0: truth, broken flows, and reproducibility

1. **Implemented in first slice:** Correct the STT identity everywhere from Moonshine v2 Medium to the actual Moonshine v1 Base INT8 until a real v2 migration occurs.
2. **Implemented:** Correct Android TTS download and installed-size claims from approximately 95 MB to measured archive/extracted values.
3. **Implemented through Decision 017:** The native registry exposes 28 accepted English US/UK speakers instead of inferring nine languages from speaker prefixes.
4. **Implemented in first slice:** Fix desktop STT first-run with a working, verified `/api/stt/models/download` path.
5. **Implemented:** A capability contract hides unsupported Android batch transcription and unknown API routes return JSON.
6. **Implemented:** The Gradle wrapper, version-locked AAR, generated web assets, strict verification, and Android CI definitions are repository-owned.
7. **Implemented in Slice 2:** Track npm and hash-pinned Python locks; use them
   in the launcher, Docker, and CI.
8. **Implemented:** The top-level Apache-2.0 `LICENSE` is tracked.
9. **Implemented:** The root README uses the real clone URL and current documentation paths.
10. **Implemented for active surfaces:** Server/UI/Android identity is Open Mobile TTS and runtime versions share the root `VERSION`; archived/reference docs are explicitly historical.

### P1: privacy, correctness, reliability, and quality gates

1. **Implemented for local defaults:** Bind the desktop server to `127.0.0.1` and use local development origins; deliberately enabled LAN mode still has no authentication.
2. **Implemented:** Redact content logs by default, with an explicit troubleshooting opt-in.
3. **Implemented baseline:** Both backends consume one versioned model catalog; Android uses checksum validation, resume, durable WorkManager progress, staging, smoke tests, and rollback.
4. **Partially implemented:** Native managers serialize non-thread-safe inference, TTS uses disk-backed jobs and a bounded transfer queue, and cleanup/cancellation exist; desktop batch quotas and per-request engine isolation remain.
5. **Implemented bounded baseline through Decision 018:** Android caps transcription at 15 minutes/256 MiB and windows Moonshine input. Target-device peak-memory acceptance remains.
6. **Implemented through Decision 018:** WAV recovery writes one PCM file and patches one RIFF header after all chunks.
7. **Implemented:** WebView navigation, permissions, file picking, and the JavaScript bridge are limited to `http://127.0.0.1:8080`; other cleartext traffic is denied.
8. Validate Android foreground-service types and background generation on Android 14-16.
9. **Implemented through Decision 014:** The visible client History/IndexedDB library is authoritative; backup/restore and retention operate on that same data source.
10. **Implemented in Slice 2:** ReportLab is declared, hash-locked, and verified
    with a real PDF response from the clean environment.
11. **Implemented in Slice 2:** The launcher detects the Hugging Face cache and
    Docker persists `/root/.cache/huggingface`.
12. **Implemented in Slice 2:** `run.py` hashes all frontend build inputs and
    rebuilds when the stored source hash differs.

### P2: product structure and visual modernization

1. **Implemented:** Settings uses five compact responsive categories instead of one long scroll.
2. **Partially implemented:** Voice/STT surfaces show catalog-backed size, runtime, language, license, status, progress, pause, and integrity details; repair/delete/update remain future controls.
3. Simplify the creation surface. Dictate, Upload, Batch Upload, language, voice, Save, Export, and Generate currently compete in one dense region.
4. **Implemented for Settings:** Category navigation, model details, and platform-specific connection controls use progressive disclosure/capability gating.
5. **Implemented checker-visible baseline:** Navigation, model progress, player/list transitions, and `prefers-reduced-motion` handling are present; manual assistive-tech QA remains.
6. **Implemented through Decision 016:** The selected two-panel waveform mark replaces generic branding.
7. **Implemented:** Real favicon, manifest, Apple-touch, Android adaptive/themed, README, and iOS-ready raster assets exist.
8. **Decided:** PWA installation is unsupported for this stabilization release; the product is described as a local web interface.

## Web/desktop audit

### Strengths worth preserving

- The three-item navigation model—New Audio, History, Settings—is understandable.
- The dark visual system is consistent and mobile-first.
- The text editor, generation progress, synchronized highlighting, compact player, expanded player, queue, and history form a coherent listening loop.
- TTS backends already share an abstraction, making controlled model benchmarks possible.
- Local processing, no account requirement, and deterministic transcript formatting are strong product positions.
- The FastAPI server and static UI run as one process, which keeps basic setup approachable.

### Correctness and experience gaps

#### The current “streaming” claim overstates playback behavior

FastAPI streams framed audio chunks, but the browser client buffers all audio chunks into a Blob before starting playback. The user sees generation progress, but audio does not progressively play as each generated chunk arrives. Either implement real progressive playback or describe the current behavior as streamed transfer with playback after generation.

#### First-use model download is implicit and opaque

The web app fetches voices on mount. That lazily initializes Kokoro and may trigger a roughly 320 MB Hugging Face download while the UI only shows a loading state and silently swallows errors. A first-run model screen should show:

- exact archive and installed size;
- cache location;
- license and source;
- progress and speed;
- cancel/retry;
- offline failure recovery; and
- a compact alternative if available.

#### Desktop STT setup is visibly broken

At the audit starting point, the model appeared in Settings with a Download action but the server had no matching endpoint. Phase Zero now provides pinned background download, exact byte progress, checksum and required-path validation, safe extraction, load smoke testing, and staged activation. Android additionally provides pause/resume, storage preflight, retry/backoff, notification progress, and durable WorkManager execution; desktop remains process-bound.

#### PWA language is misleading

The current product is a local Python application with a browser UI. It is not a serverless browser TTS app. The service worker is intentionally disabled and unregisters itself, so the documented offline app shell is not present. The README should say “local web interface” until PWA installation, updates, cache invalidation, and offline behavior are deliberately supported.

#### Storage and backup have two sources of truth

History and audio live in localStorage and IndexedDB. Project endpoints use JSON files under a separate server directory. The client does not create/update those projects in the normal flow, yet Settings backup exports them. Users can reasonably believe their history/audio is backed up when it is not.

### Privacy and security boundary

“No data leaves your machine” is plausible for the default inference path, but “private” needs sharper boundaries:

- Default server bind is now loopback; all-interface binding is explicit.
- Default CORS origins are now local development origins; wildcard is explicit.
- There is no authentication.
- Remote/LAN server URLs are suggested in Settings.
- Text and extracted-document previews are redacted unless explicitly enabled.
- Project and log endpoints can read, mutate, export, and delete data.

Recommended boundary:

- loopback-only by default;
- explicit “Allow LAN clients” mode with a warning;
- authentication/TLS/origin allowlist for any remote mode;
- metadata-only logs by default;
- content logging as an opt-in troubleshooting mode with expiry; and
- visible storage/delete/export controls.

## Android audit

### Strengths worth preserving

- On-device inference uses a shared runtime family, sherpa-onnx, for TTS and STT.
- TTS jobs write audio to disk and expose recovery endpoints, so generation can survive a WebView stream drop.
- The app has generation/playback notifications, a wake lock, MediaSession controls, and lock-screen/Bluetooth transport routing.
- Downloads use staging, clean temporary archives, follow redirects, and attempt path-traversal protection.
- The shared Svelte UI minimizes duplicated visual work.

### Clean-build baseline and remaining release blockers

Slice 3 restored a reproducible debug baseline: the wrapper is tracked, API 36
is configured, normal assembly builds the web client, and one official
sherpa-onnx 1.13.4 AAR supplies checksum-pinned matching Kotlin/JNI code.
Android CI and APK-content checks are defined. Remaining release blockers are
hosted-CI evidence, release signing, a release checklist/artifact path, fresh
full model download, and physical-device preservation/playback acceptance.

### Current download flow

For a fresh install:

1. The app loads the shared model catalog and validates every required TTS path.
2. The user sees the exact 333.2 MiB TTS download and an up-front free-space check.
3. Kokoro downloads, verifies, smoke-generates, and activates.
4. The local server starts and the WebView appears.
5. Moonshine remains an explicit optional Settings download.

For an existing TTS-only user, the app opens normally and Settings offers an
explicit STT download. Slice 3 removed the unsolicited upgrade transfer.

### Model-download debt

Phase Zero now owns a versioned shared catalog, runtime/minimum-app metadata,
source/license notes, pinned sizes/SHA-256, required-path checks, HTTP
validation, timeouts, storage preflight, link/path rejection, resumable durable
WorkManager execution, retry/backoff, pause/cancellation, native load/generation
smoke tests, staging, and rollback-safe activation. Remaining product controls
are repair, delete, update, explicit Wi-Fi/cellular preference, and user-visible
rollback. Fresh interrupted-download acceptance still requires a device.

### Android correctness and platform debt

- The STT model is batch-only, uses greedy search, and returns no useful duration or timestamps.
- Long audio remains a bounded full-array decode, capped at 15 minutes/256 MiB; Moonshine inference is windowed and target-device peak memory remains to be measured.
- Android capability gating hides desktop-only batch transcription.
- Unknown Android `/api/*` paths return JSON 404 responses.
- Android ignores stale remote-server preferences and shows its fixed on-device loopback connection.
- `addJavascriptInterface`, navigation, permissions, and file picking are restricted to the exact local app origin. See [Android WebView native bridge guidance](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).
- Generation uses a service declared only as media playback. Foreground-service behavior and policy need Android 14-16 device validation. See [Android foreground-service type guidance](https://developer.android.com/about/versions/14/changes/fgs-types-required).
- English abbreviation/number normalization runs regardless of selected language.
- Both large models can remain resident; earlier roughly 195 MB RAM assumptions do not match the actual unquantized Kokoro package.

## UX and visual-system audit

### Current flow health

| Step | Experience | Health | Reason |
|---|---|---|---|
| 1 | Launch / first model setup | Poor | Desktop download is opaque; Android sizes/identity are wrong; existing Android users may receive an unsolicited STT download |
| 2 | Create or dictate text | Fair | Core text path is clear, but many adjacent actions compete and STT availability is inconsistent |
| 3 | Choose engine/language/voice | Poor | Labels and language availability are not trustworthy; PyTorch default exposes only seven voices |
| 4 | Generate and monitor | Fair | Progress and recovery are good; web playback is not genuinely progressive despite the claim |
| 5 | Listen, seek, queue, and highlight | Good with risks | Strong functional loop; some interactive containers lack keyboard semantics |
| 6 | History and reader view | Fair | Useful, but storage/backup ownership is unclear |
| 7 | Settings and maintenance | Poor | One long page, six large cards, broken model download, and platform-irrelevant controls |
| 8 | Install/update/recover | Poor | PWA is nominal; Android debug build is reproducible, but durable model migration and release acceptance remain incomplete |

### Information architecture recommendation

Keep the primary navigation simple, but reorganize the product as:

- **Create**: text, dictate, single import, Generate;
- **Library**: history, saved projects, batch jobs, exports;
- **Player**: compact persistent player expanding into queue and reading/highlight view;
- **Settings**:
  - Voice & playback
  - Speech recognition
  - Models & storage
  - Privacy & logs
  - Connection (desktop/advanced only)
  - About

On mobile, Settings should open to a category list. Each category gets a short screen with its own title and back navigation. On desktop, a secondary settings rail is appropriate. Do not solve the long scroll only with accordions; categories are more discoverable and provide stable deep links.

### Visual modernization direction

The current design is a competent dark utility UI, but it lacks a distinctive product hierarchy. A later design pass should explore exactly three grounded directions before implementation, for example:

1. Quiet editorial reader: typography, generous reading space, understated waveform motion.
2. Modern voice studio: stronger voice identity, audio visualization, explicit model/download status.
3. Native mobile utility: platform-clean surfaces, compact controls, and restrained motion.

No direction should be selected from prose alone. Capture current Android and desktop screens, generate three visual targets, choose one, then implement against the chosen reference.

### Accessibility risks visible in source

- `user-scalable=no` disables browser zoom.
- The checker-visible clickable-container, dialog-keyboard, and seek-control
  issues were corrected in Slice 4; manual keyboard and assistive-technology
  acceptance is still required.
- Several controls use 10-11 px text.
- Focus trapping, focus restoration, escape handling, and screen-reader state announcements are not proven.
- Animations include spinners, pulsing status, transitions, and expanding player behavior without a verified reduced-motion path.
- The Svelte checker now reports 0 warnings.

This audit does not claim a WCAG result. Required follow-up includes keyboard-only use, VoiceOver/TalkBack, 200-400% zoom/reflow, contrast measurement, dynamic text/font scaling, reduced motion, and device target-size checks.

## Model modernization research

### Recommendation: benchmark, do not chase releases

Kokoro remains a sensible TTS baseline. The current Moonshine model remains a useful STT baseline once correctly labeled. Newer models should earn adoption through the same test set on the target hardware.

### TTS benchmark shortlist

| Candidate | Why test it | Main cautions | Role |
|---|---|---|---|
| Current Kokoro | Known quality, Apache-2.0 weights, existing integration | Large ONNX package; current streaming is chunk-level | Baseline |
| KittenTTS v0.8 Mini | Approximately 80 MB, ONNX, Apache-2.0, eight English voices | Developer preview; quality claims need independent listening | Compact quality candidate |
| KittenTTS v0.8 Nano INT8 | Approximately 25 MB; excellent download target | Reported INT8 issues and reduced capacity | Small-device candidate |
| Pocket TTS INT8 | Genuine audio streaming, long-form focus, voice cloning, active work | Main model is gated CC-BY-4.0 with conditions; voice licenses vary; cloning adds consent/abuse UX | Streaming experiment |
| Supertonic 3 | 99M, ONNX, 31 languages, mobile examples | Official project announced archival/no future support; weights are OpenRAIL-M | Experimental comparison only |
| Piper | Broad language/voice catalog and mature edge use | GPL-3.0 runtime and per-voice licensing | Optional language packs only after legal review |

Sources: [KittenTTS](https://github.com/KittenML/KittenTTS), [Pocket TTS](https://github.com/kyutai-labs/pocket-tts), [Pocket TTS model card](https://huggingface.co/kyutai/pocket-tts), [Supertonic](https://github.com/supertone-inc/supertonic), and [Piper](https://github.com/OHF-Voice/piper1-gpl).

Recommended first bake-off: **current Kokoro versus Kitten Mini, Kitten Nano INT8, and Pocket TTS INT8**. Do not adopt Supertonic as a default dependency while its upstream is being archived.

### STT benchmark shortlist

| Candidate | Why test it | Main cautions | Role |
|---|---|---|---|
| Current Moonshine v1 Base INT8 | Already integrated and functioning | English-only; mislabeled today | Baseline |
| Moonshine v2 Streaming Small | Strong size/accuracy/latency balance; intended for edge streaming | English streaming variant; self-reported benchmarks need device verification | Likely default candidate |
| Moonshine v2 Streaming Medium | Better reported English WER | Larger and slower; optional tier | High-accuracy English pack |
| whisper.cpp Base multilingual | Mature Android/iOS/WASM path; quantization; documented Core ML encoder acceleration | Not architecturally true streaming; larger RAM than disk size | Cross-platform/multilingual control |
| Meta Omnilingual ASR CTC 300M INT8 | 1,600+ languages; sherpa export and mobile bindings | Quality varies by language; punctuation/casing work; 348 MB | Optional broad-language pack |
| NVIDIA Parakeet Unified 0.6B | Strong English desktop reference | 2.47 GB checkpoint; too large for default mobile | Desktop comparison only |

Sources: [Moonshine v2 paper](https://download.moonshine.ai/docs/moonshine_streaming_paper.pdf), [Moonshine project](https://github.com/moonshine-ai/moonshine), [whisper.cpp](https://github.com/ggml-org/whisper.cpp), [Omnilingual ASR model card](https://huggingface.co/facebook/omniASR-CTC-300M), and [Parakeet Unified model card](https://huggingface.co/nvidia/parakeet-unified-en-0.6b).

Moonshine v2's published Small/Medium results—average WER 7.84/6.65 and M3 first-token latency 65.1/129.8 ms—are useful candidate-selection signals, not substitutes for Open Mobile TTS measurements.

### Benchmark protocol

Test devices:

- one 4-6 GB mid/low Android phone;
- Pixel 9 Pro;
- one older supported iPhone;
- one current iPhone;
- Apple Silicon Mac; and
- ordinary x86 laptop, CPU-only plus a common GPU if supported.

TTS measurements:

- blinded preference/listening score;
- pronunciation corpus: names, numbers, dates, URLs, abbreviations, punctuation, and long paragraphs;
- time to first audio;
- real-time factor;
- peak RSS/PSS and cold initialization;
- archive and installed size;
- repeats, skips, stability, and sentence-boundary quality over 10-30 minutes;
- battery and thermal throttling; and
- license/redistribution review for the runtime, base weights, and every bundled voice.

STT measurements:

- WER/CER on clean, noisy, accented, quiet, and long recordings relevant to actual users;
- endpoint and final latency;
- partial-text churn for streaming candidates;
- hallucination behavior on silence/music/noise;
- punctuation, casing, numerals, and names;
- peak memory and cold initialization;
- long-file stability;
- battery/thermal behavior; and
- archive/installed size and license.

Use the same audio, resampling, normalization, text normalization, and device power state for every engine. Publish raw results and keep “best” platform/profile-specific.

## Shared model manager specification

Create one versioned manifest consumed by desktop, Android, and iOS:

```json
{
  "id": "moonshine-v2-small-int8",
  "kind": "stt",
  "runtime": "sherpa-onnx",
  "runtimeVersion": "1.13.x",
  "languages": ["en"],
  "archiveBytes": 0,
  "installedBytes": 0,
  "sha256": "...",
  "requiredFiles": ["..."],
  "sourceUrl": "https://...",
  "license": "MIT",
  "minimumAppVersion": "...",
  "platforms": ["desktop", "android", "ios"]
}
```

The real schema should also include ABI/precision, sample rate, voices, voice licenses, cache version, test phrase/audio, and migration rules. Runtime binaries and Kotlin/Swift bindings must be version-locked with the model support they expose. Android currently documents sherpa-onnx 1.12.25; Moonshine v2 support arrived later, and current upstream is on the 1.13.x line. Review the [sherpa-onnx changelog](https://github.com/k2-fsa/sherpa-onnx/blob/master/CHANGELOG.md) before upgrading.

## iOS plan

### Recommended product approach

Use the shared Svelte frontend for the first iOS version, but do not copy Android conditionals into more `window.iOS` checks. Introduce a platform-capability interface first:

```text
VoiceEngine
TranscriptionEngine
ModelManager
FileImportExport
MediaSession
BackgroundExecution
Diagnostics
```

The Svelte client should consume one typed capability layer. Desktop, Android, and iOS adapters then implement the same contract, and one contract test suite runs against all three.

### Likely iOS stack

- Swift application shell.
- WKWebView for the shared UI initially.
- sherpa-onnx Swift/C or XCFramework for the lowest-risk TTS/STT parity path.
- AVAudioSession plus native player/Now Playing/remote commands.
- UIDocumentPicker/Files and native share/export.
- URLSession background downloads into Application Support.
- manifest, hash, staging, load-smoke-test, atomic activation, and rollback matching Android.
- explicit handling for app suspension, interruptions, Bluetooth routes, headphones, and lock-screen controls.

Sherpa officially supports Swift/iOS; see the [sherpa-onnx iOS build documentation](https://k2-fsa.github.io/sherpa/onnx/ios/build-sherpa-onnx-swift.html). For STT, also benchmark whisper.cpp because it has a documented Core ML encoder path; do not assume generic ONNX automatically uses Apple's Neural Engine without profiling.

### Required spike before committing to the shell transport

Prototype two native/UI communication paths with the same small flow—download a model, synthesize two paragraphs, background the app, play, seek, stop, transcribe a WAV, and export the result:

1. Loopback HTTP parity with Android.
2. Typed WKScriptMessage/native bridge with binary data kept in native storage and referenced by job IDs.

Choose from measured lifecycle stability, streaming behavior, memory copying, debugging, and contract-test reuse. Avoid a third large hand-written API implementation that drifts independently from Python and Kotlin.

### iOS phase gates

1. Product name and icon direction chosen.
2. Model manifest and downloader contract implemented on desktop/Android.
3. Engine and endpoint contract tests exist.
4. Model shortlist benchmarked on at least one older and one current iPhone.
5. Audio session, interruption, route change, background, and lock-screen prototype accepted.
6. Storage, backup, model deletion, privacy, diagnostics, and offline behavior documented.
7. Simulator build is reproducible.
8. Signed in-place physical-device installation preserves Application Support/Documents and settings.
9. Hands-on TTS and STT acceptance completed on physical iPhone/iPad; a build or process alone is not acceptance.

## GitHub and README audit

### Current public experience

The root README is short and easy to scan, but it describes an older product:

- placeholder `git clone <repo-url>`;
- Android described as future despite a substantial implementation;
- STT, batch transcription, projects, and export omitted;
- wrong model/voice/size/streaming claims;
- four broken documentation links;
- no screenshots or demo;
- no platform/install-status matrix;
- no actual LICENSE file;
- no top-level contributing/security guidance;
- only server CI; and
- no clear Android download/release path.

### What to borrow from HarkinianPad

Borrow HarkinianPad's evidence hierarchy, not its game-specific length:

1. Centered one-sentence promise.
2. CI/platform/privacy badges that link to real evidence.
3. A current hero image or short GIF.
4. “Choose your version” and install-status tables.
5. Tested/working matrix with limits stated beside claims.
6. Current desktop and Android screenshots.
7. Exact first-launch model behavior and sizes.
8. FAQ using `<details>`.
9. Project map.
10. Contribution, security, license, model provenance, and acknowledgements.

### Proposed README outline

1. Product name, one-sentence promise, badges, hero.
2. What it is—and what “web” means.
3. Choose your version: local desktop web UI, Android on-device, iOS planned.
4. Install status and real release/download links.
5. Quick start with the real clone command.
6. First run: prerequisites, exact model choices/sizes/cache locations.
7. Current screenshots and a concise feature tour.
8. Models and privacy/storage boundary.
9. What works/tested matrix and known limits.
10. Architecture diagram.
11. Build and release instructions.
12. FAQ/troubleshooting.
13. Project map, contributing, security, license, and acknowledgements.

Also add a GitHub social-preview image, precise repository description, platform/model topics, structured issue forms, `CONTRIBUTING.md`, `SECURITY.md`, and release artifacts/checksums when those artifacts actually exist.

## Icon and brand direction

Resolved on 2026-08-11 by [ADR 016](decisions/016-two-panel-waveform-icon.md).
The selected cross-platform mark uses two facing text panels around a
negative-space waveform. It replaces the generic Android speaker icon and the
previous placeholder web artwork.

The cross-platform icon:

- communicate voice and text in one simple silhouette rather than a generic speaker alone;
- remain recognizable at 16-32 px;
- avoid letters that become obsolete if the name changes;
- work inside Android adaptive-icon masks and Apple's square icon system;
- avoid transparent corners in the iOS master;
- supply monochrome Android themed-icon artwork;
- use the same core mark for favicon, PWA, Android, iOS, GitHub social preview, and README;
- be original and avoid resemblance to OpenAI, Apple Voice Memos, Google Recorder, ElevenLabs, or common accessibility marks; and
- include light/dark marketing lockups separately from the app icon.

Delivered assets:

- 1024x1024 iOS master;
- Android foreground/background/monochrome layers with safe-zone check;
- PWA maskable 512 and 192 PNGs;
- favicon set;
- README logo/wordmark;
- GitHub social preview; and
- source/provenance/license note.

The canonical source files and export guidance live in
[`assets/brand/`](../assets/brand/). Visual QA evidence, including Android
adaptive-mask validation, lives in
[`docs/audits/brand-2026-08-11/`](audits/brand-2026-08-11/).

## Test and release matrix

### Required CI

- Python 3.10-3.12 server tests and locked dependency install (Kokoro 0.9.4
  does not support Python 3.9).
- Client format, type/Svelte check, unit tests, and production build.
- Shared API contract suite against FastAPI and Android NanoHTTPD.
- Android clean-clone debug/release assembly for supported ABIs.
- Android unit and instrumentation smoke tests.
- Docker build and cold-start/model-cache test.
- README/link checker and license/model-manifest validation.
- Dependency and secret scanning.

### Required physical acceptance

Android:

- first install and model download over Wi-Fi/cellular/interruption;
- repair, update, rollback, delete, and low-storage behavior;
- TTS/STT quality and performance tiers;
- microphone, documents, audio import, batch behavior, backup/restore;
- playback/background/lock-screen/Bluetooth/headphones/interruption;
- process kill and stream recovery;
- low-memory device behavior; and
- Android 14-16 foreground-service and notification behavior.

Web/desktop:

- clean first run on macOS, Windows, and Linux;
- Docker cold/warm cache;
- offline restart after model installation;
- long TTS/STT jobs;
- multiple clients/concurrent requests;
- local-only versus explicit LAN mode;
- browser storage/export/restore; and
- real progressive playback if that claim remains.

iOS later:

- Simulator build and UI smoke;
- signed update-in-place with data preservation;
- iPhone and iPad first-run downloads;
- background and audio-route matrix;
- microphone/Files/export permissions;
- model benchmark and thermal/battery behavior; and
- hands-on TTS/STT playback acceptance.

## Recommended modernization sequence

### Phase 0: truth and unblockers

- Decide product name.
- Correct model identities, sizes, languages, versions, and streaming claims.
- Finish the full model-manager UX and Android/client API parity; the desktop STT download blocker is fixed.
- Add LICENSE and repair README/docs links.
- Add real web/PWA icons or stop advertising installability.
- Bind loopback by default and redact logs.

### Phase 1: reproducibility and safety

- Lock dependencies.
- Reproduce Android from a clean clone and add CI.
- Add shared model manifest/checksums and safe installers.
- Add frontend, contract, Android, Docker, and link tests.
- Unify storage/backup semantics.

### Phase 2: measured model bake-off

- TTS: Kokoro, Kitten Mini, Kitten Nano INT8, Pocket TTS INT8.
- STT: current Moonshine v1 Base, Moonshine v2 Small/Medium, whisper.cpp Base; optional Omnilingual tier.
- Publish device results and choose profile-specific defaults.

### Phase 3: product and visual redesign

- Capture current desktop/Android flows.
- Explore three image-based directions.
- Select one direction.
- Rebuild navigation, Settings, model management, first run, storage, and error states.
- Create final icon family and README media.
- Run accessibility and physical-device acceptance.

### Phase 4: distribution quality

- Decide whether a real PWA is worth supporting.
- Produce signed/reproducible Android artifacts and release notes/checksums.
- Publish the HarkinianPad-style README and public support surfaces.

### Phase 5: iOS

- Implement the accepted platform-capability and model-manager contracts in Swift.
- Select shell transport from the lifecycle spike.
- Benchmark models on iPhone.
- Build, sign, preserve data on updates, and complete physical-device acceptance.

## Explicit non-goals for the next implementation pass

- Do not replace Kokoro immediately.
- Do not call the current STT Moonshine v2.
- Do not add an LLM transcript fixer by default.
- Do not start a visual reskin before P0 truth and broken-flow fixes are scoped.
- Do not generate the final icon before choosing the product name and visual direction.
- Do not create iOS as a third drifting API implementation.
- Do not advertise PWA, multilingual, streaming playback, model size, privacy, or platform support beyond verified behavior.

## Recommended immediate decision workshop

Before implementation, align on five decisions:

1. Final product name: Open Mobile TTS or Open Mobile Voice.
2. Supported distribution targets for the next release: desktop local web UI, Android APK/local build, or both.
3. Whether STT is a headline feature now or remains experimental until real Moonshine v2 is benchmarked.
4. Whether the browser-install/PWA path is worth supporting, or whether “local desktop web UI” is the honest permanent model.
5. Which devices will define performance acceptance for the model bake-off and Android/iOS minimum requirements.

Once those are settled, Phase 0 can be turned into a narrow implementation plan without committing prematurely to a new model or a visual direction.
