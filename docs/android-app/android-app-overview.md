# Android App

## Current architecture

Android packages the same SvelteKit interface as the desktop app. A WebView
loads it from an in-process NanoHTTPD server on `127.0.0.1:8080`; Kotlin owns
local TTS/STT inference, files, model delivery, audio encoding, notifications,
media controls, and the JavaScript bridge. No Compose UI or external inference
server is required.

The WebView and native server publish the same schema-v1 platform capability
contract. Unsupported desktop-only actions, including batch transcription and
unsupported controls are hidden rather than allowed to fail. Engine switching
is exposed for installed Kokoro and experimental Kitten Mini/Micro models.

The native Activity remains edge-to-edge on current Android versions, but it
applies status-bar, navigation-bar, and display-cutout insets to both the
first-run model screen and shared WebView. System UI must never overlap the app
header, controls, or bottom navigation.

Navigation, microphone permission, file picking, and the JavaScript bridge are
restricted to the exact `http://127.0.0.1:8080` application origin. Android's
network-security policy blocks cleartext traffic everywhere else; loopback is
the single declared exception used by the in-process server.

## Build contract

Gradle is the only supported asset/build path. It runs `npm ci`, builds the
Svelte static output, packages it into the APK, resolves the official
sherpa-onnx 1.13.4 AAR, and verifies dependency SHA-256 metadata. The project
uses compile/target API 36, min API 26, AGP 8.10.1, Gradle 8.11.1, Java 17, and
WorkManager 2.10.5. Manual asset-copy scripts are obsolete.

```sh
cd android
./gradlew :app:assembleDebug --dependency-verification strict
```

## Model behavior

- First use downloads only `kokoro-multi-lang-v1_0` (333.2 MiB archive).
- Optional Settings download installs
  `sherpa-onnx-moonshine-base-en-int8` (239.2 MiB archive).
- The STT package is Moonshine v1 Base English INT8, not v2 Medium.
- WorkManager owns durable foreground download work; partial archives can be
  resumed and candidates are checksum-verified and staged.

See [model download flow](model-download-flow.md) for acceptance boundaries.

## Resilient TTS generation

Each stream request creates a disk-backed job and returns a job ID. Native
generation writes audio to disk independently of the HTTP consumer. If the
WebView stream drops while backgrounded, the client polls job status and
recovers completed audio and timing. Completed jobs have a bounded retention
window.

## Native responsibilities

| Area | Main implementation |
|---|---|
| WebView host, permissions, file chooser, bridge | `MainActivity.kt` |
| Local HTTP API and static assets | `TtsHttpServer.kt` |
| TTS/STT inference | `TtsManager.kt`, `SttManager.kt` |
| Durable model delivery | `ModelDownloadWorker.kt`, `ModelDownloader.kt` |
| Audio decode/encode | `AudioDecoder.kt`, `AacEncoder.kt`, `WavEncoder.kt` |
| Documents and exports | `DocumentExtractor.kt`, `ExportManager.kt` |
| Generation/playback notification | `TtsService.kt` |
| Runtime/model identity | `models/model-catalog.v1.json`, `ModelCatalog.kt` |

## Current status

🟡 Signed GitHub preview published. Version `v3.1.0-preview.1` is available
with a public checksum; its downloaded APK, v2 signature, version manifest,
web entry, shared model catalog, and arm64 JNI runtime were independently
verified on 2026-08-27. Earlier emulator updates and the physical Pixel debug
model cycle remain valid bounded evidence. Signed clean-install, fresh model
delivery, hands-on playback/transcription, low/mid hardware, background,
interruption, thermal, and store-distribution acceptance remain open.
