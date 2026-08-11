# Security Policy

## Supported surface

Open Mobile TTS is a local-first application under active development. The
current server binds to loopback by default and has no authentication. Public
internet exposure and untrusted-LAN deployment are not supported.

Security fixes target the current `main` branch. No separately supported
release line exists yet.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting or Security Advisory flow
for this repository. Do not open a public issue containing exploit details,
private text/audio, local paths, access tokens, signing material, or logs that
have not been reviewed.

Include:

- affected commit or version;
- desktop, Android, or both;
- clear reproduction steps;
- expected and observed impact; and
- a minimal redacted log when useful.

## Current boundary

- Inference is local after model download.
- Logs redact user text by default, but should still be reviewed before sharing.
- LAN mode is an explicit trusted-development option, not a secured service.
- Android navigation, microphone/file permissions, JavaScript bridge access,
  and cleartext traffic are restricted to `http://127.0.0.1:8080`.
- Android model and app-signing artifacts must be verified separately from
  source correctness.
