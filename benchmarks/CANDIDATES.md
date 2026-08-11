# Model Candidate Gate

**Reviewed:** 2026-08-11

This is a benchmark queue, not a model migration decision. Current Kokoro and
Moonshine v1 remain defaults until a candidate wins on the same devices and
content while meeting license/distribution requirements.

## TTS queue

| Order | Candidate | Why test it | Adoption blockers |
|---|---|---|---|
| Baseline | Kokoro-82M / current sherpa Kokoro | Existing voices, highlighting, and known integration | Large native archive; Python first-use UX; per-voice/archive provenance |
| Experimental integration | KittenTTS v0.8 Mini | 80M, eight English voices, ONNX/CPU, Apache-2.0; Android download/swap/generation works | Developer preview; quality, pronunciation, physical-device, battery, and thermal validation |
| Experimental integration | KittenTTS v0.8 Micro | 40M, smaller verified 42.4 MiB archive with the same eight voices; Android download/swap/generation works | Same unmeasured quality/device boundaries as Mini |
| Next candidate | KittenTTS v0.8 Nano INT8 | About 25 MB and highly attractive for low-storage phones | Upstream reports issues; quality and stability may not meet long-form baseline |
| Later candidate | Pocket TTS | 100M, real audio streaming, upstream reports about 200 ms first audio and CPU operation | Gated model access/conditions, separate voice terms, cloning safety, redistribution flow |

Sources: [KittenTTS](https://github.com/KittenML/KittenTTS),
[sherpa Kitten integration](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kitten.html),
[Pocket TTS code](https://github.com/kyutai-labs/pocket-tts), and
[Pocket TTS model terms](https://huggingface.co/kyutai/pocket-tts).

Pocket TTS code being MIT does not make its gated model and voice files an
automatic in-app download. Voice cloning is not part of the current product
goal.

## STT queue

| Order | Candidate | Why test it | Adoption blockers |
|---|---|---|---|
| Baseline | Moonshine v1 Base English INT8 | Current integration and known archive | Batch-oriented and lower reported accuracy than newer streaming models |
| 1 | Moonshine v2 Small Streaming | 123M; upstream reports 7.84% benchmark WER and low endpoint latency | Exact sherpa archive/RAM, partial-result UX, and target-device proof |
| 2 | Moonshine v2 Medium Streaming | 245M; upstream reports 6.65% benchmark WER | Larger disk/RAM/thermal cost; may be an optional quality tier rather than default |
| Control | whisper.cpp Base | Mature multilingual/iOS control | Not architecturally true streaming; separate runtime increases maintenance |
| Optional language pack | Omnilingual ASR CTC 300M INT8 | Broad language coverage through sherpa | Variable per-language quality, punctuation/casing, 348 MB-class package |

Source: [Moonshine Voice models and benchmark methodology](https://github.com/moonshine-ai/moonshine).
Upstream benchmark numbers are shortlist evidence only; Open Mobile TTS must
measure its own recordings, runtime, quantization, devices, and endpoint logic.

## Required promotion evidence

A candidate cannot become a default until one reviewed result set includes:

- exact model revision, archive, SHA-256, runtime, and precision;
- cold and warm first output, total generation/endpoint latency, and RTF;
- peak memory, archive/installed bytes, battery, and thermal behavior;
- TTS blind preference, pronunciation, long-form repeats/skips, and output
  stability; or STT WER/CER, silence/noise hallucination, endpoint behavior,
  partial churn, and long-file stability;
- low/mid and current Android devices plus representative desktop hardware;
- iPhone evidence before any iOS default; and
- code, weights, voice, data, gated-access, and redistribution review.
