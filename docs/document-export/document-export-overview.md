# Document Export

## What It Does

Saves text (whether typed, pasted, or transcribed) as a document file. Supports PDF, Markdown (.md), and plain text (.txt) export.

## Why It Matters

Completes the STT workflow — users can dictate, edit, then export a document without leaving the app. Also useful for saving edited text from any source.

## Core Rules

- Supported export formats: PDF, Markdown (.md), Plain Text (.txt) (spec-stated)
- No .docx export — Android has no clean, lightweight library for it (decided — [003](../decisions/003-no-docx-export.md))
- Android PDF: `android.graphics.pdf.PdfDocument` built-in API, zero dependencies (decided — [008](../decisions/008-android-native-pdf-export.md))
- Desktop PDF: ReportLab Python library (spec-stated)
- MD and TXT export: simple file write on both platforms (spec-stated)
- Android file sharing via share intent or `MediaStore` (spec-stated)

## New API Endpoints

| Endpoint | Method | Purpose | Request | Response |
|----------|--------|---------|---------|----------|
| `/api/export/pdf` | POST | Export text as PDF | `{text, title}` | PDF file bytes |
| `/api/export/md` | POST | Export text as Markdown | `{text, title}` | `.md` file bytes |
| `/api/export/txt` | POST | Export text as plain text | `{text, title}` | `.txt` file bytes |

## New Code

### Android (Kotlin)

- **ExportManager.kt** — PDF via `android.graphics.pdf.PdfDocument` (Canvas-based rendering: title, body text, page numbers). MD/TXT via file I/O. Returns file bytes or saves via `MediaStore`/share intent.

### Desktop (Python)

- **export_manager.py** — PDF via ReportLab (new pip dependency). MD/TXT via file write. Returns file bytes as StreamingResponse.

### Frontend (SvelteKit)

- **ExportPicker.svelte** — Format selection UI (PDF / MD / TXT). Appears on Export button tap. Sends text to appropriate `/api/export/*` endpoint. Triggers file download or Android share intent.

## What's Assumed

- PDF covers the "formal document" use case sufficiently without .docx — Risk if wrong: Low
- Android's PdfDocument API can produce clean enough output (title, paragraphs, page numbers) — Risk if wrong: Medium
- PDF output may look slightly different between Android (PdfDocument) and desktop (ReportLab) — accepted tradeoff

## Key References

- **Source spec:** `docs/EXPANSION-PLAN-OPEN-MOBILE-VOICE.md`, section "Document Export"
- **Decision:** [003-no-docx-export.md](../decisions/003-no-docx-export.md), [008-android-native-pdf-export.md](../decisions/008-android-native-pdf-export.md)

## Status

🔵 Not Started
