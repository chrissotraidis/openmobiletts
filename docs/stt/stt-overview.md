# Speech-to-Text (STT)

## Current product behavior

Open Mobile TTS can record or import audio, transcribe it locally, and place the
result in the editable Generate text area. It does not send audio to a cloud
service and it does not run an LLM over the transcript.

The current desktop and Android model is exactly:

| Field | Current value |
|---|---|
| Model ID | `sherpa-onnx-moonshine-base-en-int8` |
| Family | Moonshine v1 Base |
| Language | English |
| Precision | INT8 |
| Runtime | sherpa-onnx 1.13.4 on Android; sherpa-onnx Python on desktop |
| Download | 250,807,309 bytes (239.2 MiB) |
| Installed | 286,929,760 bytes (273.6 MiB) |

This is **not Moonshine v2 Medium**. Decision
[012](../decisions/012-current-moonshine-v1-baseline-and-v2-benchmark.md)
keeps the current model as the measured baseline until newer candidates pass
the same benchmark and device tests.

## Core rules

- STT is optional. Android first run downloads only the required TTS model.
- Android users install STT explicitly from Settings. The download is durable,
  resumable when the host accepts HTTP Range, checksum-verified, staged, and
  activated only after validation.
- Desktop Settings starts a pinned background installer and reports progress.
- Transcription is batch-oriented: record or import first, then transcribe.
  Streaming partial transcripts remain a benchmark-driven future option.
- Transcript polishing is deterministic paragraph formatting. No LLM
  correction is enabled (decision [002](../decisions/002-no-llm-transcript-correction.md)).
- The English-only limitation must be visible; unsupported multilingual claims
  are not product capabilities.

## API contract

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/stt/transcribe` | POST | Transcribe one uploaded audio file |
| `/api/stt/models` | GET | Report exact model identity, size, progress, and readiness |
| `/api/stt/models/download` | POST | Start or resume the pinned optional model download |
| `/api/stt/models/download/cancel` | POST | Pause the Android WorkManager download; the partial archive is retained |

Batch transcription is a desktop capability and is hidden on Android through
the shared platform-capability contract.

## Model modernization

The next comparison is the current v1 Base baseline against Moonshine v2
Streaming Small and Medium. Multilingual controls may include whisper.cpp Base
and Omnilingual ASR, but they are optional comparison tiers, not promised
replacements. Selection requires common audio, WER/CER, endpoint latency,
partial stability, RAM, disk, battery, and thermal evidence. See
[the benchmark harness](../../benchmarks/README.md) and
[model provenance](../MODEL_PROVENANCE.md).

## Status

🟡 Experimental and implemented. Real desktop inference and Android emulator
health have passed. A fresh Android download/resume cycle, representative
recordings, and physical-device quality/performance acceptance remain open.
