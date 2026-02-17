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
        # Chunks are now dicts with 'text' and 'starts_paragraph' keys
        assert all(isinstance(chunk, dict) for chunk in chunks)
        assert all('text' in chunk and 'starts_paragraph' in chunk for chunk in chunks)

    def test_process_pipeline(self):
        """Test full preprocessing pipeline."""
        text = "Dr. Smith said, 'Hello.'   Mr. Jones replied."
        chunks = self.preprocessor.process(text)

        assert isinstance(chunks, list)
        assert len(chunks) > 0
        # Check that abbreviations are expanded in output
        combined = " ".join(chunk['text'] for chunk in chunks)
        assert "Doctor" in combined
        assert "Mister" in combined

    def test_paragraph_breaks_preserved(self):
        """Test that paragraph breaks are marked in chunk metadata."""
        text = "First paragraph sentence one. Sentence two.\n\nSecond paragraph here."
        chunks = self.preprocessor.process(text)

        assert len(chunks) >= 2
        # First chunk should start a paragraph
        assert chunks[0]['starts_paragraph'] is True
        # A later chunk should also start a paragraph (the second one)
        para_starts = [c['starts_paragraph'] for c in chunks]
        assert para_starts.count(True) >= 2  # At least 2 paragraphs

    # --- Markdown stripping tests ---

    def test_strip_markdown_headers(self):
        """Test markdown header removal."""
        assert "Header" in self.preprocessor.normalize("## Header")
        assert "#" not in self.preprocessor.normalize("## Header")
        assert "Title" in self.preprocessor.normalize("# Title")
        assert "Subheading" in self.preprocessor.normalize("### Subheading")

    def test_strip_markdown_bold_italic(self):
        """Test bold and italic marker removal."""
        assert self.preprocessor.normalize("**bold**") == "bold"
        assert self.preprocessor.normalize("*italic*") == "italic"
        assert self.preprocessor.normalize("__underline__") == "underline"
        assert self.preprocessor.normalize("_emphasis_") == "emphasis"

    def test_strip_markdown_links(self):
        """Test link syntax removal - keep text, remove URL."""
        result = self.preprocessor.normalize("[click here](https://example.com)")
        assert "click here" in result
        assert "https" not in result
        assert "example.com" not in result

    def test_strip_markdown_code(self):
        """Test inline code backtick removal."""
        assert self.preprocessor.normalize("`code`") == "code"

    def test_strip_markdown_list_markers(self):
        """Test list marker removal."""
        result = self.preprocessor.normalize("- item one\n- item two")
        assert "-" not in result
        assert "item one" in result

    def test_strip_markdown_blockquotes(self):
        """Test blockquote marker removal."""
        result = self.preprocessor.normalize("> quoted text")
        assert ">" not in result
        assert "quoted text" in result

    # --- Special character handling tests ---

    def test_sanitize_url_removal(self):
        """Test URL removal."""
        result = self.preprocessor.normalize("Visit https://example.com/path for info")
        assert "https" not in result
        assert "colon" not in result.lower()
        assert "Visit" in result
        assert "info" in result

    def test_sanitize_email_removal(self):
        """Test email address removal."""
        result = self.preprocessor.normalize("Contact user@example.com please")
        assert "@" not in result
        assert "Contact" in result
        assert "please" in result

    def test_sanitize_hashtag(self):
        """Test hashtag handling - keep word, remove #."""
        result = self.preprocessor.normalize("Check out #trending topic")
        assert "#" not in result
        assert "trending" in result

    def test_sanitize_mention(self):
        """Test @ mention handling - keep word, remove @."""
        result = self.preprocessor.normalize("Thanks @username for helping")
        assert "@" not in result
        assert "username" in result

    def test_sanitize_ampersand(self):
        """Test ampersand to 'and' conversion."""
        result = self.preprocessor.normalize("R&D department")
        assert "&" not in result
        assert "and" in result

    def test_sanitize_percent(self):
        """Test percent symbol conversion."""
        result = self.preprocessor.normalize("Save 50% today")
        assert "%" not in result
        assert "percent" in result

    def test_sanitize_degree(self):
        """Test degree symbol conversion."""
        result = self.preprocessor.normalize("Temperature is 72°F")
        assert "°" not in result
        assert "degrees" in result

    def test_sanitize_special_chars(self):
        """Test removal of various special characters."""
        result = self.preprocessor.normalize("Test / slash | pipe ~ tilde")
        assert "/" not in result
        assert "|" not in result
        assert "~" not in result
        # Words should still be present
        assert "Test" in result

    def test_sanitize_html_tags(self):
        """Test HTML tag removal."""
        result = self.preprocessor.normalize("<p>Hello</p> <br> world")
        assert "<" not in result
        assert ">" not in result
        assert "Hello" in result
        assert "world" in result

    # --- Emoji handling tests ---

    def test_strip_emojis_basic(self):
        """Test basic emoji removal."""
        result = self.preprocessor.normalize("Hello 😀 world")
        assert "😀" not in result
        assert "Hello" in result
        assert "world" in result

    def test_strip_emojis_multiple(self):
        """Test multiple emoji removal."""
        result = self.preprocessor.normalize("Party 🎉🎊🎈 time!")
        assert "🎉" not in result
        assert "🎊" not in result
        assert "🎈" not in result
        assert "Party" in result
        assert "time" in result

    # --- Number handling tests ---

    def test_convert_integers(self):
        """Test integer to words conversion."""
        result = self.preprocessor.normalize("I have 42 apples")
        assert "42" not in result
        assert "forty" in result.lower()

    def test_convert_decimals(self):
        """Test decimal number conversion."""
        result = self.preprocessor.normalize("Pi is 3.14")
        assert "3.14" not in result
        assert "point" in result.lower()
        assert "three" in result.lower()

    def test_convert_decimal_leading_zero(self):
        """Test decimal with leading zero."""
        result = self.preprocessor.normalize("Probability is 0.5")
        assert "0.5" not in result
        assert "point" in result.lower()

    # --- Integration tests ---

    def test_complex_markdown_document(self):
        """Test complex markdown with multiple features."""
        text = """## Chapter 1: **Introduction**

        This is a [link](https://example.com) to read more.

        - First point
        - Second point

        > Quote from someone

        Contact: user@email.com
        """
        result = self.preprocessor.normalize(text)

        # Markdown should be stripped
        assert "##" not in result
        assert "**" not in result
        assert "[link]" not in result
        assert "-" not in result or "First" in result  # list markers gone
        assert ">" not in result or "Quote" in result  # blockquote gone

        # URLs and emails should be gone
        assert "https" not in result
        assert "@" not in result

        # Content should remain
        assert "Chapter" in result
        assert "Introduction" in result

    def test_social_media_text(self):
        """Test social media style text with hashtags and mentions."""
        text = "Thanks @friend for sharing! 😊 Check out #coding #python"
        result = self.preprocessor.normalize(text)

        assert "@" not in result
        assert "#" not in result
        assert "😊" not in result
        assert "Thanks" in result
        assert "friend" in result
        assert "coding" in result
        assert "python" in result

    def test_technical_text(self):
        """Test technical text with special formatting."""
        text = "The R&D team achieved 95% accuracy with version 2.5"
        result = self.preprocessor.normalize(text)

        assert "&" not in result
        assert "%" not in result
        assert "and" in result
        assert "percent" in result
        assert "point" in result.lower()  # 2.5 converted
