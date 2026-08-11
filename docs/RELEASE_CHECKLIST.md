# Release Checklist

No release is ready until every applicable item is evidenced. A build alone is
not release acceptance.

## Current pre-release evidence

- Locked server/client checks and strict Android dependency verification pass.
- Debug and unsigned release-target APKs assemble with the built web entry,
  shared model catalog, and required arm64-v8a/x86_64 sherpa JNI libraries.
- The current debug APK passed an API 34 ARM64 in-place emulator update while
  preserving downloaded models and preferences.
- The current debug APK also passed a physical Pixel 9 Pro XL in-place update,
  Kokoro/Kitten generation cycle, selected-model transition, and preservation
  readback.
- Signing, fresh required-model downloads, physical STT acceptance, and the
  wider hardware/background matrix remain open below.

## Source and CI

- [ ] Version and release notes are current.
- [ ] Server tests and client check/build pass from locked dependencies.
- [ ] Android strict build and packaged web/JNI audits pass.
- [ ] `git diff --check` passes and release source is committed/tagged.

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

- [ ] Signed release artifact is produced from the tagged source.
- [ ] Artifact hash and package contents are recorded.
- [ ] Clean install, update with data/model preservation, and cold launch pass.
- [ ] TTS/STT model download and hands-on playback/transcription pass on a
  selected physical low/mid phone and current reference phone.
- [ ] Background, interruption, notification, Bluetooth/headphone, battery,
  memory, and thermal behavior meet the release target.

## Product and repository

- [x] README screenshots and install instructions match the artifact.
- [x] Security, contribution, license, acknowledgements, and provenance links work.
- [ ] Release asset is downloadable and its checksum is published.
