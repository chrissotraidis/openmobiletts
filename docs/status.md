# Project Status

**Last updated:** 2026-08-27

**Baseline:** signed release tag `v3.1.0-preview.1` at
`ad643834a7c69632b4be8c68fdb53142cd60697e`

**Release state:** signed Android preview published on GitHub; stable-release
and Play Store acceptance remain open

## Current verification

| Check | Result | Boundary |
|---|---|---|
| Product version | Pass | Tagged source and the published Android manifest report `3.1.0-preview.1`; Android `versionCode` is `3010001` |
| Desktop TTS | Pass | Real framed audio/timing response; current listening quality not re-accepted |
| Desktop STT | Pass | Pinned Moonshine v1 Base archive installed, smoke-loaded, and transcribed locally |
| Server tests | 58 passed | 7 dependency deprecation warnings |
| Client diagnostics | Pass | 0 errors and 0 warnings |
| Client production build | Pass | Current categorized Settings/client build generated |
| Benchmark harness | Pass | TTS/STT runners compile; one Mac Kokoro HTTP baseline validates measurement, not model selection |
| Android strict compile | Pass | WorkManager 2.10.5 and the shared client compile under strict dependency verification on local and clean GitHub runners |
| [Android signed preview](https://github.com/chrissotraidis/openmobiletts/releases/tag/v3.1.0-preview.1) | Pass, preview | Public APK is 138,451,751 bytes; SHA-256 `978c5e1730758015d4bc2a60d54ac0bf844f45d7c039d52ce4a267a1ea5b39a0`; v2 signature certificate SHA-256 `386fa5cdb632e56b33ce6919af616b19841277bedda755fdb0a8297f510548a4` |
| Android debug APK | Pass | Current OMTTS-label/continuous-model-switch build is 142,626,776 bytes, v2 debug-signed, and strict-verified; SHA-256 `4b29f75e211160ff5c07d5bef12fb7e4071e9610a3f697b027e5bea07db71455` |
| Android CI targets | Pass | Clean Linux CI still builds and audits the debug APK plus unsigned release target without access to release secrets |
| Android emulator | Pass, bounded | API 34 ARM64 in-place updates preserved prior data; Pixel Launcher renders the full OMTTS label; real Kitten generation/Kokoro rollback and the continuous selected-model transition passed |
| Android physical phone | Pass, bounded | Pixel 9 Pro XL exposed and then passed the process-isolation fix: Kokoro, Mini, Micro, and Kokoro again produced full-duration 24 kHz jobs; final in-place APK kept every model and Kokoro active; listening acceptance remains with the owner |
| iOS/iPadOS | Planned | No app implementation exists |

## Feature truth

| Feature | Status | Current boundary |
|---|---|---|
| Desktop Kokoro TTS | 🟡 Working baseline | Implicit first model download; browser buffers full audio before playback |
| Android TTS | 🟡 Stable plus experiments | Kokoro remains first-run/default; optional Kitten Mini/Micro install and persist; every model change restarts into one clean sherpa engine per process and the full physical model cycle passed |
| Desktop STT | 🟡 Experimental | Truthful Moonshine v1 Base English INT8 and pinned installer |
| Android STT | 🟡 Experimental/optional | Explicit Settings download; WorkManager progress, retry, pause, and partial resume implemented |
| Document/audio import | 🟡 Bounded baseline | Android rejects inputs over 15 minutes/256 MiB and windows Moonshine input; representative memory/device acceptance remains |
| Batch transcription | 🟡 Desktop-only | Hidden on Android through the capability contract |
| Export | 🟡 Implemented | Desktop PDF verified; Android share and cross-platform PDF rendering need device review |
| Player/History/queue | 🟡 Working | Visible History is client-owned; manual assistive-tech/storage tests remain |
| Data management | 🟡 Implemented baseline | Versioned History backup/restore, retention, and cached-audio cleanup; no audio in JSON backup |
| Settings | 🟡 Redesigned slice | Voice, Connection, Models, Data, and App categories; required TTS and optional STT setup are explicit; Android model switching preserves section/card context across its clean restart |
| PWA | 🔴 Not supported | Service worker disables itself; a local Python/native backend is required |
| Android release | 🟡 Signed preview published | Tagged APK, checksum, signature, packaged assets, physical Pixel debug cycle, and direct download pass; signed clean-install and wider stable matrix remain |

## Modernization phases

| Phase | Status | Evidence / next gate |
|---|---|---|
| Phase 0: truth and reproducibility | 🟡 Near exit | Shared catalog, locks, safety defaults, model truth, CI definitions, capability contract, license/repo baseline and native activation smoke done; interrupted-download/device acceptance remains |
| Phase 1: measured model bake-off | 🟡 Harness started | Current Kokoro Mac baseline recorded; Kitten Mini/Micro are functionally integrated as opt-in Android experiments without comparative performance claims |
| Phase 2: product architecture | 🟡 Partial | Capability contract and visible data ownership resolved; bounded inference/long-audio work remains |
| Phase 3: shared UI | 🟡 Partial | Settings navigation and checker-visible accessibility done; broader visual direction and manual device/assistive-tech QA remain |
| Phase 4: brand/repository/Android release | 🟡 Preview published | Icon family, OMTTS label, screenshots, consumer install guide, stable signing identity, tagged workflow, checksum, and public APK pass; stable hardware/lifecycle matrix remains |
| Phase 5: iOS | 🔵 Planned | Runtime/transport decisions follow shared model/device evidence |

## Phase Zero gates

- [x] One product name/version source across server, client, and Android.
- [x] Current models report exact family, precision, language, and measured size.
- [x] Desktop STT installs from Settings with pinned integrity validation.
- [x] Loopback and metadata-only logs are safe defaults.
- [x] Hash-locked Python, tracked npm lock, deterministic UI build, and CI definitions.
- [x] Android uses version-locked sherpa Kotlin/JNI and generated web assets.
- [x] Desktop/Android publish the same schema-v1 capability shape.
- [x] Client diagnostics pass with 0 errors and 0 warnings.
- [x] Active README/feature docs no longer advertise unsupported PWA,
  multilingual, release, model, or progressive-playback behavior.
- [x] Repository Apache-2.0 grant and model/runtime provenance ledger exist.
- [x] Complete current archive/per-voice document review; models remain runtime
  downloads and missing upstream per-voice manifests are explicit boundaries.
- [x] Keep staged native load validation for STT; use integrity validation plus
  clean-process functional generation for TTS (Decision 021).
- [ ] Complete physical interrupted-download acceptance.

## Remaining execution order

1. Exercise fresh TTS, optional STT, pause/resume, retry, process death,
   notification, and low-space states on an emulator/physical phone.
2. Compare the now-integrated Kitten experiments and future STT candidates on
   target devices only if a default/performance decision is needed; Kokoro
   remains unchanged meanwhile.
3. Finish manual assistive-technology/device QA and model/voice acceptance;
   document-level provenance and emulator launcher-mask brand reviews now pass.
4. Collect preview installation feedback and close the signed clean-install,
   low/mid-device, background, interruption, and thermal gates before promoting
   a stable release or pursuing Play Store distribution.
5. Begin the iOS shell only after the shared model/runtime choices are stable.

## Current documents

- [Modernization plan](PHASE_ZERO_MODERNIZATION_PLAN.md)
- [Technical debt audit](TECH_DEBT_AND_MODERNIZATION_AUDIT.md)
- [Model provenance](MODEL_PROVENANCE.md)
- [Release checklist](RELEASE_CHECKLIST.md)
- [Signed Android preview decision](decisions/024-signed-android-github-preview.md)
- [Open decisions](unknowns.md)
