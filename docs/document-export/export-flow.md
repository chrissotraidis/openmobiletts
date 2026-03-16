# Export Flow

## Who

A user who has text in the text area (typed, pasted, or transcribed) and wants to save it as a document file.

## The Happy Path

1. User has text in the text area on the Generate tab
2. User taps the **Export** button
3. **Format picker** appears with three options: PDF, Markdown, Plain Text
4. User selects a format (e.g., PDF)
5. App POSTs text and title to the appropriate endpoint (`/api/export/pdf`)
6. Backend generates the file:
   - **Android:** ExportManager uses `android.graphics.pdf.PdfDocument` (PDF) or file I/O (MD/TXT)
   - **Desktop:** export_manager.py uses ReportLab (PDF) or file I/O (MD/TXT)
7. File bytes are returned to the client
8. **Android:** Share intent opens — user can save to Files, share via messaging, etc.
9. **Desktop:** Browser triggers file download to the default downloads folder
10. Success feedback: brief "Exported!" confirmation

## What Could Go Wrong

### Empty text area
- **When:** User taps Export with no text content
- **What happens:** Export button is disabled when text area is empty. If somehow triggered, show "Nothing to export."
- **Recovery:** User adds text first.

### Very long text (PDF pagination)
- **When:** User exports a 10,000+ word document as PDF
- **What happens:** ExportManager paginates automatically — adds page breaks, page numbers, and proper margins. Canvas-based rendering on Android handles this via PdfDocument's `startPage()` / `finishPage()` loop.
- **Recovery:** No action needed — pagination is automatic.

### Export during active generation
- **When:** User taps Export while TTS is still generating (text is being progressively added to the area)
- **What happens:** Export uses whatever text is currently in the text area at the moment of export. Does not wait for generation to finish.
- **Recovery:** User can re-export after generation completes for the full text.

### Android share intent cancelled
- **When:** User triggers export, share intent opens, but user cancels without saving
- **What happens:** File is still generated but not saved. No state change in the app.
- **Recovery:** User can tap Export again — file is regenerated.

### PDF rendering differences across platforms
- **When:** Same text exported as PDF on Android vs desktop
- **What happens:** Output looks slightly different (Android uses PdfDocument Canvas, desktop uses ReportLab). Both are readable but fonts, margins, and layout may vary.
- **Recovery:** Not a bug — accepted tradeoff per [decision 008](../decisions/008-android-native-pdf-export.md).

## Acceptance Criteria

- [ ] Export button appears on Generate tab when text area has content
- [ ] Export button is disabled when text area is empty
- [ ] Format picker shows PDF, Markdown, and Plain Text options
- [ ] PDF export produces a valid, readable PDF with title, body text, and page numbers
- [ ] Markdown export produces a valid .md file with the text content
- [ ] Plain text export produces a .txt file with the text content
- [ ] Android: share intent opens after export
- [ ] Desktop: file downloads to browser's default download location
- [ ] 10,000-word document exports as PDF with correct pagination
- [ ] Export completes within 2 seconds for typical documents

## Related

- See: [Document Export Overview](document-export-overview.md) for API endpoints and code structure
- See: [Decision 008](../decisions/008-android-native-pdf-export.md) for PDF implementation choice
