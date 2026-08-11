# Phase Zero Modernization Plan

**Prepared:** 2026-08-10

**Status:** Baseline slices and selected brand assets implemented locally;
device acceptance, model comparison, and final Android release work remain

**Input:** [Technical debt and modernization audit](TECH_DEBT_AND_MODERNIZATION_AUDIT.md)

## Purpose

Phase Zero establishes a truthful, reproducible baseline before model swaps,
visual redesign, Android release work, or an iOS implementation. It prevents
the project from modernizing its appearance while preserving stale model
claims, broken setup paths, and three drifting platform backends.

## Implementation checkpoint: 2026-08-10

Completed in the first bounded slice:

- canonical `VERSION` metadata for the Python API and Android build, with the
  shared client showing the runtime-reported version;
- truthful Moonshine v1 Base English INT8 identity across desktop, shared UI,
  transcript metadata, and Android responses/comments;
- a pinned desktop STT catalog with exact archive size, required files, and
  SHA-256;
- background Settings download with byte progress, safe extraction, required
  file validation, model load smoke test, staging, and atomic activation;
- loopback-only and local-origin defaults; and
- redacted text/document/transcript log previews unless explicitly enabled.

Verified through the current slice: 58 server tests pass, the client production build passes, and the real
239.2 MB STT archive passed checksum, safe extraction, required-file, and
sherpa load-smoke validation through the new installer. The client quality
gate now passes with 0 errors and 0 warnings.

Slice 2 additionally verified a clean 118-package Python 3.11 installation
from the universal hash lock, all 48 tests in that environment, real PDF
generation, `npm ci`, launcher source-hash invalidation/cache hits, Compose
configuration, and CI workflow syntax. Docker Hub timed out during base-image
metadata lookup, so a complete container build is not yet claimed.

Slice 3 restored the Gradle wrapper, moved compile/target SDK to API 36, pinned
sherpa-onnx 1.13.4 as one checksum-verified AAR, wired `npm ci` and the Svelte
build into Android assembly, added Android CI/APK content checks, and hardened
model download verification/activation. A 139,410,379-byte debug APK built,
updated an existing API 34 ARM64 emulator while preserving its models, launched
cold in 2,294 ms, and returned healthy model/API state. Fresh full-download,
physical-device, playback, and performance evidence remain open. Long model
downloads now run as unique foreground WorkManager 2.10.5 jobs with connected-
network constraints, retry/backoff, durable progress, pause, and partial resume.

Slice 4 added a versioned platform-capability schema
to FastAPI and NanoHTTPD, JSON fallbacks for unknown API paths, and shared UI
gating for backend-only controls. It also cleared the Svelte checker baseline,
made that check blocking in desktop CI, and moved the default launcher into a
repository-local `.venv`. A real isolated launch initialized Kokoro voices,
and Android rebuilt successfully with the new web/API contract. It also made
the visible client History/IndexedDB library authoritative, implemented
versioned backup/restore and retention, replaced the Settings scroll with five
responsive categories, added the repository license/security/contribution/
issue/release baseline, and synchronized active feature documentation.

## Remaining implementation order

The first slice removed the most misleading and user-visible blockers. Finish
Phase Zero in this order:

### Slice 2: desktop reproducibility — implemented

- [x] Track the npm lockfile and replace `npm install` with `npm ci` in
  reproducible paths.
- [x] Add universal production/development Python locks with hashes.
- [x] Declare ReportLab and verify a real PDF response from the clean lock.
- [x] Fix Hugging Face cache detection and the Docker cache volume.
- [x] Replace the stale-build existence check in `run.py` with deterministic
  source/build invalidation.
- [x] Add Python 3.10-3.12 server, client, and API smoke checks to CI; the
  client quality check became blocking after Slice 4 cleared the baseline.

**Local exit:** clean locked installs, current UI build, correct cache reuse,
tests, PDF export, and launcher checks pass. Hosted CI and a complete Docker
image build remain external acceptance checks.

### Slice 3: Android reproducibility and model integrity — implemented baseline

- [x] Commit a complete Gradle wrapper.
- [x] Pin and checksum sherpa-onnx Kotlin/JNI through the official 1.13.4 AAR.
- [x] Make Android assemble depend on `npm ci` and the Svelte production build.
- [x] Move compile/target SDK to API 36 with documented toolchain versions.
- [x] Replace marker-file checks with archive size/SHA-256, required-file,
  HTTP, storage, safe-extraction, staged activation, and rollback validation.
- [x] Add foreground WorkManager execution, connected-network policy,
  resume, retry/backoff, cancellation, and durable progress.
- [x] Add staged native recognizer validation for STT. TTS uses integrity
  validation followed by clean-process functional generation because physical
  testing found in-process sherpa TTS release/recreation unsafe (Decision 021).
- [x] Add Android CI and APK packaging checks.
- [x] Run bounded emulator update/launch/API acceptance; separately complete
  fresh model download, physical-device preservation, and hands-on playback
  acceptance.

**Exit:** a clean clone can build a known APK whose runtime, web assets, models,
checksums, and source versions are auditable.

### Slice 4: shared contracts and frontend quality

- [x] Define and test one versioned FastAPI/NanoHTTPD capability contract.
- [x] Gate desktop-only features in the shared UI and return JSON for unknown
  Android API paths.
- [x] Clear all 49 client errors and 13 warnings.
- [ ] Complete manual screen-reader, zoom/reflow, contrast, and physical-device
  focus acceptance; checker-visible keyboard/dialog/reduced-motion work passes.
- [x] Resolve the history/project/audio backup source of truth and implement
  matching client backup, restore, retention, and cache deletion.
- [x] Complete the public documentation, license, security, contributing,
  issue-template, and release-checklist baseline.

**Exit:** both backends satisfy the same declared contract, the shared client
passes its quality gate, and public documentation contains no unsupported
claim.

Model swaps still depend on the measured bake-off. Settings information
architecture has advanced early because it repaired a documented usability and
data-ownership problem; broader reskin, final icon/banner, and iOS choices
remain separate decisions.

## Five working decisions

These are the recommended defaults for planning. They are deliberately easy to
reverse before implementation begins.

### 1. Keep the name Open Mobile TTS for the next stabilization release

**Working decision:** Keep the repository, package, and product name **Open
Mobile TTS** for now. Describe it as a private local voice workspace that also
includes experimental speech-to-text.

**Reasoning:** The existing repository, Android package, user recognition, and
TTS experience already use this name. Renaming to Open Mobile Voice before STT
is correctly identified, downloadable, benchmarked, and platform-consistent
would create churn without fixing the product. Revisit the name after the model
bake-off and STT acceptance pass.

### 2. Stabilize desktop/local web and Android together

**Working decision:** The next release target is:

- desktop/local web interface on macOS, Windows, and Linux; and
- Android build-from-source plus a reproducible APK pipeline.

iOS remains a planned platform. No App Store, TestFlight, or iOS build is
implied. No Android release asset is implied until a clean build, package,
checksum, install, launch, model download, inference, and data-preservation
path is verified.

### 3. Treat current STT as experimental

**Working decision:** TTS is the stable core. STT is experimental until the
current engine is truthfully labeled **Moonshine v1 Base English INT8**, the
download path works on both backends, and Moonshine v2 Small/Medium are
benchmarked on target devices.

Do not add an LLM transcript corrector by default. Deterministic formatting
preserves the product's local, compact, predictable character.

### 4. Describe the desktop product as a local web interface, not a PWA

**Working decision:** Stop claiming an offline/installable PWA. The Svelte UI
is served by the local Python application. The tracked service worker disables
itself. Real icon assets now exist, but PWA support can return later only if its
installation, offline shell, cache invalidation, updates, and model dependency
are deliberately designed and tested.

### 5. Use an evidence ladder for device acceptance

**Working decision:** Separate these claims:

1. source/build/configuration evidence;
2. package and signature evidence;
3. emulator/simulator launch evidence;
4. physical installation and process evidence;
5. preserved settings/history/models across an update; and
6. hands-on TTS/STT/playback acceptance.

Current local test assets:

| Asset | Available now | Valid use |
|---|---|---|
| Apple Silicon Mac | Yes | Desktop CPU baseline and local web acceptance |
| Pixel 3a API 34 ARM64 AVD | Yes; update/launch/API smoke passed | Android functional checks, not performance acceptance |
| Pixel 9 Pro | User-referenced target; reconnect when available | High-end Android model benchmark |
| 4-6 GB physical Android phone | Not yet selected | Low/mid-tier memory, thermals, and default-model decision |
| iPhone 14 | Paired | Future physical iOS acceptance |
| 12.9-inch iPad Pro (6th generation) | Paired | Future tablet/iOS acceptance |
| iOS 26.5 simulators | Available | Future iOS UI/build smoke tests |

## Phase Zero deliverables

### A. Product truth

- [x] Use Open Mobile TTS consistently in active code, UI, manifests, and current docs.
- [x] Identify the current STT model as Moonshine v1 Base English INT8.
- [x] Share the current application version between the Python API and Android
  package, with the client reading the runtime value.
- [x] Report and license-audit every current TTS model/archive/voice surface;
  missing upstream per-voice manifests are explicit redistribution boundaries.
- [x] Remove or capability-gate every unverified multilingual claim; the native
  registry now exposes only 28 accepted English US/UK speakers.
- [x] Complete the active-document and public-claim sweep; historical
  `_reference`/`_archive` materials remain explicitly non-current.

Original scope:

- Use Open Mobile TTS consistently in code, UI, docs, manifests, and package
  metadata.
- Report the actual models and precision:
  - desktop default TTS: Kokoro-82M through PyTorch;
  - optional desktop/Android TTS: sherpa-onnx
    `kokoro-multi-lang-v1_0`;
  - desktop/Android STT: sherpa-onnx
    `sherpa-onnx-moonshine-base-en-int8` (Moonshine v1).
- Correct model archive, installed, and expected peak-memory numbers from
  measurements.
- Remove unverified multilingual claims.
- Describe browser audio transfer separately from progressive playback.
- Remove unsupported PWA/offline-shell claims.
- Establish one generated application version shared by Python, Svelte,
  Android, exports, and docs.

### B. Reproducible Android baseline

- [x] Commit a complete Gradle wrapper.
- [x] Verify APK assembly locally with Android Studio's bundled JBR 21.
- [x] Upgrade compile/target SDK to the supported API 36 toolchain.
- [x] Version-lock matching sherpa-onnx Kotlin/JNI in its official AAR.
- [x] Pin the AAR and all Android build dependencies by SHA-256.
- [x] Wire the Svelte production build into Android assemble.
- [x] Return JSON errors for unknown `/api/*` routes.
- [x] Add Android/client API contract tests.
- [x] Add debug and release assembly CI definitions.
- [x] Document signing and APK audit separately from physical-device acceptance.

The local host has Android Studio, SDK platforms 34-36, build tools 36.0.0,
ADB 37.0.0, JBR 21, and a Pixel 3a API 34 ARM64 AVD. The repository now owns
the reproducible build path; hosted CI and release signing remain separate.

### C. Model manager baseline

One versioned catalog is now shared by desktop, Android, and future iOS:
[`models/model-catalog.v1.json`](../models/model-catalog.v1.json).
Each entry must provide:

- stable model ID and human label;
- model family/version and precision;
- TTS/STT role;
- runtime and required runtime version;
- source and license;
- per-voice license where applicable;
- supported and verified languages;
- archive and installed bytes;
- SHA-256;
- required files;
- platform/ABI compatibility;
- minimum app version;
- model-specific smoke test; and
- migration/rollback rules.

The installers must support storage preflight, explicit Wi-Fi/cellular choice,
progress, retry, cancellation, resumable transfer when available, staging,
hash verification, load verification, atomic activation, repair, deletion,
and rollback. Existing users must opt in before a large new STT download.

### D. Desktop safety and setup

- [x] Bind to loopback by default.
- [x] Make LAN binding and cross-origin access explicit configuration.
- [x] Keep remote/VPS use unsupported until authentication, TLS, and an origin
  allowlist exist.
- [x] Redact text/document/transcript content from default logs.
- [x] Fix Hugging Face cache detection and Docker persistence.
- [x] Implement and verify the STT model download endpoint used by Settings.
- [x] Declare PDF export dependencies.
- [x] Prevent stale frontend builds after source changes.
- [x] Track JavaScript and Python dependency locks.

### E. Documentation baseline

- Root README: current platform table, accurate models, real clone command,
  install status, limits, privacy boundary, test status, and current docs.
- Android README: actual architecture, model sizes, prerequisites, reproducible
  build status, and no unsupported APK claim.
- Client README: shared Svelte UI, supported workflows, disabled PWA state, and
  frontend quality-gate status.
- Server README: actual POST endpoints, both TTS backends, STT identity, cache
  locations, local bind guidance, and security boundary.
- Keep [status.md](status.md), [overview.md](overview.md), and
  [unknowns.md](unknowns.md) synchronized with implementation.
- Treat `_reference/` and `_archive/` as historical input, not current truth.

## Modernization roadmap

### Phase 0: truth and reproducibility

Deliver the items above without changing default models or redesigning the UI.

**Exit criteria:** current docs match code; desktop STT setup works; Android
builds from a clean clone; shared contracts pass; no misleading model, PWA,
privacy, release, or language claims remain.

### Phase 1: measured model bake-off

TTS shortlist:

1. current Kokoro baseline;
2. KittenTTS v0.8 Mini;
3. KittenTTS v0.8 Nano INT8; and
4. Pocket TTS INT8.

STT shortlist:

1. current Moonshine v1 Base INT8 baseline;
2. Moonshine v2 Streaming Small;
3. Moonshine v2 Streaming Medium;
4. whisper.cpp Base multilingual control; and
5. optional Omnilingual ASR pack for broad-language research.

Supertonic 3 may be measured as an experiment, but its announced archival
status makes it unsuitable as the default dependency. Newness alone is not an
adoption criterion.

**Exit criteria:** blind listening/accuracy results, time to first output,
real-time factor, peak memory, disk size, battery, thermals, long-input
stability, and license review are published for the target devices.

### Phase 2: product architecture cleanup

- One typed platform-capability interface for desktop, Android, and iOS.
- One API contract suite.
- One storage/backup source of truth. **Implemented for the visible client
  library through Decision 014.**
- Bounded inference and batch queues.
- Streaming/segmented long-audio processing.
- Honest platform feature gating.

**Exit criteria:** the shared UI cannot call a feature unsupported by its active
backend, and backup/restore covers the data users see.

### Phase 3: shared UI redesign

Preserve the simple Create/Library/Settings mental model. Redesign:

- first-run and model downloads;
- Create actions and progressive disclosure;
- player/queue/reader motion;
- settings category navigation (implemented and visually audited);
- model and storage management;
- status, empty, error, repair, and offline states; and
- accessibility semantics, focus, zoom, contrast, reduced motion, and screen
  reader behavior.

Use the visual-design workflow: capture the current desktop and Android flows,
generate exactly three grounded design directions, select one, implement it,
and compare the implementation against the selected reference.

**Current evidence:** responsive Settings categories, desktop side-by-side
visual QA, browser interaction checks, reduced-motion CSS, and a 0/0 Svelte
check pass. Manual narrow-device and assistive-technology acceptance remains.

**Exit criteria:** responsive visual QA plus keyboard, VoiceOver/TalkBack,
zoom/reflow, target-size, contrast, and reduced-motion checks pass.

### Phase 4: brand, README, and Android release quality

- [x] Select visual option 1 and record Decision 016.
- [x] Generate a cross-platform icon family: iOS master, Android adaptive and
  monochrome layers, web/favicons, and README mark.
- [x] Create a banner/hero using the verified categorized Settings screen.
- [x] Publish the HarkinianPad-style README with evidence-bound platform,
  model, install, and test matrices.
- [x] Add contributing, security, issue templates, release checklist, and
  development-artifact checksums; add release assets when final-phone evidence
  exists.

**Exit criteria:** public docs, release assets, checksums, package contents, CI,
and tested behavior agree.

### Phase 5: iOS

- Swift shell with the shared capability contract.
- Benchmark loopback HTTP versus a typed native bridge before choosing.
- sherpa-onnx Swift/C baseline; separately benchmark whisper.cpp/Core ML for
  STT.
- Native model downloads, audio session, Now Playing/remote commands, Files,
  export/share, interruptions, and background behavior.
- Simulator, signed physical update, data preservation, and hands-on TTS/STT
  acceptance.

**Exit criteria:** a signed in-place update preserves Application Support,
Documents, models, history, and settings; physical iPhone/iPad TTS/STT and
audio-route acceptance is recorded.

## README and brand preparation brief

The current README can be made truthful before visual assets exist. The final
visual version should add:

1. banner or product hero;
2. CI/platform/local-processing badges backed by evidence;
3. desktop and Android screenshots;
4. choose-your-version and install-status tables;
5. exact model/size/language table;
6. privacy and storage boundary;
7. tested/remaining-work matrix;
8. expandable FAQ;
9. project map; and
10. contributing, security, license, provenance, and release links.

The icon brief should combine voice and text in one simple silhouette, avoid
letters tied to a possible future rename, remain clear at favicon size, fit
Android adaptive masks and the Apple icon square, and avoid resemblance to
existing voice/AI brands. Visual option 1 was selected on 2026-08-11; its
two-panel waveform mark is now the shared platform master.

## Available tools and how they fit

| Capability | Available | Planned use |
|---|---|---|
| Repository inspection/editing | Yes | Source truth, docs, narrow patches, diff review |
| Local server and API checks | Yes | TTS/STT smoke tests, endpoint contracts, privacy/bind checks |
| Python and Svelte quality gates | Yes | Server tests, frontend build/type/accessibility checks |
| GitHub repository access | Yes | Current Actions/releases, README validation, later PR/release work when requested |
| Primary-source web/Hugging Face research | Yes | Model/runtime/license monitoring and benchmark inputs |
| Android Studio and SDK | Yes | Gradle sync/build, emulator launch, profiling, Logcat, APK inspection |
| ADB and Android emulator | Yes | Install/launch/read-back, device logs, functional smoke tests |
| Xcode, Apple simulators, paired iPhone/iPad | Yes | Future iOS shell, signing, install/update, runtime evidence |
| Image generation | Yes | Three visual directions, icon/banner concepts after the brief is approved |
| In-app browser review | Yes | Local UI review; automated screenshot control must be revalidated before the visual audit |

## Change discipline

- Documentation and planning may proceed now.
- Phase Zero implementation should use small reviewed slices.
- Model defaults do not change until benchmark evidence exists.
- UI code does not change until a visual direction is selected.
- Android installation does not imply model, playback, or hands-on acceptance.
- iOS work does not begin by copying the Android backend route by route.
- No release, PWA, multilingual, privacy, streaming, or licensing claim is
  published without current evidence.
