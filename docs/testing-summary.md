# Open Mobile TTS - Testing Summary

**Last Updated**: 2026-02-17
**Status**: ✅ App fully functional and tested

---

## Automated Tests

### Server Tests

```
tests/test_text_preprocessor.py .......... 4 PASSED
tests/test_document_processor.py ......... 3 PASSED
```

**Covered:**
- ✅ Text preprocessing (normalization, chunking, abbreviation expansion)
- ✅ Document processing (PDF, DOCX, TXT extraction)

**Note:** `test_api.py` and `test_auth.py` exist from the old client-server architecture but reference removed auth endpoints. These need to be updated or removed.

---

## Manual Testing: ✅ SUCCESSFUL

**Environment:**
- Platform: macOS ARM64
- Python: 3.11.2
- espeak-ng installed
- CPU: Apple Silicon (no GPU)

**Test Results:**
1. **App start** (`python run.py`): ✅ Checks deps, builds client, starts server
2. **UI loads**: ✅ SvelteKit app at http://localhost:8000
3. **TTS generation**: ✅ Text-to-speech works with all 11 voices
4. **Streaming**: ✅ Audio streams progressively during generation
5. **Text highlighting**: ✅ Sentences highlight in sync with playback
6. **Document upload**: ✅ PDF, DOCX, TXT files processed correctly
7. **History**: ✅ Generated audio saved and replayable
8. **Docker**: ✅ `docker compose up --build` works

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

1. **Stale test files**: `test_api.py` and `test_auth.py` reference the old auth system (`/token` endpoint, `src.auth` module) that was removed during the monolithic migration. These tests will fail if run.

2. **iOS PWA**: Background audio stops when app is minimized (iOS platform limitation, not a bug).

---

## Test Coverage Gaps

- [ ] No automated tests for the streaming TTS endpoint (requires Kokoro model)
- [ ] No client-side tests
- [ ] Stale auth tests need removal or replacement
