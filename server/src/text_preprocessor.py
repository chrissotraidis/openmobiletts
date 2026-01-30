"""Text preprocessing for TTS - cleaning, normalization, and chunking."""

import re
import unicodedata
from typing import List

from num2words import num2words

from .config import settings


class TextPreprocessor:
    """Preprocess text for optimal TTS quality."""

    # Common abbreviations to expand
    ABBREVIATIONS = {
        r'\bDr\.': 'Doctor',
        r'\bMr\.': 'Mister',
        r'\bMrs\.': 'Missus',
        r'\bMs\.': 'Miss',
        r'\bProf\.': 'Professor',
        r'\bSr\.': 'Senior',
        r'\bJr\.': 'Junior',
        r'\bInc\.': 'Incorporated',
        r'\bLtd\.': 'Limited',
        r'\bCo\.': 'Company',
        r'\bCorp\.': 'Corporation',
        r'\bAve\.': 'Avenue',
        r'\bSt\.': 'Street',
        r'\bRd\.': 'Road',
        r'\bBlvd\.': 'Boulevard',
        r'\betc\.': 'et cetera',
        r'\be\.g\.': 'for example',
        r'\bi\.e\.': 'that is',
        r'\bvs\.': 'versus',
    }

    def __init__(self, max_chunk_tokens: int = None):
        """
        Initialize the text preprocessor.

        Args:
            max_chunk_tokens: Maximum tokens per chunk (default from settings)
        """
        self.max_chunk_tokens = max_chunk_tokens or settings.MAX_CHUNK_TOKENS

    def normalize(self, text: str) -> str:
        """
        Normalize text for TTS processing.

        Args:
            text: Raw input text

        Returns:
            Normalized text ready for TTS
        """
        # Normalize Unicode (NFKC handles ligatures, special chars)
        text = unicodedata.normalize('NFKC', text)

        # Remove PDF artifacts (page numbers, excessive whitespace)
        text = re.sub(r'\n\s*\d+\s*\n', '\n', text)  # Page numbers
        text = re.sub(r'\s{3,}', ' ', text)  # Multiple spaces
        text = re.sub(r'\n{3,}', '\n\n', text)  # Excessive newlines

        # Remove hyphenation at line breaks
        text = re.sub(r'-\n', '', text)

        # Expand common abbreviations
        for pattern, replacement in self.ABBREVIATIONS.items():
            text = re.sub(pattern, replacement, text, flags=re.IGNORECASE)

        # Convert numbers to words (for better TTS pronunciation)
        text = self._convert_numbers_to_words(text)

        # Clean up whitespace
        text = ' '.join(text.split())

        return text

    def _convert_numbers_to_words(self, text: str) -> str:
        """
        Convert standalone numbers to words for better TTS.

        Args:
            text: Input text with numbers

        Returns:
            Text with numbers converted to words
        """
        def replace_number(match):
            try:
                num = match.group(0)
                # Only convert reasonable numbers (not years, IDs, etc.)
                if len(num) <= 4:
                    return num2words(int(num))
                return num
            except (ValueError, OverflowError):
                return match.group(0)

        # Match standalone numbers (not part of addresses, dates, etc.)
        text = re.sub(r'\b\d{1,4}\b', replace_number, text)
        return text

    def chunk_text(self, text: str) -> List[str]:
        """
        Split text into optimal chunks for TTS processing.

        Chunks are split at sentence boundaries and target max_chunk_tokens.
        This ensures natural pauses and stays within Kokoro's 510 token limit.

        Args:
            text: Preprocessed text to chunk

        Returns:
            List of text chunks
        """
        # Split into sentences (handles common sentence endings)
        sentences = re.split(r'(?<=[.!?])\s+', text)

        chunks = []
        current_chunk = []
        current_length = 0

        for sentence in sentences:
            # Rough token estimate: ~4 characters per token
            sentence_tokens = len(sentence) // 4

            # If adding this sentence exceeds max, save current chunk
            if current_length + sentence_tokens > self.max_chunk_tokens and current_chunk:
                chunks.append(' '.join(current_chunk))
                current_chunk = [sentence]
                current_length = sentence_tokens
            else:
                current_chunk.append(sentence)
                current_length += sentence_tokens

        # Add remaining sentences
        if current_chunk:
            chunks.append(' '.join(current_chunk))

        return chunks

    def process(self, text: str) -> List[str]:
        """
        Full preprocessing pipeline: normalize and chunk text.

        Args:
            text: Raw input text

        Returns:
            List of normalized, chunked text ready for TTS
        """
        normalized = self.normalize(text)
        return self.chunk_text(normalized)
