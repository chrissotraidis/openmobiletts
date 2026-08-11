# Document Export

## Current behavior

The shared Generate interface exports current text as PDF, Markdown, or plain
text through local backend endpoints:

| Endpoint | Output |
|---|---|
| `POST /api/export/pdf` | PDF |
| `POST /api/export/md` | Markdown |
| `POST /api/export/txt` | UTF-8 text |

Desktop PDF uses the declared ReportLab dependency. Android PDF uses
`android.graphics.pdf.PdfDocument`; Markdown and text are byte output on both
platforms. The browser downloads the result and the Android bridge can hand it
to the platform file/share flow. DOCX export is intentionally out of scope
(decision [003](../decisions/003-no-docx-export.md)).

## Boundaries

- Empty input is rejected.
- Platform PDF typography and pagination can differ.
- Very large documents, complex Unicode, emoji, and right-to-left text need
  explicit cross-platform output review.
- Export saves the current editor text. It is separate from the versioned
  History-library JSON backup.

## Status

🟡 Implemented. A real desktop PDF response passes from the locked environment;
Android export code and endpoints build. Cross-platform visual PDF comparison,
large documents, Unicode/RTL, filename handling, and physical Android share
acceptance remain open.
