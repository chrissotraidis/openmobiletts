#!/usr/bin/env python3
"""Dependency-free HTTP benchmark runner for the active Open Mobile TTS backend."""

from __future__ import annotations

import argparse
import json
import mimetypes
import platform
import re
import socket
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from statistics import mean
from typing import Any


def request_json(url: str) -> Any:
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.load(response)


def post_json(url: str, payload: dict[str, Any]):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    return urllib.request.urlopen(request, timeout=900)


def parse_tts_stream(response, started: float) -> dict[str, Any]:
    buffer = bytearray()
    pending_audio = 0
    audio_bytes = 0
    first_audio_seconds: float | None = None
    timings: list[dict[str, Any]] = []

    while True:
        chunk = response.read(64 * 1024)
        if not chunk:
            break
        buffer.extend(chunk)

        while buffer:
            if pending_audio:
                if len(buffer) < pending_audio:
                    break
                del buffer[:pending_audio]
                audio_bytes += pending_audio
                pending_audio = 0
                if first_audio_seconds is None:
                    first_audio_seconds = time.perf_counter() - started
                continue

            newline = buffer.find(b"\n")
            if newline < 0:
                break
            line = bytes(buffer[:newline]).decode("utf-8")
            del buffer[: newline + 1]
            if line.startswith("AUDIO:"):
                pending_audio = int(line[6:])
            elif line.startswith("TIMING:"):
                timings.append(json.loads(line[7:]))

    if pending_audio or buffer:
        raise RuntimeError("TTS response ended with an incomplete frame")
    duration = max((float(item.get("end", 0) or 0) for item in timings), default=0.0)
    total = time.perf_counter() - started
    return {
        "ttfa_ms": round((first_audio_seconds or total) * 1000, 1),
        "generation_seconds": round(total, 3),
        "audio_duration_seconds": round(duration, 3),
        "real_time_factor": round(total / duration, 4) if duration > 0 else None,
        "audio_bytes": audio_bytes,
        "chunks": len(timings),
    }


def run_tts(base_url: str, cases_path: Path, voice: str | None) -> dict[str, Any]:
    voices = request_json(f"{base_url}/api/voices")
    selected_voice = voice or voices[0]["name"]
    engine = request_json(f"{base_url}/api/engine")["engine"]
    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    results = []
    for case in cases:
        started = time.perf_counter()
        with post_json(
            f"{base_url}/api/tts/stream",
            {"text": case["text"], "voice": selected_voice, "speed": 1.0},
        ) as response:
            metrics = parse_tts_stream(response, started)
        results.append({"id": case["id"], "characters": len(case["text"]), **metrics})
        print(f"{case['id']}: TTFA {metrics['ttfa_ms']} ms, RTF {metrics['real_time_factor']}")
    measured_rtfs = [item["real_time_factor"] for item in results if item["real_time_factor"] is not None]
    return {
        "kind": "tts",
        "engine": engine,
        "voice": selected_voice,
        "cases": results,
        "mean_ttfa_ms": round(mean(item["ttfa_ms"] for item in results), 1),
        "mean_rtf": round(mean(measured_rtfs), 4) if measured_rtfs else None,
    }


def normalize_words(text: str) -> list[str]:
    return re.findall(r"[a-z0-9']+", text.lower())


def word_error_rate(reference: str, hypothesis: str) -> float:
    expected = normalize_words(reference)
    actual = normalize_words(hypothesis)
    if not expected:
        return 0.0 if not actual else 1.0
    previous = list(range(len(actual) + 1))
    for i, expected_word in enumerate(expected, start=1):
        current = [i]
        for j, actual_word in enumerate(actual, start=1):
            current.append(
                min(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + (expected_word != actual_word),
                )
            )
        previous = current
    return previous[-1] / len(expected)


def multipart_audio(path: Path) -> tuple[bytes, str]:
    boundary = f"----openmobiletts-{uuid.uuid4().hex}"
    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    prefix = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
        f"Content-Type: {mime}\r\n\r\n"
    ).encode("utf-8")
    suffix = f"\r\n--{boundary}--\r\n".encode("utf-8")
    return prefix + path.read_bytes() + suffix, boundary


def run_stt(base_url: str, cases_path: Path) -> dict[str, Any]:
    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    model_state = request_json(f"{base_url}/api/stt/models")
    results = []
    for case in cases:
        audio_path = Path(case["file"]).expanduser().resolve()
        body, boundary = multipart_audio(audio_path)
        request = urllib.request.Request(
            f"{base_url}/api/stt/transcribe",
            data=body,
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
            method="POST",
        )
        started = time.perf_counter()
        with urllib.request.urlopen(request, timeout=900) as response:
            payload = json.load(response)
        elapsed = time.perf_counter() - started
        hypothesis = payload.get("text", "")
        wer = word_error_rate(case["reference"], hypothesis)
        results.append(
            {
                "id": case["id"],
                "latency_seconds": round(elapsed, 3),
                "wer": round(wer, 4),
                "reference": case["reference"],
                "hypothesis": hypothesis,
            }
        )
        print(f"{case['id']}: {elapsed:.3f}s, WER {wer:.2%}")
    return {
        "kind": "stt",
        "models": model_state.get("models", []),
        "cases": results,
        "mean_wer": round(mean(item["wer"] for item in results), 4),
        "mean_latency_seconds": round(mean(item["latency_seconds"] for item in results), 3),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("tts", "stt"))
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--cases", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--voice")
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")

    try:
        health = request_json(f"{base_url}/api/health")
        result = run_tts(base_url, args.cases, args.voice) if args.kind == "tts" else run_stt(base_url, args.cases)
    except (OSError, urllib.error.URLError, ValueError, RuntimeError) as error:
        print(f"Benchmark failed: {error}", file=sys.stderr)
        return 1

    document = {
        "schema_version": 1,
        "recorded_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "app": health,
        "host": {
            "system": platform.system(),
            "release": platform.release(),
            "machine": platform.machine(),
            "python": platform.python_version(),
            "hostname": socket.gethostname(),
        },
        **result,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
