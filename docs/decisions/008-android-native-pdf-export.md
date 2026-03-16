# Decision: Android Native PdfDocument for PDF Export

**Date:** 2026-03-16
**Who Decided:** User
**Status:** Accepted

## The Situation

PDF export on Android has several options: Android's built-in `PdfDocument` API, third-party libraries (iText, Apache PDFBox), or generating on a server.

## What We Chose

Android's built-in `android.graphics.pdf.PdfDocument` API — zero external dependencies, fastest and most native option.

## What We Rejected

- Third-party PDF libraries (add APK size, dependency management)
- Server-side PDF generation (breaks the fully-local-on-Android principle)

## Why

- User stated: "whatever is faster and more native for Android"
- Built into Android — zero additional dependencies
- No APK size increase
- Fast — no library initialization overhead
- Canvas-based rendering gives full control over layout

## Consequences

- PDF output is Canvas-based — need to write basic text layout (title, paragraphs, page numbers)
- Output may look slightly different from desktop ReportLab PDFs — accepted tradeoff
- Limited formatting capabilities compared to ReportLab (adequate for text documents)
