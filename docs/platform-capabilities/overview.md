# Platform Capabilities

**Status:** 🟢 Implemented

The shared Svelte interface discovers backend support through
`GET /api/capabilities` instead of inferring backend behavior from the browser
or native bridge. (decided — [Decision 013](../decisions/013-server-reported-platform-capabilities.md))

## Contract

Every backend returns:

```json
{
  "schema_version": 1,
  "platform": "desktop",
  "features": {
    "tts": true,
    "stt": true,
    "batch_transcription": true,
    "engine_switching": true,
    "document_import": true,
    "audio_import": true,
    "model_download": true,
    "project_storage": true,
    "exports": true,
    "logs": true
  }
}
```

`platform` is `desktop` for FastAPI and `android` for NanoHTTPD. Android reports
`batch_transcription` and `engine_switching` as false. All keys are required so
a missing feature cannot accidentally be treated as supported. (code-derived)

## Client rules

- Load capabilities before presenting platform-dependent actions.
- Hide Batch Upload when `batch_transcription` is false.
- Hide engine switching when `engine_switching` is false; the active engine may
  still be displayed.
- Native bridge detection remains valid only for native save/media callbacks.
- A failed capability request uses a conservative all-false fallback except for
  the universal TTS surface; it does not invent Android support.

## Error contract

Unknown `/api/*` routes return HTTP 404 with JSON `{"detail":"Not found"}` on
both backends. Static SPA fallback applies only to non-API navigation.
