# Open Mobile TTS Benchmark Harness

This harness measures the active app backend without changing model defaults.
Use the same cases and device state for every candidate.

## TTS baseline

Start the app, select the engine/voice to test, then run:

```sh
python3 benchmarks/run.py tts \
  --base-url http://127.0.0.1:8000 \
  --cases benchmarks/cases/tts.json \
  --output benchmarks/results/tts-kokoro-mac.json
```

The runner records time to first audio frame, total generation time, encoded
bytes, reported audio duration, real-time factor, engine, voice, host, and case.
Run once cold and once warm; do not combine them.

## STT baseline

Place locally recorded WAV files outside Git or under an ignored directory.
Create a JSON manifest:

```json
[
  {"id": "clean-01", "file": "/absolute/path/clean-01.wav", "reference": "The expected words."}
]
```

Then run:

```sh
python3 benchmarks/run.py stt \
  --base-url http://127.0.0.1:8000 \
  --cases /absolute/path/stt-cases.json \
  --output benchmarks/results/stt-moonshine-v1-mac.json
```

The runner records endpoint latency, normalized reference/hypothesis, per-case
word error rate, and mean WER. Keep recordings private unless they are cleared
for redistribution.

## Required device notes

The JSON result is only the automated portion. Record beside it:

- app/model revision and exact archive/checksum;
- cold/warm state and background apps;
- device, OS, RAM, power mode, and thermal state;
- archive/extracted size and peak process memory;
- ten-minute battery/thermal behavior;
- TTS listening/pronunciation notes; and
- STT silence/noise hallucinations and partial-text stability.

Do not select a default from one laptop result. The release decision requires a
low/mid Android phone, current Android reference, older/current iPhone when iOS
exists, Apple Silicon Mac, and ordinary x86 laptop.

## Candidate order

- TTS: current Kokoro, KittenTTS Mini, KittenTTS Nano INT8, then Pocket TTS only
  after its gated model/voice terms are accepted for the intended distribution.
- STT: current Moonshine v1 Base, Moonshine v2 Streaming Small, Moonshine v2
  Streaming Medium, then whisper.cpp Base as the multilingual control.

Raw result files are ignored by default; commit only reviewed aggregate results
that contain no private text, audio paths, or machine identifiers.

The reviewed [current baseline](BASELINE.md) records the first harness check.
The [candidate gate](CANDIDATES.md) defines what must be true before a model can
replace a default.
