# Text-to-Speech (TTS)

## What It Does

Converts text to streaming audio using the Kokoro TTS model. Text is preprocessed, chunked at sentence boundaries, and streamed as interleaved timing metadata + binary audio. Supports 11 built-in voices across American and British English. Desktop users can choose between PyTorch Kokoro (GPU-accelerated) or Sherpa-ONNX (lighter dependencies).

## Why It Matters

This is the core feature of the app — everything else builds on top of it. The streaming architecture enables progressive playback (users hear audio before generation completes) and synchronized text highlighting.

## Core Rules

- Kokoro has a hard limit of 510 tokens per inference pass (code-derived)
- Text is chunked at 175-250 tokens per chunk, splitting at sentence boundaries (code-derived)
- Audio is encoded as MP3 64kbps CBR 22050Hz mono on desktop, AAC-LC via hardware MediaCodec on Android (code-derived)
- Streaming uses a length-prefixed framing protocol: `TIMING:{json}\n` then `AUDIO:{length}\n` then binary bytes (code-derived)
- Android emits `JOB:{id}\n` at stream start for job recovery; desktop does not (code-derived)
- Both platforms emit `CHUNKS:{total}\n` for progress display (code-derived)
- Desktop engine selection: PyTorch Kokoro (best GPU performance) or Sherpa-ONNX (lighter deps, same quality) (code-derived)
- Android uses Sherpa-ONNX exclusively with Kokoro INT8 model (~95 MB) (code-derived)
- Text preprocessing: Unicode normalization (NFKC), abbreviation expansion, number-to-words, PDF artifact removal (code-derived)
- Single HTTP POST request with StreamingResponse — no WebSockets, no polling (code-derived)
- Client can abort the fetch to cancel generation (code-derived)
- Audio format auto-detected from first chunk's magic bytes (AAC vs WAV vs MP3) (code-derived)

## What's Assumed

- Kokoro model quality is sufficient for production use — Risk if wrong: High
- 64kbps CBR MP3 is optimal for speech quality/size tradeoff — Risk if wrong: Low
- Sequential chunk processing is acceptable (no parallel generation) — Risk if wrong: Medium

## Key References

- **Streaming protocol**: `docs/technical-architecture.md`, "Streaming Protocol Design"
- **Text processing pipeline**: `docs/HOW_IT_WORKS.md`, "How Text Becomes Speech"
- **Desktop engine selection**: `docs/ROADMAP.md`, "Desktop Dual-Engine Design"
- **Server code**: `server/src/tts_engine.py`, `server/src/text_preprocessor.py`
- **Client code**: `client/src/lib/stores/player.js`
- **Android code**: `android/app/.../TtsManager.kt`, `android/app/.../TtsHttpServer.kt`

## Performance

| Hardware | TTS Speed | First Audio Chunk |
|----------|-----------|-------------------|
| RTX 4090 | ~210x real-time | ~80ms |
| RTX 3090 Ti | ~90x real-time | ~150ms |
| 8-core CPU | 3-11x real-time | ~1-3s |
| Android (Pixel 9 Pro) | ~0.80 RTF | N/A |

Audio file size: ~480KB per minute at 64kbps MP3.

## Status

🟢 Implemented — fully working on both desktop and Android platforms.
