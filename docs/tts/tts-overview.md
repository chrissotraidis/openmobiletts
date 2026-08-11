# Text-to-Speech (TTS)

## Current product behavior

Text-to-speech is the stable core of Open Mobile TTS. The app preprocesses and
sentence-chunks text, generates Kokoro audio locally, returns sentence timing,
and keeps the result in the player and History library.

| Platform/profile | Runtime and model | Current boundary |
|---|---|---|
| Desktop default | `hexgrad/Kokoro-82M` through PyTorch | Default `lang_code=a` exposes seven verified US voices |
| Desktop optional | sherpa-onnx `kokoro-multi-lang-v1_0` | Manual setup; 28 accepted English voices exposed |
| Android | sherpa-onnx 1.13.4 with `kokoro-multi-lang-v1_0` | 333.2 MiB archive; 28 accepted English voices; app-private first-use download |

The native package is not the historical 95 MB model. The archive and voice-
surface review is recorded in [model provenance](../MODEL_PROVENANCE.md); model
weights are downloaded at runtime and are not bundled in the APK.

## Processing and transfer

- Text is normalized and split at sentence boundaries within Kokoro's
  inference limit.
- Desktop encodes MP3; Android prefers AAC. Its WAV recovery path writes one
  disk-backed PCM stream and patches a single header at completion.
- The response frames timing metadata and audio bytes, and Android also emits
  a recoverable job ID.
- FastAPI transfers chunks while inference runs. The current browser client
  assembles the complete Blob before starting playback, so this is streamed
  transfer with progress, not genuine progressive playback.
- Android disk-backed jobs continue independently of a dropped WebView stream;
  the client can poll and recover completed audio/timing.

## Model modernization

Keep current Kokoro as the quality/stability baseline. Phase One compares it
against KittenTTS Mini/Nano INT8 and Pocket TTS using the same pronunciation,
long-form, first-audio, real-time factor, memory, disk, battery, thermal, and
license gates. A newer or smaller model does not become the default without
that evidence.

The checked-in [benchmark harness](../../benchmarks/README.md) records the
current HTTP behavior. One Mac baseline has validated the harness, but it is
not yet cross-model or device selection evidence.

## Status

🟡 Working baseline on desktop and Android. Real desktop generation, strict
Android packaging, emulator launch, and job recovery have evidence. Listening
quality, fresh Android model delivery, physical-device playback, and true
progressive playback remain open.
