# Open Mobile TTS — Android

Native Android app with on-device TTS powered by Sherpa-ONNX + Kokoro.

## Setup

### 1. Download Sherpa-ONNX JNI Libraries

Download the pre-built native libraries from the [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases) page:

```bash
cd /tmp
curl -SL -O https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.25/sherpa-onnx-v1.12.25-android.tar.bz2
tar xf sherpa-onnx-v1.12.25-android.tar.bz2

# Copy JNI libs to the project
cp -r jniLibs/* android/app/src/main/jniLibs/
```

This provides `.so` files for all 4 ABIs: arm64-v8a, armeabi-v7a, x86, x86_64.

### 2. Open in Android Studio

1. Open Android Studio
2. File > Open > select the `android/` directory
3. Wait for Gradle sync to complete
4. Build and run on a device/emulator

### 3. First Launch

On first launch, tap "Download Model" to download the INT8 Kokoro model (~95 MB).
After download, tap "Speak" to hear on-device TTS.

## Architecture

- **Tts.kt** — Sherpa-ONNX Kotlin API (JNI bindings for `OfflineTts`)
- **TtsManager** — Wraps `OfflineTts` with Kotlin coroutines for init/generate
- **ModelDownloader** — Downloads INT8 Kokoro model on first launch
- **MainActivity** — Compose UI with download + speak buttons

No server or network needed for TTS (only for initial model download).

## Requirements

- Android 8.0+ (API 26)
- arm64-v8a device (most modern Android phones)
- ~200 MB free storage (app + model)
