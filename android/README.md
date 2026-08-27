# Open Mobile TTS for Android

The Android app runs the shared SvelteKit interface in a WebView and serves it
from an in-process NanoHTTPD server on `127.0.0.1:8080`. Kotlin owns on-device
TTS/STT inference, model delivery, files, audio encoding, notifications, media
controls, and the JavaScript bridge.

Android displays the short launcher/system label **OMTTS** so the name fits
beneath the app icon. The full product name remains **Open Mobile TTS** inside
the app and across repository documentation.

The packaged application label and API 34 Pixel Launcher rendering were both
verified as `OMTTS`; the launcher no longer truncates the name.

## Install the signed preview

On Android 8.0 or newer, download
[Open Mobile TTS v3.1.0-preview.1](https://github.com/chrissotraidis/openmobiletts/releases/download/v3.1.0-preview.1/open-mobile-tts-v3.1.0-preview.1.apk),
open the APK, and allow the browser or Files app to **Install unknown apps** if
Android prompts. The permission can be disabled again after installation.

Allow at least 1.2 GB of free space. The APK does not contain model weights;
first launch downloads and verifies the required 333.2 MiB Kokoro archive, then
stages its roughly 382 MiB installed model in app-private storage.

This signed build can receive future GitHub APK updates in place. A locally
built debug APK uses a different signing identity. If a debug copy is already
installed, export any needed History backup before uninstalling it; uninstalling
also deletes its downloaded models and private app data.

The APK is a preview rather than a stable or Play Store release. See the
[main installation guide](../README.md#install-on-android) and published
[checksum](https://github.com/chrissotraidis/openmobiletts/releases/download/v3.1.0-preview.1/open-mobile-tts-v3.1.0-preview.1.apk.sha256).

## Build status

A clean checkout can build the debug APK without copied source bindings, local
JNI folders, or prebuilt web assets. Gradle now:

1. installs the exact client dependencies with `npm ci`;
2. builds and packages the Svelte static output;
3. resolves sherpa-onnx `1.13.4` as one official Android AAR containing its
   matching Kotlin API and native libraries; and
4. verifies dependency SHA-256 values from
   `gradle/verification-metadata.xml`.

The project uses compile/target API 36, AGP 8.10.1, Gradle 8.11.1, Java 17,
and min API 26. The Android Studio bundled JBR 21 is valid for local builds.

## Build a debug APK

Requirements:

- Android SDK platform 36 and build tools 36.0.0;
- JDK 17 or newer; and
- Node.js 20 or 22 LTS with npm 9 or newer.

```sh
cd android
./gradlew :app:assembleDebug --dependency-verification strict
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

Android CI repeats this build and checks that the APK contains the Svelte entry
page plus arm64 and x86_64 sherpa JNI libraries. Tagged versions use the
separate release workflow, dedicated signing identity, signature/content audit,
and GitHub Releases publication. Debug builds remain development-only.

## Current models

| Role | Model | Download | Installed planning size | Notes |
|---|---|---:|---:|---|
| TTS | `kokoro-multi-lang-v1_0` | 349,418,188 bytes (333.2 MiB) | 400,786,089 bytes (382.2 MiB measured) | 53-speaker package; app exposes 28 accepted English US/UK speakers |
| TTS experimental | `kitten-mini-en-v0_8` | 67,547,594 bytes (64.4 MiB) | 99,550,582 bytes (94.9 MiB measured) | Optional English developer-preview model; eight voices |
| TTS experimental | `kitten-micro-en-v0_8` | 44,423,643 bytes (42.4 MiB) | 62,667,538 bytes (59.8 MiB measured) | Optional smaller English developer-preview model; eight voices |
| STT | `sherpa-onnx-moonshine-base-en-int8` | 250,807,309 bytes (239.2 MiB) | 287,755,667 bytes (274.4 MiB measured) | Moonshine v1 Base, English, INT8; not Moonshine v2 Medium |

First run requests only the 333.2 MiB TTS archive and reserves space for its
staged installation. STT is an optional 239.2 MiB Settings download. After
installation, inference and model files remain in app-private storage.

Models settings can separately install, activate, pause/resume, and remove the
inactive experimental Kitten packages. Activation persists the selection and
restarts the app before loading a different model. Kokoro remains required, is
not removable through the model manager, and is the stable rollback choice.

## Model integrity and activation

The downloader uses pinned archive sizes and SHA-256 values, validates HTTP
status/content range, applies connection/read timeouts, checks available space,
rejects archive links and path traversal, verifies every required file is
non-empty, stages extraction, and keeps the previous model until installation
finishes. TTS download workers do not create temporary native engines: physical
testing found that releasing one sherpa TTS instance can corrupt a later engine
in the same process. A model change therefore restarts the app into one clean
engine instance. Existing TTS-only users must explicitly
request the optional STT download from Settings; upgrades do not start it
silently.

AndroidX WorkManager 2.10.5 owns each unique foreground model job. It requires
a connected network, exposes durable progress, retries transient I/O failures
with exponential backoff, retains a valid partial archive on pause/process
interruption, and requests the remaining bytes on retry when HTTP Range is
available. The first-run Activity observes work instead of owning it.

Both backends and Android assembly consume
`models/model-catalog.v1.json`; download identity, size, checksum, runtime,
required paths, languages, and license notes no longer live in separate Python
and Kotlin constants.

Still open before stable-release quality:

- add explicit repair/update actions beyond the current experimental-model
  download, activation, pause/resume, removal, and Kokoro rollback controls;
- repeat model/voice review if release artifacts ever bundle weights; and
- test fresh, paused, resumed, process-killed, retry, notification-denied, and
  low-storage behavior on physical devices.

## Runtime features

- on-device Kokoro TTS, experimental Kitten Mini/Micro TTS, and Moonshine STT;
- disk-backed generation jobs and stream-drop recovery;
- AAC encoding with valid disk-backed WAV recovery;
- Android document/audio import and export;
- foreground service, wake lock, MediaSession, notification progress, and
  lock-screen/Bluetooth controls; and
- shared Generate, History, Player, and Settings UI.

Android audio import is currently bounded to 15 minutes and 256 MiB. Moonshine
uses overlapping 25-second windows. If AAC encoding is unavailable before the
first TTS chunk, the job writes one header-patched WAV to disk and the client
recovers it through the job API; it never concatenates complete WAV files.

The shared capability contract hides desktop-only batch transcription and
exposes engine switching only for installed Android TTS models.

## Validation recorded on 2026-08-10

- strict verified debug assembly passed;
- APK size: 139,410,379 bytes;
- the package contains `assets/webapp/index.html` and sherpa JNI libraries for
  arm64-v8a, armeabi-v7a, x86, and x86_64;
- an existing Pixel 3a API 34 ARM64 emulator install was updated in place;
- its previously downloaded TTS/STT model files were preserved;
- cold activity launch completed in 2,294 ms; and
- `/api/health` and `/api/stt/models` reported version `3.1.0-dev`,
  sherpa-onnx, and the installed Moonshine v1 model as active.

This proves build, package, emulator update, launch, data-preservation readback,
and API health for that AVD. It does not prove a fresh full model download,
physical-phone preservation, model quality/performance, hands-on playback,
background behavior, thermals, or release signing.

See the [Phase Zero plan](../docs/PHASE_ZERO_MODERNIZATION_PLAN.md),
[current status](../docs/status.md), and
[full audit](../docs/TECH_DEBT_AND_MODERNIZATION_AUDIT.md).

## Emulator update checkpoint — 2026-08-11

The current selected-icon/WorkManager APKs passed strict debug and unsigned
release-target assembly. The debug APK also passed an in-place update on the
existing API 34 ARM64 emulator:

- current OMTTS-label/continuous-model-switch debug APK: 142,626,776 bytes,
  SHA-256 `4b29f75e211160ff5c07d5bef12fb7e4071e9610a3f697b027e5bea07db71455`;
- the current debug APK is v2 debug-signed and contains the built web entry,
  shared model catalog, and arm64-v8a plus x86_64 sherpa JNI libraries;
- the current unsigned release target is 138,443,539 bytes with SHA-256
  `c0664f3905ee65b75944eab76149a569be93c975d421296dd1a2fe7d68a86cc3`;
- Kokoro model directory remained 394,376 KiB before and after update;
- Moonshine model directory remained 281,096 KiB before and after update;
- shared preferences remained 40 KiB;
- cold launch completed in 2,544 ms;
- health, capability, shared-catalog, 28-voice, and Moonshine status APIs passed;
- optional STT remained installed but inactive after cold launch, confirming
  lazy initialization;
- WorkManager created its durable `androidx.work.workdb`; and
- Pixel Launcher rendered the selected adaptive icon without crop/fringe.

This is an emulator-test artifact, not the deferred final phone build. It does
not close fresh download/pause/resume/process-death, physical playback,
transcription quality, thermals, release signing, or store-distribution gates.

## Experimental Kitten emulator checkpoint — 2026-08-11

The current API 34 ARM64 emulator completed both real optional downloads with
the production WorkManager path, pinned checksums, safe extraction, and staged
native audio generation:

- Kitten Mini generated 170,912 samples at 24 kHz (7.121 seconds) through the
  normal TTS endpoint after activation;
- Kitten Micro generated 182,818 samples at 24 kHz (7.617 seconds);
- each exposed the verified eight upstream voices and remained selected after
  process relaunch;
- switching back to Kokoro generated 92,334 samples at 24 kHz; and
- in-place APK updates preserved Kokoro, Moonshine, both Kitten packages, and
  existing preferences.

This proves functional emulator integration, not comparative model quality,
speed, battery, thermals, physical-device playback, or default promotion. A
later physical Pixel pass exposed in-process sherpa engine-release corruption;
Decision 021 replaces hot swapping with clean-process activation, which passed
the subsequent physical regression cycle below.

## Physical model-cycle checkpoint — 2026-08-11

Pixel 9 Pro XL testing reproduced a lifecycle defect: releasing one sherpa TTS
instance caused later Kitten and Kokoro instances in the same process to return
truncated audio. Downloaded models and hashes were intact. The app now keeps one
TTS engine per process, installs TTS packages without a temporary native engine,
and uses a short foreground restart bridge for model changes.

After that repair, the same phone produced:

- Kokoro: 88,337 samples, 3.68 seconds, 31,002 encoded bytes;
- Kitten Mini: 168,237 samples, 7.01 seconds, 58,172 encoded bytes;
- Kitten Micro: 191,144 samples, 7.96 seconds, 65,836 encoded bytes; and
- Kokoro after the complete cycle: 123,629 samples, 5.15 seconds, 42,846
  encoded bytes.

The final 142,626,776-byte debug APK installed in place with SHA-256
`4b29f75e211160ff5c07d5bef12fb7e4071e9610a3f697b027e5bea07db71455`.
Kokoro, Mini, Micro, Moonshine, and app preferences remained installed; Kokoro
was left active. Emulator UI testing separately proved automatic process
restart and selected-model voice refresh. These are functional artifacts and
durations, not owner listening acceptance or a performance comparison.

## Continuous model-switch checkpoint — 2026-08-11

The clean process boundary remains mandatory, but the Android UI no longer
looks like a crash or returns to Generate after a model change. Emulator UI
validation changed PID 9982 to PID 10191 while showing the app icon,
**Switching voice model**, the exact Kitten Mini label, and progress. The new
process reopened Settings at the selected card, reported Mini active through
the API, exposed its voices, and showed **Active and ready**. The resume
destination travels through the native relaunch intent and is consumed once;
ordinary launches still open Generate.
