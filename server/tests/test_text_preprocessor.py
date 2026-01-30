"""Tests for text preprocessing."""

import pytest
from src.text_preprocessor import TextPreprocessor


class TestTextPreprocessor:
    """Test text preprocessing functionality."""

    def setup_method(self):
        """Set up test fixtures."""
        self.preprocessor = TextPreprocessor(max_chunk_tokens=100)

    def test_normalize_abbreviations(self):
        """Test abbreviation expansion."""
        text = "Dr. Smith and Mr. Jones work at ABC Inc."
        normalized = self.preprocessor.normalize(text)

        assert "Doctor" in normalized
        assert "Mister" in normalized
        assert "Incorporated" in normalized

    def test_normalize_whitespace(self):
        """Test whitespace normalization."""
        text = "Too    many     spaces"
        normalized = self.preprocessor.normalize(text)

        assert "  " not in normalized
        assert normalized == "Too many spaces"

    def test_chunk_text(self):
        """Test text chunking at sentence boundaries."""
        text = "First sentence. Second sentence. Third sentence."
        chunks = self.preprocessor.chunk_text(text)

        assert isinstance(chunks, list)
        assert len(chunks) > 0
        assert all(isinstance(chunk, str) for chunk in chunks)

    def test_process_pipeline(self):
        """Test full preprocessing pipeline."""
        text = "Dr. Smith said, 'Hello.'   Mr. Jones replied."
        chunks = self.preprocessor.process(text)

        assert isinstance(chunks, list)
        assert len(chunks) > 0
        # Check that abbreviations are expanded in output
        combined = " ".join(chunks)
        assert "Doctor" in combined
        assert "Mister" in combined
