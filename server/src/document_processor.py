"""Document processing for extracting text from PDF and DOCX files."""

import re
from pathlib import Path
from typing import Optional

import pymupdf4llm
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
        Extract text from PDF preserving reading order.

        Uses pymupdf4llm for best multi-column and structure handling.

        Args:
            filepath: Path to PDF file

        Returns:
            Extracted text in plain format
        """
        # Extract as markdown (best for structure preservation)
        markdown = pymupdf4llm.to_markdown(filepath, write_images=False)

        # Convert markdown to plain text suitable for TTS
        return self._markdown_to_plain(markdown)

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

    def _markdown_to_plain(self, markdown: str) -> str:
        """
        Convert markdown to TTS-ready plain text.

        Removes markdown formatting while preserving structure for natural speech.

        Args:
            markdown: Markdown-formatted text

        Returns:
            Plain text suitable for TTS
        """
        text = markdown

        # Remove headings markers but keep the text
        text = re.sub(r'^#{1,6}\s+', '', text, flags=re.MULTILINE)

        # Remove bold/italic markers
        text = re.sub(r'\*{1,2}([^*]+)\*{1,2}', r'\1', text)
        text = re.sub(r'_{1,2}([^_]+)_{1,2}', r'\1', text)

        # Convert links [text](url) to just text
        text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)

        # Remove inline code backticks
        text = re.sub(r'`([^`]+)`', r'\1', text)

        # Remove horizontal rules
        text = re.sub(r'^[-*_]{3,}\s*$', '', text, flags=re.MULTILINE)

        # Remove list markers (preserve text)
        text = re.sub(r'^\s*[-*+]\s+', '', text, flags=re.MULTILINE)
        text = re.sub(r'^\s*\d+\.\s+', '', text, flags=re.MULTILINE)

        # Clean up excessive whitespace
        text = re.sub(r'\n{3,}', '\n\n', text)
        text = re.sub(r' {2,}', ' ', text)

        return text.strip()
