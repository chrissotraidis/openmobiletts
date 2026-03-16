# Document Import

## What It Does

Accepts uploaded documents (PDF, DOCX, TXT, MD) and extracts plain text for TTS generation. The extracted text goes through the standard text preprocessing pipeline before speech synthesis.

## Why It Matters

Users often want to listen to existing documents rather than typing text manually. This is especially valuable for long-form content like articles, reports, and books.

## Core Rules

- Max upload size: 10 MB (configurable via `MAX_UPLOAD_SIZE_MB` env var) (code-derived)
- Supported formats: PDF, DOCX, TXT, MD (code-derived)
- All formats produce plain text that goes through the standard text preprocessing pipeline (code-derived)
- Markdown syntax is stripped (headers, bold/italic, links, code blocks, HTML tags, list markers) before TTS (code-derived)

### Extraction Libraries

| Format | Desktop (Python) | Android (Kotlin) | Notes |
|--------|-----------------|-------------------|-------|
| PDF | PyMuPDF (`fitz`) | pdfbox-android | Page-by-page extraction |
| DOCX | python-docx | ZIP + SAX (zero-dependency) | Extracts paragraph text |
| TXT | Built-in | Built-in | Direct UTF-8 read |
| MD | Built-in + regex | Built-in + regex | Markdown syntax stripped |

## v3.0 Changes

In v3.0, the Upload Document button expands to also accept audio files (mp3, aac, ogg, wav). The app detects file type by extension and routes accordingly:
- Document files → existing text extraction pipeline (TTS path)
- Audio files → new STT transcription pipeline (see [Audio Import](../audio-import/audio-import-overview.md))

## API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `POST /api/documents/upload` | POST | Upload and extract text from document |
| `POST /api/documents/stream` | POST | Upload document and stream TTS directly |

## What's Assumed

- 10 MB covers the vast majority of user documents — Risk if wrong: Low
- pdfbox-android produces acceptable text extraction quality — Risk if wrong: Medium

## Key References

- **Server code:** `server/src/document_processor.py`
- **Architecture:** `docs/technical-architecture.md`, "Document Processing"

## Status

🟢 Implemented
