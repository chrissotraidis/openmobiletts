"""Document processing for extracting text from PDF and DOCX files."""

import re
from pathlib import Path

import fitz  # PyMuPDF - for memory-efficient page-by-page extraction
from docx import Document


class DocumentProcessor:
    """Extract text from PDF and DOCX documents."""

    SUPPORTED_FORMATS = {'.pdf', '.docx', '.txt'}

    def extract(self, filepath: str) -> str:
        """
        Extract text from a document file.

        Args:
            filepath: Path to the document file

        Returns:
            Extracted text content

        Raises:
            ValueError: If file format is not supported
        """
        path = Path(filepath)
        suffix = path.suffix.lower()

        if suffix not in self.SUPPORTED_FORMATS:
            raise ValueError(
                f"Unsupported file format: {suffix}. "
                f"Supported formats: {', '.join(self.SUPPORTED_FORMATS)}"
            )

        if suffix == '.pdf':
            return self.extract_pdf(filepath)
        elif suffix == '.docx':
            return self.extract_docx(filepath)
        elif suffix == '.txt':
            return self.extract_txt(filepath)

        raise ValueError(f"Unsupported format: {suffix}")

    def extract_pdf(self, filepath: str) -> str:
        """
        Extract text from PDF using memory-efficient page-by-page extraction.

        Uses PyMuPDF (fitz) directly for streaming extraction that handles
        large files (100MB+) without loading the entire document into memory.

        Args:
            filepath: Path to PDF file

        Returns:
            Extracted text in plain format
        """
        doc = fitz.open(filepath)
        text_parts = []

        try:
            for page_num in range(len(doc)):
                page = doc[page_num]
                # Extract text with layout preservation for better reading order
                page_text = page.get_text("text")
                if page_text.strip():
                    text_parts.append(page_text.strip())
        finally:
            doc.close()

        # Join pages with double newlines for paragraph structure
        text = '\n\n'.join(text_parts)

        # Clean up excessive whitespace
        text = re.sub(r'\n{3,}', '\n\n', text)
        text = re.sub(r' {2,}', ' ', text)

        return text.strip()

    def extract_docx(self, filepath: str) -> str:
        """
        Extract text from DOCX file.

        Args:
            filepath: Path to DOCX file

        Returns:
            Extracted text
        """
        doc = Document(filepath)

        # Extract paragraphs, filtering empty ones
        paragraphs = [p.text.strip() for p in doc.paragraphs if p.text.strip()]

        # Join with double newlines to preserve paragraph structure
        return '\n\n'.join(paragraphs)

    def extract_txt(self, filepath: str) -> str:
        """
        Extract text from plain text file.

        Args:
            filepath: Path to text file

        Returns:
            File contents
        """
        with open(filepath, 'r', encoding='utf-8') as f:
            return f.read()
