package com.openmobiletts.app

import java.text.Normalizer

/**
 * Text preprocessing for TTS — normalization, sanitization, and chunking.
 *
 * Ports the desktop Python TextPreprocessor to Android to ensure high-quality
 * TTS output. Raw text from document extraction or user input goes through
 * an 8-step normalization pipeline before being chunked into TTS-ready segments.
 *
 * Pipeline:
 *   1. Unicode NFKC normalization (ligatures, smart quotes)
 *   2. Emoji removal
 *   3. Markdown formatting removal
 *   4. Special character sanitization (URLs, emails, symbols)
 *   5. PDF artifact cleanup (page numbers, hyphenated line breaks)
 *   6. Abbreviation expansion (Dr. → Doctor, etc.)
 *   7. Number-to-words conversion (42 → forty-two)
 *   8. Whitespace normalization (preserving paragraph breaks)
 *
 * Then chunks the normalized text at paragraph and sentence boundaries
 * with a token budget that stays safely under Kokoro's ~510 token limit.
 */
object TextPreprocessor {

    private const val TAG = "TextPreprocessor"

    // Chunking budget: Kokoro's token limit is ~510.
    // At ~4 chars/token, TARGET_TOKENS=250 ≈ 1000 chars, MAX_TOKENS=400 ≈ 1600 chars.
    private const val TARGET_TOKENS = 250
    private const val MAX_TOKENS = 400
    private const val CHARS_PER_TOKEN = 4

    /** A chunk of text ready for TTS generation. */
    data class TextChunk(val text: String, val startsParagraph: Boolean)

    // Common abbreviations → expansions (case-insensitive regex)
    private val ABBREVIATION_REGEXES: List<Pair<Regex, String>> = listOf(
        "\\bDr\\." to "Doctor",
        "\\bMr\\." to "Mister",
        "\\bMrs\\." to "Missus",
        "\\bMs\\." to "Miss",
        "\\bProf\\." to "Professor",
        "\\bSr\\." to "Senior",
        "\\bJr\\." to "Junior",
        "\\bInc\\." to "Incorporated",
        "\\bLtd\\." to "Limited",
        "\\bCo\\." to "Company",
        "\\bCorp\\." to "Corporation",
        "\\bAve\\." to "Avenue",
        "\\bSt\\." to "Street",
        "\\bRd\\." to "Road",
        "\\bBlvd\\." to "Boulevard",
        "\\betc\\." to "et cetera",
        "\\be\\.g\\." to "for example",
        "\\bi\\.e\\." to "that is",
        "\\bvs\\." to "versus",
    ).map { (pattern, replacement) ->
        Regex(pattern, RegexOption.IGNORE_CASE) to replacement
    }

    // Number words lookup tables
    private val ONES = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven",
        "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty",
        "sixty", "seventy", "eighty", "ninety",
    )

    // ---------- Public API ----------

    /**
     * Full preprocessing pipeline: normalize text and chunk for TTS.
     * @param text Raw input text (from user or document extraction)
     * @return List of text chunks ready for TTS generation
     */
    fun process(text: String): List<TextChunk> {
        val inputLen = text.length
        AppLog.i(TAG, "Processing $inputLen chars")
        val normalized = normalize(text)
        val chunks = chunkText(normalized)
        AppLog.i(TAG, "Processed: $inputLen → ${normalized.length} chars, ${chunks.size} chunks")
        return chunks
    }

    /**
     * Normalize text through the 8-step pipeline.
     */
    fun normalize(text: String): String {
        var t = text

        // 1. Unicode NFKC (collapses ligatures, smart quotes, half-width chars)
        t = Normalizer.normalize(t, Normalizer.Form.NFKC)

        // 2. Strip emojis
        t = stripEmojis(t)

        // 3. Strip markdown formatting
        t = stripMarkdown(t)

        // 4. Sanitize special characters (URLs, emails, symbols)
        t = sanitizeSpecialChars(t)

        // 5. Clean PDF artifacts (page numbers, hyphenation, excess whitespace)
        t = cleanPdfArtifacts(t)

        // 6. Expand abbreviations (Dr. → Doctor, etc.)
        t = expandAbbreviations(t)

        // 7. Convert numbers to words (42 → forty-two)
        t = convertNumbers(t)

        // 8. Clean whitespace while preserving paragraph breaks
        t = cleanWhitespace(t)

        return t.trim()
    }

    /**
     * Chunk normalized text at paragraph and sentence boundaries.
     * Each chunk stays within the token budget for Kokoro TTS.
     */
    fun chunkText(text: String): List<TextChunk> {
        val paragraphs = text.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<TextChunk>()

        for (paragraph in paragraphs) {
            val sentences = splitSentences(paragraph)
            val grouped = groupSentences(sentences)

            for ((i, chunkText) in grouped.withIndex()) {
                result.add(TextChunk(text = chunkText, startsParagraph = i == 0))
            }
        }

        // Fallback: if no chunks produced, use the whole text
        if (result.isEmpty() && text.isNotBlank()) {
            result.add(TextChunk(text = text.trim(), startsParagraph = true))
        }

        return result
    }

    // ---------- Step 2: Emoji removal ----------

    private fun stripEmojis(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)

            val isEmoji = cp in 0x1F600..0x1F64F ||  // Emoticons
                    cp in 0x1F300..0x1F5FF ||  // Misc Symbols and Pictographs
                    cp in 0x1F680..0x1F6FF ||  // Transport and Map
                    cp in 0x1F1E0..0x1F1FF ||  // Flags
                    cp in 0x1F900..0x1F9FF ||  // Supplemental Symbols
                    cp in 0x1FA00..0x1FA6F ||  // Chess Symbols
                    cp in 0x1FA70..0x1FAFF ||  // Extended Symbols
                    cp in 0x2600..0x26FF ||    // Misc Symbols
                    cp in 0x2702..0x27B0 ||    // Dingbats
                    cp in 0x2700..0x27BF ||    // Dingbats extended
                    cp in 0xFE00..0xFE0F ||    // Variation Selectors
                    cp == 0x200D ||             // Zero Width Joiner
                    cp == 0x20E3               // Combining Enclosing Keycap

            if (!isEmoji) {
                sb.appendCodePoint(cp)
            } else {
                sb.append(' ')
            }
            i += charCount
        }
        return sb.toString()
    }

    // ---------- Step 3: Markdown removal ----------

    private fun stripMarkdown(text: String): String {
        var t = text
        // Code blocks (fenced) — keep content, strip delimiters
        t = t.replace(Regex("```[^\\n]*\\n([\\s\\S]*?)```")) { it.groupValues[1] }
        // Inline code → content
        t = t.replace(Regex("`([^`]+)`")) { it.groupValues[1] }
        // Images ![alt](url) → alt
        t = t.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        // Links [text](url) → text
        t = t.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        // HTML tags
        t = t.replace(Regex("<[^>]+>"), "")
        // Headers
        t = t.replace(Regex("(?m)^#{1,6}\\s+"), "")
        // Bold/italic (symmetric delimiters only — process bold before italic)
        t = t.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        t = t.replace(Regex("\\*([^*]+)\\*"), "$1")
        t = t.replace(Regex("__([^_]+)__"), "$1")
        t = t.replace(Regex("_([^_]+)_"), "$1")
        // Strikethrough
        t = t.replace(Regex("~~([^~]+)~~"), "$1")
        // Blockquotes
        t = t.replace(Regex("(?m)^>\\s?"), "")
        // Horizontal rules
        t = t.replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")
        // List markers (bullet and numbered)
        t = t.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        t = t.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        return t
    }

    // ---------- Step 4: Special character sanitization ----------

    private fun sanitizeSpecialChars(text: String): String {
        var t = text
        // URLs (http/https)
        t = t.replace(Regex("https?://[^\\s<>\"'()\\[\\]{}]+[^\\s<>\"'()\\[\\]{},.:;!?]"), " ")
        t = t.replace(Regex("\\(https?://[^)]+\\)"), " ")
        t = t.replace(Regex("\\[https?://[^\\]]+\\]"), " ")
        t = t.replace(Regex("www\\.[^\\s<>\"'()\\[\\]{}]+"), " ")
        // Email addresses
        t = t.replace(Regex("[\\w.+-]+@[\\w.-]+\\.\\w{2,}"), " ")
        // File paths
        t = t.replace(Regex("(?:[A-Za-z]:)?[/\\\\][\\w./\\\\-]+"), " ")
        // Reference numbers [1], (1)
        t = t.replace(Regex("\\[\\d+\\]"), " ")
        t = t.replace(Regex("\\(\\d+\\)"), " ")
        // Parenthetical metadata: (PDF), (Source: ...), etc.
        t = t.replace(
            Regex("\\((?:PDF|Link|Source|Via|Accessed|Retrieved|See|Cf\\.?)[^)]*\\)", RegexOption.IGNORE_CASE),
            " ",
        )
        // Empty parens/brackets left after URL removal
        t = t.replace(Regex("\\(\\s*\\)"), " ")
        t = t.replace(Regex("\\[\\s*\\]"), " ")
        // Hashtags → word
        t = t.replace(Regex("#(\\w+)"), "$1")
        // @mentions → word
        t = t.replace(Regex("@(\\w+)"), "$1")
        // Ampersand → and
        t = t.replace(Regex("\\s*&\\s*"), " and ")
        // Plus sign between words → plus
        t = t.replace(Regex("\\s+\\+\\s+"), " plus ")
        // Percent → percent
        t = t.replace(Regex("(\\d+)\\s*%"), "$1 percent")
        // Degree → degrees
        t = t.replace(Regex("(\\d+)\\s*°([CF]?)")) { m ->
            "${m.groupValues[1]} degrees ${m.groupValues[2]}"
        }
        // Copyright/trademark symbols
        t = t.replace("©", " copyright ")
        t = t.replace("®", " registered ")
        t = t.replace("™", " trademark ")
        // Standalone special chars that shouldn't be spoken
        t = t.replace(Regex("[/\\\\|~^`<>{}\\[\\]]"), " ")
        // Standalone asterisks
        t = t.replace(Regex("\\s\\*+\\s"), " ")
        t = t.replace(Regex("(?m)^\\*+\\s"), "")
        return t
    }

    // ---------- Step 5: PDF artifact cleanup ----------

    private fun cleanPdfArtifacts(text: String): String {
        var t = text
        // Page numbers on their own line: \n 42 \n
        t = t.replace(Regex("\n\\s*\\d+\\s*\n"), "\n")
        // Multiple horizontal spaces (not newlines — preserve paragraph breaks)
        t = t.replace(Regex("[^\\S\\n]{3,}"), " ")
        // Excessive newlines
        t = t.replace(Regex("\n{3,}"), "\n\n")
        // Hyphenated line breaks from PDF column wrapping
        t = t.replace(Regex("-\n"), "")
        return t
    }

    // ---------- Step 6: Abbreviation expansion ----------

    private fun expandAbbreviations(text: String): String {
        var t = text
        for ((regex, replacement) in ABBREVIATION_REGEXES) {
            t = t.replace(regex, replacement)
        }
        return t
    }

    // ---------- Step 7: Number-to-words conversion ----------

    private fun convertNumbers(text: String): String {
        // Decimals first: 3.14 → three point one four
        var t = text.replace(Regex("\\b(\\d+)\\.(\\d+)\\b")) { m ->
            decimalToWords(m.groupValues[1], m.groupValues[2])
        }
        // Then standalone integers (1-4 digits only, matching Python behavior)
        t = t.replace(Regex("\\b\\d{1,4}\\b")) { m ->
            try {
                intToWords(m.value.toInt())
            } catch (_: NumberFormatException) {
                m.value
            }
        }
        return t
    }

    private fun decimalToWords(intPart: String, decPart: String): String {
        val whole = try {
            intToWords(intPart.toInt())
        } catch (_: Exception) {
            intPart
        }
        val digits = decPart.map { c ->
            try {
                ONES[c.digitToInt()]
            } catch (_: Exception) {
                c.toString()
            }
        }.joinToString(" ")
        return "$whole point $digits"
    }

    /** Convert integer 0–9999 to English words. */
    private fun intToWords(n: Int): String {
        if (n < 0) return "negative ${intToWords(-n)}"
        return when {
            n < 20 -> ONES[n]
            n < 100 -> {
                val t = TENS[n / 10]
                if (n % 10 > 0) "$t-${ONES[n % 10]}" else t
            }
            n < 1000 -> {
                val rest = n % 100
                if (rest > 0) "${ONES[n / 100]} hundred and ${intToWords(rest)}"
                else "${ONES[n / 100]} hundred"
            }
            n < 10000 -> {
                val rest = n % 1000
                if (rest > 0) "${intToWords(n / 1000)} thousand ${intToWords(rest)}"
                else "${intToWords(n / 1000)} thousand"
            }
            else -> n.toString()
        }
    }

    // ---------- Step 8: Whitespace cleanup ----------

    private fun cleanWhitespace(text: String): String {
        var t = text
        // Normalize line endings
        t = t.replace("\r\n", "\n").replace("\r", "\n")
        // Collapse horizontal whitespace (not newlines)
        t = t.replace(Regex("[^\\S\n]+"), " ")
        // Normalize paragraph breaks
        t = t.replace(Regex("\n{2,}"), "\n\n")
        // Handle single newlines: sentence end + uppercase/digit = paragraph break, else space.
        // Loop until stable because each match consumes two boundary characters,
        // so consecutive single-newlines require multiple passes.
        var prev = ""
        while (prev != t) {
            prev = t
            t = t.replace(Regex("([^\n])\n(?!\n)([^\n])")) { m ->
                val before = m.groupValues[1]
                val after = m.groupValues[2]
                if (before.last() in ".!?:" && (after.first().isUpperCase() || after.first().isDigit())) {
                    "$before\n\n$after"
                } else {
                    "$before $after"
                }
            }
        }
        // Clean spaces around paragraph breaks and trim each paragraph
        t = t.replace(Regex(" *\n\n *"), "\n\n")
        t = t.split("\n\n").joinToString("\n\n") { it.trim() }
        return t
    }

    // ---------- Chunking helpers ----------

    /** Split text at sentence boundaries. */
    private fun splitSentences(text: String): List<String> {
        // After abbreviation expansion, most periods are true sentence ends.
        // Split at .!? followed by whitespace.
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** Group sentences into chunks within the token budget. */
    private fun groupSentences(sentences: List<String>): List<String> {
        val targetChars = TARGET_TOKENS * CHARS_PER_TOKEN  // ~1000
        val maxChars = MAX_TOKENS * CHARS_PER_TOKEN        // ~1600

        val result = mutableListOf<String>()
        val current = StringBuilder()

        for (sentence in sentences) {
            // Split very long sentences at clause boundaries first
            val parts = if (sentence.length > maxChars) {
                splitLongText(sentence, maxChars)
            } else {
                listOf(sentence)
            }

            for (part in parts) {
                if (current.isEmpty()) {
                    current.append(part)
                } else if (current.length + part.length + 1 <= targetChars) {
                    current.append(" ").append(part)
                } else {
                    result.add(current.toString())
                    current.clear()
                    current.append(part)
                }
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    /** Split text exceeding maxLength at clause or word boundaries. */
    private fun splitLongText(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val result = mutableListOf<String>()
        var remaining = text

        while (remaining.length > maxLength) {
            var breakAt = -1

            // Prefer clause-level breaks (comma, semicolon, colon, em-dash)
            for (i in maxLength downTo maxLength / 2) {
                if (i < remaining.length && remaining[i] in ",;:\u2014\u2013") {
                    breakAt = i + 1
                    break
                }
            }

            // Fall back to word boundary
            if (breakAt == -1) {
                for (i in maxLength downTo maxLength / 2) {
                    if (i < remaining.length && remaining[i] == ' ') {
                        breakAt = i
                        break
                    }
                }
            }

            // Hard break as last resort
            if (breakAt == -1) breakAt = maxLength

            val chunk = remaining.substring(0, breakAt).trim()
            if (chunk.isNotEmpty()) result.add(chunk)
            val newRemaining = remaining.substring(breakAt).trim()
            if (newRemaining.length >= remaining.length) break // safety: ensure forward progress
            remaining = newRemaining
        }

        if (remaining.isNotEmpty()) result.add(remaining)

        return result
    }
}
