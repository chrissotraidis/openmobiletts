# Open Questions and Decision Gates

**Last reviewed:** 2026-08-11

This file contains unresolved choices that can materially change the product.
Known defects and prioritized work belong in
[the modernization audit](TECH_DEBT_AND_MODERNIZATION_AUDIT.md) and
[Phase Zero plan](PHASE_ZERO_MODERNIZATION_PLAN.md).

## Working decisions to revisit after stabilization

### Product name

- **Working default:** Open Mobile TTS.
- **Revisit when:** Real Moonshine v2 is benchmarked and STT is accepted on
  desktop and Android.
- **Decision:** Keep the established name or rename the broader workspace to
  Open Mobile Voice.

### PWA support

- **Working default:** Do not advertise PWA/offline installation.
- **Revisit when:** Desktop/Android Phase Zero gates pass.
- **Decision:** Invest in a real install/update/offline shell or permanently
  describe the desktop surface as a local web interface.

### Remote/LAN mode

- **Working default:** Loopback-only default; no supported remote/VPS mode.
- **Needed first:** authentication, TLS, origin allowlist, rate/resource limits,
  secret storage, and threat model.
- **Decision:** Support trusted LAN only, secured remote access, or neither.

## Model questions

### Default mobile TTS profile

Candidates: current Kokoro, KittenTTS Mini, KittenTTS Nano INT8, Pocket TTS
INT8.

Decision requires blind listening, pronunciation, time-to-first-audio,
real-time factor, disk, peak memory, battery, thermals, long-form stability,
and license review on target devices.

### Default mobile STT profile

Candidates: current Moonshine v1 Base INT8, Moonshine v2 Streaming Small, and
Moonshine v2 Streaming Medium. whisper.cpp Base is the cross-platform
multilingual control.

Decision requires WER/CER, latency, partial-result stability, silence/noise
hallucination, long-file behavior, disk, peak memory, battery, thermals, and
license review.

### Multilingual strategy

- **Unknown:** Which languages users actually need.
- **Unknown:** Which TTS/STT candidates pass quality acceptance per language.
- **Rule:** Do not expose a language because a voice ID or model card contains
  it; verify model, text normalization, pronunciation, UI, and export behavior.

### Voice cloning

Pocket TTS and other candidates make cloning possible, but this adds consent,
abuse, storage, provenance, license, and user-education requirements.

- **Working default:** no voice cloning.
- **Decision:** Only revisit if it becomes a clear product goal.

## Platform questions

### Android minimum hardware

- Pixel 3a API 34 AVD is available for functional smoke tests.
- Pixel 9 Pro remains the high-end reference.
- A 4-6 GB physical Android phone must be selected for the low/mid baseline.
- Minimum RAM, storage, and supported ABI cannot be set until the model bake-off.

### Android background generation

Validate foreground-service type/policy, process kill, notification denial,
screen off, WebView throttling, Bluetooth/headphone routes, interruptions, and
long jobs on Android 14-16.

### iOS UI/backend transport

Prototype both:

1. loopback HTTP parity with Android; and
2. typed WKScriptMessage/native jobs with native file references.

Choose from lifecycle reliability, streaming, copying, debugging, and contract
test reuse. Do not add a third drifting backend before this decision.

### iOS model runtime

- sherpa-onnx Swift/C is the parity baseline.
- whisper.cpp/Core ML is an STT benchmark candidate.
- Do not assume ONNX automatically uses the Apple Neural Engine.

## Product questions

### Desktop launcher environment

**Resolved 2026-08-11:** The default launcher uses a repository-local `.venv`
when no virtual environment is active and respects a deliberately active
environment. See [Decision 011](decisions/011-project-virtual-environment-for-launcher.md).

### Storage source of truth

**Resolved 2026-08-11:** The client-visible History library and IndexedDB audio
cache are authoritative. Backup includes history and portable preferences but
not audio blobs or the device-specific server URL. See
[Decision 014](decisions/014-client-library-is-the-visible-data-source.md).

### Real progressive playback

The server streams framed audio, but the browser assembles a Blob before
playback. Decide whether to implement genuine progressive playback or change
the product wording permanently.

### Batch transcription on mobile

**Resolved for the current release:** Android declares batch transcription
unsupported and the shared client hides it. Revisit only with a bounded native
job design and low-memory evidence.

### First-run experience

**Resolved 2026-08-11:** Android first run downloads only the required Kokoro
TTS package. Moonshine STT is an explicit Settings download owned by
WorkManager. See [Decision 015](decisions/015-workmanager-model-delivery-and-tts-first-start.md).

## Brand questions

- **Resolved for the stabilization release:** the product remains Open Mobile
  TTS and uses selected visual option 1, a balanced text-and-waveform symbol.
  See [Decision 016](decisions/016-two-panel-waveform-icon.md).
- Which real desktop/Android screens will be used for the README hero.

## Questions already resolved by the audit

- Current STT is Moonshine v1 Base English INT8, not Moonshine v2 Medium.
- Current Android/sherpa Kokoro is roughly 350-383 MB extracted, not 95 MB.
- No LLM transcript corrector is present.
- Android uses the shared Svelte UI in a WebView, not a separate Compose UI.
- The current service worker disables the PWA cache and unregisters itself.
- The selected icon family now supplies real Android, web, README, and
  iOS-ready raster assets.
