# Open Mobile TTS - Testing Summary

**Last Updated**: 2026-02-19
**Status**: App fully functional and tested (web + Android WebView)

---

## Automated Tests

### Server Tests

```
tests/test_text_preprocessor.py .......... 4 PASSED
tests/test_document_processor.py ......... 3 PASSED
tests/test_api.py ...................... 6 PASSED
```

**Covered:**
- Text preprocessing (normalization, chunking, abbreviation expansion)
- Document processing (PDF, DOCX, TXT extraction)
- API endpoints (health, voices, TTS, document upload, root)

---

## Manual Testing: SUCCESSFUL

**Environment:**
- Platform: macOS ARM64
- Python: 3.11.2
- espeak-ng installed
- CPU: Apple Silicon (no GPU)

**Test Results:**
1. **App start** (`python run.py`): Checks deps, builds client, starts server
2. **UI loads**: SvelteKit app at http://localhost:8000
3. **TTS generation**: Text-to-speech works with all 11 voices
4. **Streaming**: Audio streams progressively during generation
5. **Text highlighting**: Sentences highlight in sync with playback
6. **Document upload**: PDF, DOCX, TXT files processed correctly
7. **History**: Generated audio saved and replayable
8. **Docker**: `docker compose up --build` works

**Performance Observations (CPU):**
- First chunk latency: ~2-3 seconds
- Short text ("Hello world"): ~3 seconds total
- Audio quality: Excellent at 64kbps mono

---

## CI/CD

### GitHub Actions Workflow

**File**: `.github/workflows/test-server.yml`

**Triggers:**
- Push to main/develop branches (server changes)
- Pull requests to main

**Steps:**
1. Python 3.11 setup
2. System dependencies (espeak-ng, ffmpeg)
3. Python dependencies installation
4. Pytest execution

---

## Known Issues

1. **iOS PWA**: Background audio stops when app is minimized (iOS platform limitation, not a bug).

---

## Test Coverage Gaps

- [ ] No automated tests for the streaming TTS endpoint (requires Kokoro model)
- [ ] No client-side tests
- [ ] No automated Android instrumentation tests
