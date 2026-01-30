"""Tests for document processing."""

import pytest
from pathlib import Path
import tempfile

from src.document_processor import DocumentProcessor


class TestDocumentProcessor:
    """Test document extraction functionality."""

    def setup_method(self):
        """Set up test fixtures."""
        self.processor = DocumentProcessor()

    def test_extract_txt(self):
        """Test plain text extraction."""
        # Create a temporary text file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False) as f:
            f.write("Hello world.\nThis is a test.")
            temp_path = f.name

        try:
            text = self.processor.extract(temp_path)
            assert "Hello world" in text
            assert "This is a test" in text
        finally:
            Path(temp_path).unlink()

    def test_unsupported_format(self):
        """Test that unsupported formats raise ValueError."""
        with tempfile.NamedTemporaryFile(suffix='.xyz', delete=False) as f:
            temp_path = f.name

        try:
            with pytest.raises(ValueError, match="Unsupported file format"):
                self.processor.extract(temp_path)
        finally:
            Path(temp_path).unlink()

    def test_markdown_to_plain(self):
        """Test markdown to plain text conversion."""
        markdown = "# Heading\n\n**Bold text** and *italic*.\n\n- List item\n"
        plain = self.processor._markdown_to_plain(markdown)

        # Check markdown formatting is removed
        assert "#" not in plain
        assert "**" not in plain
        assert "*" not in plain
        assert "-" not in plain  # List marker removed
        assert "Heading" in plain
        assert "Bold text" in plain
        assert "italic" in plain
