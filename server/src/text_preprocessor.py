"""Text preprocessing for TTS - cleaning, normalization, and chunking."""

import re
import unicodedata
from typing import List

from num2words import num2words

from .config import settings
from .logging_config import get_logger, preview_text

logger = get_logger(__name__)


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

    # Emoji regex pattern (covers most common emoji ranges)
    EMOJI_PATTERN = re.compile(
        "["
        "\U0001F600-\U0001F64F"  # emoticons
        "\U0001F300-\U0001F5FF"  # symbols & pictographs
        "\U0001F680-\U0001F6FF"  # transport & map
        "\U0001F1E0-\U0001F1FF"  # flags
        "\U00002702-\U000027B0"  # dingbats
        "\U000024C2-\U0001F251"  # misc
        "\U0001F900-\U0001F9FF"  # supplemental symbols
        "\U0001FA00-\U0001FA6F"  # chess symbols
        "\U0001FA70-\U0001FAFF"  # symbols extended
        "\U00002600-\U000026FF"  # misc symbols
        "\U00002700-\U000027BF"  # dingbats
        "]+",
        flags=re.UNICODE
    )

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

        Full pipeline:
        1. Unicode normalization
        2. Strip emojis
        3. Strip markdown formatting
        4. Sanitize special characters
        5. Clean PDF artifacts
        6. Expand abbreviations
        7. Convert numbers to words
        8. Final whitespace cleanup

        Args:
            text: Raw input text

        Returns:
            Normalized text ready for TTS
        """
        original_len = len(text)
        original_newlines = text.count('\n')
        original_paragraphs = text.count('\n\n') + 1

        logger.debug(f"Normalize input: {original_len} chars, {original_newlines} newlines, ~{original_paragraphs} paragraphs")
        logger.debug(f"Input preview: {preview_text(text, 300)}")

        # 1. Normalize Unicode (NFKC handles ligatures, special chars)
        text = unicodedata.normalize('NFKC', text)

        # 2. Strip emojis (before any text processing)
        text = self._strip_emojis(text)

        # 3. Strip markdown formatting
        text = self._strip_markdown(text)

        # 4. Sanitize special characters (URLs, emails, symbols)
        text = self._sanitize_special_chars(text)

        # 5. Remove PDF artifacts (page numbers, excessive whitespace)
        text = re.sub(r'\n\s*\d+\s*\n', '\n', text)  # Page numbers
        text = re.sub(r'\s{3,}', ' ', text)  # Multiple spaces
        text = re.sub(r'\n{3,}', '\n\n', text)  # Excessive newlines

        # Remove hyphenation at line breaks
        text = re.sub(r'-\n', '', text)

        # 6. Expand common abbreviations
        for pattern, replacement in self.ABBREVIATIONS.items():
            text = re.sub(pattern, replacement, text, flags=re.IGNORECASE)

        # 7. Convert numbers to words (for better TTS pronunciation)
        text = self._convert_numbers_to_words(text)

        # 8. Clean up whitespace while PRESERVING paragraph breaks
        # First normalize line endings
        text = text.replace('\r\n', '\n').replace('\r', '\n')

        # Count paragraphs before whitespace normalization
        pre_whitespace_paragraphs = text.count('\n\n') + 1

        # Collapse multiple spaces (but not newlines) to single space
        text = re.sub(r'[^\S\n]+', ' ', text)
        # Normalize paragraph breaks: 2+ newlines become double newline
        text = re.sub(r'\n{2,}', '\n\n', text)

        # Handle single newlines intelligently:
        # - If line ends with sentence punctuation (.!?) and next line starts with
        #   uppercase or number, treat as paragraph break (convert to double newline)
        # - Otherwise convert to space (soft wrap)
        def handle_single_newline(match):
            before = match.group(1)  # Character before newline
            after = match.group(2)   # Character after newline
            # Sentence end followed by new sentence = paragraph break
            if before in '.!?:' and (after.isupper() or after.isdigit()):
                return before + '\n\n' + after
            # Otherwise it's a soft wrap
            return before + ' ' + after

        # Match: char + single newline (not preceded/followed by newline) + char
        text = re.sub(r'([^\n])\n(?!\n)([^\n])', handle_single_newline, text)

        # Clean up spaces around paragraph breaks
        text = re.sub(r' *\n\n *', '\n\n', text)
        # Strip leading/trailing whitespace from each line
        text = '\n\n'.join(line.strip() for line in text.split('\n\n'))

        final_len = len(text)
        final_newlines = text.count('\n')
        final_paragraphs = text.count('\n\n') + 1

        logger.info(f"Normalize: {original_len} -> {final_len} chars, {original_newlines} -> {final_newlines} newlines, {pre_whitespace_paragraphs} -> {final_paragraphs} paragraphs")
        logger.debug(f"Output preview: {preview_text(text, 300)}")

        return text.strip()

    def _strip_emojis(self, text: str) -> str:
        """
        Remove emojis to prevent TTS issues.

        Args:
            text: Input text potentially containing emojis

        Returns:
            Text with emojis removed
        """
        return self.EMOJI_PATTERN.sub(' ', text)

    def _strip_markdown(self, text: str) -> str:
        """
        Strip markdown formatting for clean TTS output.

        Handles: headers, bold, italic, links, code, horizontal rules,
        list markers, and blockquotes.

        Args:
            text: Input text potentially containing markdown

        Returns:
            Plain text without markdown formatting
        """
        # Code blocks: ```lang\ncode\n``` → code (do this first)
        text = re.sub(r'```[^\n]*\n(.*?)```', r'\1', text, flags=re.DOTALL)

        # Inline code: `code` → code
        text = re.sub(r'`([^`]+)`', r'\1', text)

        # Headers: ## Title → Title
        text = re.sub(r'^#{1,6}\s+', '', text, flags=re.MULTILINE)

        # Bold/italic: **text** or *text* or __text__ or _text_ → text
        text = re.sub(r'\*\*([^*]+)\*\*', r'\1', text)
        text = re.sub(r'\*([^*]+)\*', r'\1', text)
        text = re.sub(r'__([^_]+)__', r'\1', text)
        text = re.sub(r'_([^_]+)_', r'\1', text)

        # Links: [text](url) → text
        text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)

        # Images: ![alt](url) → alt
        text = re.sub(r'!\[([^\]]*)\]\([^)]+\)', r'\1', text)

        # Horizontal rules: --- or *** or ___ → (remove)
        text = re.sub(r'^[-*_]{3,}\s*$', '', text, flags=re.MULTILINE)

        # List markers: - item or * item or + item → item
        text = re.sub(r'^\s*[-*+]\s+', '', text, flags=re.MULTILINE)

        # Numbered lists: 1. item → item
        text = re.sub(r'^\s*\d+\.\s+', '', text, flags=re.MULTILINE)

        # Blockquotes: > text → text
        text = re.sub(r'^>\s*', '', text, flags=re.MULTILINE)

        return text

    def _sanitize_special_chars(self, text: str) -> str:
        """
        Replace or remove special characters that shouldn't be vocalized.

        Handles: URLs, emails, file paths, hashtags, mentions,
        and various symbols.

        Args:
            text: Input text with special characters

        Returns:
            Text with special characters handled appropriately
        """
        # URLs → remove (handles URLs in parentheses, brackets, quotes)
        # Match http/https URLs including those ending with punctuation
        text = re.sub(r'https?://[^\s<>"\'()\[\]{}]+[^\s<>"\'()\[\]{},.:;!?]', ' ', text)
        # Also catch URLs that might be wrapped in parentheses: (https://...)
        text = re.sub(r'\(https?://[^)]+\)', ' ', text)
        # And in brackets: [https://...]
        text = re.sub(r'\[https?://[^\]]+\]', ' ', text)

        # Remove any remaining URL-like patterns (www.example.com)
        text = re.sub(r'www\.[^\s<>"\'()\[\]{}]+', ' ', text)

        # Email addresses → remove
        text = re.sub(r'[\w.+-]+@[\w.-]+\.\w{2,}', ' ', text)

        # File paths: /usr/bin/file or C:\path\file → remove
        text = re.sub(r'(?:[A-Za-z]:)?[/\\][\w./\\-]+', ' ', text)

        # Reference-style patterns often from copy-paste:
        # [1], [2], [citation], (source), (ref 1), etc.
        text = re.sub(r'\[\d+\]', ' ', text)  # [1], [23]
        text = re.sub(r'\(\d+\)', ' ', text)  # (1), (23)

        # Parenthetical metadata: (PDF), (link), (source: ...), (via ...), (accessed ...)
        text = re.sub(r'\((?:PDF|Link|Source|Via|Accessed|Retrieved|See|Cf\.?)[^)]*\)', ' ', text, flags=re.IGNORECASE)

        # Empty parentheses or brackets left after URL removal
        text = re.sub(r'\(\s*\)', ' ', text)
        text = re.sub(r'\[\s*\]', ' ', text)

        # Hashtags: #topic → topic (keep word, remove #)
        text = re.sub(r'#(\w+)', r'\1', text)

        # At-mentions: @username → username
        text = re.sub(r'@(\w+)', r'\1', text)

        # HTML/XML tags: <tag> → remove
        text = re.sub(r'<[^>]+>', ' ', text)

        # Ampersand → and
        text = re.sub(r'\s*&\s*', ' and ', text)

        # Plus sign between words → plus
        text = re.sub(r'\s+\+\s+', ' plus ', text)

        # Percent → percent
        text = re.sub(r'(\d+)\s*%', r'\1 percent', text)

        # Degree symbol → degrees
        text = re.sub(r'(\d+)\s*°([CF]?)', lambda m: f"{m.group(1)} degrees {m.group(2)}", text)

        # Copyright/trademark symbols
        text = re.sub(r'©', ' copyright ', text)
        text = re.sub(r'®', ' registered ', text)
        text = re.sub(r'™', ' trademark ', text)

        # Standalone special chars that shouldn't be spoken
        # Remove: / \ | ~ ^ ` < > { } [ ]
        text = re.sub(r'[/\\|~^`<>{}\[\]]', ' ', text)

        # Remove remaining standalone asterisks (not part of words)
        text = re.sub(r'\s\*+\s', ' ', text)
        text = re.sub(r'^\*+\s', '', text, flags=re.MULTILINE)

        return text

    def _convert_numbers_to_words(self, text: str) -> str:
        """
        Convert standalone numbers to words for better TTS.

        Handles both integers and decimal numbers.

        Args:
            text: Input text with numbers

        Returns:
            Text with numbers converted to words
        """
        def replace_decimal(match):
            """Convert decimal numbers: 3.14 → three point one four"""
            try:
                parts = match.group(0).split('.')
                whole = num2words(int(parts[0])) if parts[0] else 'zero'
                # Convert each decimal digit to words
                decimal_digits = ' '.join(num2words(int(d)) for d in parts[1])
                return f"{whole} point {decimal_digits}"
            except (ValueError, OverflowError):
                return match.group(0)

        def replace_integer(match):
            """Convert integers: 42 → forty-two"""
            try:
                num = match.group(0)
                # Only convert reasonable numbers (not years, IDs, etc.)
                if len(num) <= 4:
                    return num2words(int(num))
                return num
            except (ValueError, OverflowError):
                return match.group(0)

        # First handle decimals: 3.14, 0.5, etc.
        text = re.sub(r'\b\d+\.\d+\b', replace_decimal, text)

        # Then handle remaining standalone integers
        text = re.sub(r'\b\d{1,4}\b', replace_integer, text)

        return text

    def chunk_text(self, text: str) -> List[dict]:
        """
        Split text into optimal chunks for TTS processing.

        Chunks are split at sentence boundaries and target max_chunk_tokens.
        This ensures natural pauses and stays within Kokoro's 510 token limit.
        Paragraph breaks are preserved in the chunk metadata.

        Args:
            text: Preprocessed text to chunk

        Returns:
            List of chunk dicts: [{"text": str, "starts_paragraph": bool}, ...]
        """
        # First split into paragraphs to preserve structure
        paragraphs = text.split('\n\n')

        chunks = []

        for para_idx, paragraph in enumerate(paragraphs):
            if not paragraph.strip():
                continue

            # Split paragraph into sentences
            sentences = re.split(r'(?<=[.!?])\s+', paragraph.strip())

            current_chunk = []
            current_length = 0
            is_first_in_para = True

            for sentence in sentences:
                if not sentence.strip():
                    continue

                # Rough token estimate: ~4 characters per token
                sentence_tokens = len(sentence) // 4

                # If adding this sentence exceeds max, save current chunk
                if current_length + sentence_tokens > self.max_chunk_tokens and current_chunk:
                    chunks.append({
                        "text": ' '.join(current_chunk),
                        "starts_paragraph": is_first_in_para
                    })
                    current_chunk = [sentence]
                    current_length = sentence_tokens
                    is_first_in_para = False
                else:
                    current_chunk.append(sentence)
                    current_length += sentence_tokens

            # Add remaining sentences from this paragraph
            if current_chunk:
                chunks.append({
                    "text": ' '.join(current_chunk),
                    "starts_paragraph": is_first_in_para
                })

        return chunks

    def process(self, text: str) -> List[dict]:
        """
        Full preprocessing pipeline: normalize and chunk text.

        Args:
            text: Raw input text

        Returns:
            List of chunk dicts: [{"text": str, "starts_paragraph": bool}, ...]
        """
        logger.info(f"Processing text: {len(text)} chars input")
        normalized = self.normalize(text)
        chunks = self.chunk_text(normalized)
        logger.info(f"Processing complete: {len(chunks)} chunks created")
        return chunks
