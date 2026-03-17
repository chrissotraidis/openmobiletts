"""
Document export manager for PDF, Markdown, and plain text.

PDF uses ReportLab on desktop (more capable than Android's PdfDocument).
MD and TXT are simple text formatting.
"""

import io
import logging
from typing import Literal

logger = logging.getLogger(__name__)

# Try to import ReportLab — optional dependency
try:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.units import mm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak
    HAS_REPORTLAB = True
except ImportError:
    HAS_REPORTLAB = False
    logger.info("reportlab not installed — PDF export disabled. Install with: pip install reportlab")


def export_pdf(text: str, title: str = "") -> bytes:
    """
    Export text as PDF bytes using ReportLab.

    Args:
        text: The text content to export
        title: Document title (appears as heading)

    Returns:
        PDF file as bytes
    """
    if not HAS_REPORTLAB:
        raise RuntimeError("reportlab is not installed. Install with: pip install reportlab")

    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=20 * mm,
        rightMargin=20 * mm,
        topMargin=25 * mm,
        bottomMargin=25 * mm,
    )

    styles = getSampleStyleSheet()

    title_style = ParagraphStyle(
        "ExportTitle",
        parent=styles["Heading1"],
        fontSize=18,
        spaceAfter=12,
    )

    body_style = ParagraphStyle(
        "ExportBody",
        parent=styles["Normal"],
        fontSize=12,
        leading=16,
        spaceAfter=6,
    )

    story = []

    if title.strip():
        story.append(Paragraph(title, title_style))
        story.append(Spacer(1, 12))

    # Split text into paragraphs and add each as a Paragraph element
    for para in text.split("\n"):
        if para.strip():
            # Escape HTML special chars for ReportLab
            safe = (
                para.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            )
            story.append(Paragraph(safe, body_style))
        else:
            story.append(Spacer(1, 8))

    if not story:
        story.append(Paragraph("(empty document)", body_style))

    doc.build(story)
    pdf_bytes = buffer.getvalue()

    logger.info(f"PDF exported: {len(pdf_bytes)} bytes, title='{title[:50]}'")
    return pdf_bytes


def export_markdown(text: str, title: str = "") -> bytes:
    """
    Export text as Markdown bytes.

    Args:
        text: The text content
        title: Document title (appears as H1 header)

    Returns:
        Markdown file as UTF-8 bytes
    """
    content = ""
    if title.strip():
        content += f"# {title}\n\n"
    content += text

    return content.encode("utf-8")


def export_plaintext(text: str, title: str = "") -> bytes:
    """
    Export text as plain text bytes.

    Args:
        text: The text content
        title: Document title (appears as underlined heading)

    Returns:
        Plain text file as UTF-8 bytes
    """
    content = ""
    if title.strip():
        content += f"{title}\n"
        content += "=" * min(len(title), 60) + "\n\n"
    content += text

    return content.encode("utf-8")
