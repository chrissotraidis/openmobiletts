# Changelog

Notable project changes are recorded here. The project has no published stable
release yet.

## Unreleased

No changes yet.

## 3.1.0-preview.1 - 2026-08-27

### Changed

- Corrected current STT identity to Moonshine v1 Base English INT8.
- Added repository-local Python environment isolation and locked builds.
- Restored a reproducible Android API 36 debug build with verified sherpa-onnx.
- Added pinned, integrity-checked desktop and Android model installation paths.
- Added platform capabilities and gated unsupported shared controls.
- Reorganized Settings into focused Voice, Connection, Models, Data,
  and App categories.
- Made visible History and its IndexedDB audio cache the owned user library.
- Consolidated model/runtime/integrity/license metadata into one catalog used
  by Python and Android.
- Limited the native voice surface to 28 accepted English US/UK speakers.
- Bounded Android transcription to 15 minutes/256 MiB with windowed Moonshine
  decoding and lazy optional STT initialization.
- Restricted Android WebView navigation, native bridge permissions, and
  cleartext traffic to the in-process loopback origin.
- Replaced invalid multi-RIFF WAV concatenation with one disk-backed,
  header-patched recovery file.
- Added optional Android Kitten Mini and Micro voice models with explicit
  experimental labels, verified downloads, removal, and Kokoro rollback.
- Isolated Android TTS model changes across clean application processes after
  physical-device testing exposed sherpa engine-release corruption.
- Added a branded continuous model-switch transition that returns to the
  selected model card instead of resetting navigation.
- Shortened the Android launcher/system label to OMTTS while retaining Open
  Mobile TTS as the full product name.

### Added

- Library backup, additive restore, retention, and audio-cache usage reporting.
- Apache License 2.0, security policy, contribution guide, issue/PR templates,
  model provenance, and release checklist.
- Selected two-panel waveform icon family, physical Android screenshots, and
  Android/iOS/web brand exports.
- Strict debug and unsigned release-target Android assembly with release-lint
  dependency verification.
- A tagged GitHub workflow that builds, verifies, signs, checksums, and publishes
  the installable Android preview.
- Physical Android generation and in-place preservation evidence for Kokoro,
  Kitten Mini, Kitten Micro, Moonshine, preferences, and model switching.
- A complete public README with desktop/Android installation guides, current
  physical-device screenshots, model guidance, FAQ, architecture, and support
  boundaries.

### Known boundaries

- The wider low/mid-range hardware, fresh-download, background, interruption,
  and thermal matrix remains pending before stable-release acceptance.
- Genuine browser progressive playback is not implemented.
- Final model bake-off and iOS implementation are pending.
