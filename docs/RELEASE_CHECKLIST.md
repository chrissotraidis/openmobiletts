# Release Checklist

No release is ready until every applicable item is evidenced. A build alone is
not release acceptance. The signed GitHub preview is published with the stable
acceptance gates below still explicitly open.

## Current preview-release evidence

- Locked server/client checks and strict Android dependency verification pass.
- Debug and unsigned release-target APKs assemble with the built web entry,
  shared model catalog, and required arm64-v8a/x86_64 sherpa JNI libraries.
- The current debug APK passed an API 34 ARM64 in-place emulator update while
  preserving downloaded models and preferences.
- The current debug APK also passed a physical Pixel 9 Pro XL in-place update,
  Kokoro/Kitten generation cycle, selected-model transition, and preservation
  readback.
- Tag `v3.1.0-preview.1` produced a signed, aligned, installable APK from
  `ad643834a7c69632b4be8c68fdb53142cd60697e`.
- The public 138,451,751-byte asset and published checksum were downloaded back
  and matched SHA-256
  `978c5e1730758015d4bc2a60d54ac0bf844f45d7c039d52ce4a267a1ea5b39a0`.
- Signed clean-install acceptance, fresh required-model downloads, physical STT,
  and the wider hardware/background matrix remain open below.

## Source and CI

- [x] Version and release notes are current.
- [x] Server tests and client check/build pass from locked dependencies.
- [x] Android strict build and packaged web/JNI audits pass.
- [x] `git diff --check` passes and release source is committed/tagged.

## Models and licenses

- [ ] Every shipped/downloaded model has identity, source, size, checksum,
  language, precision, runtime, and license recorded.
- [ ] Fresh TTS and STT downloads pass integrity, storage, activation, and
  rollback checks.
- [ ] No unverified language, streaming, privacy, or offline claim remains.

## Desktop acceptance

- [ ] Fresh install and second launch pass on supported Python/Node versions.
- [ ] Real TTS, STT, document import, exports, History, backup, and restore pass.
- [ ] Localhost default and log-redaction behavior are verified.

## Android acceptance

- [x] Signed preview artifact is produced from the tagged source.
- [x] Artifact hash, signature identity, and package contents are recorded.
- [ ] Clean install, update with data/model preservation, and cold launch pass.
- [ ] TTS/STT model download and hands-on playback/transcription pass on a
  selected physical low/mid phone and current reference phone.
- [ ] Background, interruption, notification, Bluetooth/headphone, battery,
  memory, and thermal behavior meet the release target.

## Product and repository

- [x] README screenshots and install instructions match the artifact.
- [x] Security, contribution, license, acknowledgements, and provenance links work.
- [x] Release asset is downloadable and its checksum is published.
