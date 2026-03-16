# Document Export — Edge Cases

## Special Characters in Text
**Scenario:** Text contains Unicode characters, emoji, or right-to-left text (Arabic, Hebrew).
**Why it matters:** PDF rendering must handle these correctly. Canvas-based rendering on Android may not support all Unicode ranges or RTL layout.
**How we handle it:**
1. MD and TXT export: UTF-8 encoding handles all characters natively
2. PDF export: Use a Unicode-compatible font in PdfDocument (Android) and ReportLab (desktop)
3. RTL text: Not supported in v3.0 — text renders left-to-right only
4. Emoji: Best-effort rendering — may appear as boxes on some PDF viewers
**Status:** 🔵 Not built

---

## Title Generation for Export
**Scenario:** Export needs a filename and PDF title, but user hasn't provided one.
**Why it matters:** Files need meaningful names, not `export.pdf` every time.
**How we handle it:**
1. Auto-generate title from first line of text (truncated to 50 characters)
2. Sanitize for filesystem (remove `/`, `\`, `:`, special chars)
3. Append format extension (`.pdf`, `.md`, `.txt`)
4. If text is very short or starts with non-alphanumeric characters, fall back to timestamp: `Export_20260316_143022.pdf`
**Status:** 🔵 Not built

---

## Concurrent Export Requests
**Scenario:** User taps Export, selects PDF, then immediately taps Export again and selects TXT before the first export completes.
**Why it matters:** Two export requests in flight could confuse the UI or produce unexpected downloads.
**How we handle it:**
1. Export button is disabled while an export is in progress
2. Re-enables after the file is delivered or an error occurs
3. Each export request is independent on the server — no shared state
**Status:** 🔵 Not built

---

## Storage Permission on Android
**Scenario:** On Android 10 and below, saving files to external storage requires `WRITE_EXTERNAL_STORAGE` permission. On Android 11+, `MediaStore` API is used instead.
**Why it matters:** Export could silently fail if permissions aren't handled.
**How we handle it:**
1. Use Android share intent as primary export method — avoids permission issues entirely
2. Share intent lets user choose where to save (Files, Drive, messaging app, etc.)
3. No direct file system writes from the app
**Status:** 🔵 Not built

---

## Very Large Export (100,000+ words)
**Scenario:** User pastes an entire book and exports as PDF.
**Why it matters:** PDF generation with Canvas-based rendering could be slow or memory-intensive for hundreds of pages.
**How we handle it:**
1. No hard limit on export size
2. PdfDocument creates pages on demand — memory is per-page, not cumulative
3. For very large exports (>500 pages), generation may take several seconds — show progress indicator
4. ReportLab on desktop handles large documents well
**Status:** 🔵 Not built
