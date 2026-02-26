"""Document processing for extracting text from PDF and DOCX files."""

import re
from pathlib import Path

import fitz  # PyMuPDF - for memory-efficient page-by-page extraction
from docx import Document

from .logging_config import get_logger, preview_text

logger = get_logger(__name__)


class DocumentProcessor:
    """Extract text from PDF and DOCX documents."""

    SUPPORTED_FORMATS = {'.pdf', '.docx', '.txt', '.md'}

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
        file_size = path.stat().st_size

        logger.info(f"Extracting document: {path.name} ({suffix}, {file_size} bytes)")

        if suffix not in self.SUPPORTED_FORMATS:
            logger.error(f"Unsupported file format: {suffix}")
            raise ValueError(
                f"Unsupported file format: {suffix}. "
                f"Supported formats: {', '.join(self.SUPPORTED_FORMATS)}"
            )

        if suffix == '.pdf':
            text = self.extract_pdf(filepath)
        elif suffix == '.docx':
            text = self.extract_docx(filepath)
        elif suffix == '.txt':
            text = self.extract_txt(filepath)
        elif suffix == '.md':
            text = self.extract_markdown(filepath)
        else:
            raise ValueError(f"Unsupported format: {suffix}")

        logger.info(f"Extracted {len(text)} chars from {path.name}")
        logger.debug(f"Extracted text preview: {preview_text(text, 300)}")
        return text

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
            total_pages = len(doc)
            logger.debug(f"PDF has {total_pages} pages")

            for page_num in range(total_pages):
                page = doc[page_num]
                # Extract text with layout preservation for better reading order
                page_text = page.get_text("text")
                if page_text.strip():
                    text_parts.append(page_text.strip())
                    logger.debug(f"Page {page_num + 1}: {len(page_text)} chars extracted")
        finally:
            doc.close()

        # Join pages with double newlines for paragraph structure
        text = '\n\n'.join(text_parts)
        pre_cleanup_len = len(text)
        pre_cleanup_newlines = text.count('\n')

        # Clean up excessive whitespace
        text = re.sub(r'\n{3,}', '\n\n', text)
        text = re.sub(r' {2,}', ' ', text)

        logger.debug(f"PDF cleanup: {pre_cleanup_len} -> {len(text)} chars, {pre_cleanup_newlines} -> {text.count(chr(10))} newlines")

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
        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            text = f.read()
        logger.debug(f"TXT file: {len(text)} chars, {text.count(chr(10))} newlines")
        return text

    def extract_markdown(self, filepath: str) -> str:
        """
        Extract text from Markdown file, stripping formatting for TTS.

        Removes headers, bold/italic markers, links, images, code blocks,
        and HTML tags while preserving readable text content.

        Args:
            filepath: Path to Markdown file

        Returns:
            Plain text suitable for speech synthesis
        """
        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            text = f.read()

        # Remove fenced code blocks
        text = re.sub(r'```[\s\S]*?```', '', text)
        # Remove inline code (preserve content)
        text = re.sub(r'`([^`]+)`', r'\1', text)
        # Remove images ![alt](url)
        text = re.sub(r'!\[([^\]]*)\]\([^)]*\)', r'\1', text)
        # Convert links [text](url) to just text
        text = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', text)
        # Remove HTML tags
        text = re.sub(r'<[^>]+>', '', text)
        # Remove header markers
        text = re.sub(r'(?m)^#{1,6}\s+', '', text)
        # Remove bold/italic markers
        text = re.sub(r'\*{1,3}([^*]+)\*{1,3}', r'\1', text)
        text = re.sub(r'_{1,3}([^_]+)_{1,3}', r'\1', text)
        # Remove strikethrough
        text = re.sub(r'~~([^~]+)~~', r'\1', text)
        # Remove blockquote markers
        text = re.sub(r'(?m)^>\s?', '', text)
        # Remove horizontal rules
        text = re.sub(r'(?m)^[-*_]{3,}\s*$', '', text)
        # Remove list markers
        text = re.sub(r'(?m)^\s*[-*+]\s+', '', text)
        text = re.sub(r'(?m)^\s*\d+\.\s+', '', text)

        # Clean up whitespace
        text = re.sub(r'\n{3,}', '\n\n', text)
        text = re.sub(r' {2,}', ' ', text)

        logger.debug(f"Markdown file: {len(text)} chars after stripping")
        return text.strip()
