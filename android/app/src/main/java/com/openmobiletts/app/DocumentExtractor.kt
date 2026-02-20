package com.openmobiletts.app

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Extracts plain text from PDF, DOCX, and TXT files.
 *
 * - PDF: Uses pdfbox-android, page-by-page extraction to limit memory usage.
 * - DOCX: Zero-dependency ZIP + SAX parsing of word/document.xml.
 * - TXT: Direct UTF-8 read.
 */
object DocumentExtractor {

    private const val TAG = "DocumentExtractor"
    private val SUPPORTED_EXTENSIONS = setOf("pdf", "docx", "txt")

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
        AppLog.i(TAG, "PDFBox resource loader initialized")
    }

    /**
     * Extract text from a file based on its extension.
     * @throws IllegalArgumentException for unsupported formats or unreadable files
     */
    fun extract(file: File, originalFilename: String): String {
        val ext = originalFilename.substringAfterLast('.', "").lowercase()

        if (ext !in SUPPORTED_EXTENSIONS) {
            throw IllegalArgumentException(
                "Unsupported file format: .$ext. Supported formats: PDF, DOCX, TXT."
            )
        }

        return when (ext) {
            "pdf" -> extractPdf(file)
            "docx" -> extractDocx(file)
            "txt" -> extractTxt(file)
            else -> throw IllegalArgumentException("Unsupported format: .$ext")
        }
    }

    private fun extractPdf(file: File): String {
        val doc = try {
            PDDocument.load(file)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot open PDF: ${e.message}")
        }

        return doc.use { document ->
            if (document.isEncrypted) {
                throw IllegalArgumentException("Password-protected PDFs are not supported.")
            }

            val totalPages = document.numberOfPages
            if (totalPages == 0) {
                throw IllegalArgumentException("PDF has no pages.")
            }

            AppLog.i(TAG, "Extracting text from $totalPages PDF pages")

            val stripper = PDFTextStripper()
            val pages = mutableListOf<String>()

            for (i in 1..totalPages) {
                stripper.startPage = i
                stripper.endPage = i
                val pageText = stripper.getText(document).trim()
                if (pageText.isNotEmpty()) {
                    pages.add(pageText)
                }
            }

            val result = pages.joinToString("\n\n")
            cleanWhitespace(result)
        }
    }

    private fun extractDocx(file: File): String {
        val xmlContent = file.inputStream().use { fis ->
            val zis = ZipInputStream(fis)
            var entry = zis.nextEntry
            var found: ByteArray? = null

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    found = zis.readBytes()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }

            found ?: throw IllegalArgumentException(
                "Invalid DOCX file: missing word/document.xml"
            )
        }

        return parseDocumentXml(xmlContent.inputStream())
    }

    private fun parseDocumentXml(input: InputStream): String {
        val paragraphs = mutableListOf<String>()
        val currentParagraph = StringBuilder()

        val handler = object : DefaultHandler() {
            private var insideText = false

            override fun startElement(
                uri: String, localName: String, qName: String, attributes: Attributes
            ) {
                when {
                    localName == "t" || qName == "w:t" -> insideText = true
                    localName == "p" || qName == "w:p" -> currentParagraph.clear()
                }
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                when {
                    localName == "t" || qName == "w:t" -> insideText = false
                    localName == "p" || qName == "w:p" -> {
                        val text = currentParagraph.toString().trim()
                        if (text.isNotEmpty()) {
                            paragraphs.add(text)
                        }
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (insideText) {
                    currentParagraph.append(ch, start, length)
                }
            }
        }

        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
        }
        factory.newSAXParser().parse(input, handler)

        val result = paragraphs.joinToString("\n\n")
        return cleanWhitespace(result)
    }

    private fun extractTxt(file: File): String {
        return file.readText(Charsets.UTF_8)
    }

    private fun cleanWhitespace(text: String): String {
        return text
            .replace(Regex("[ \\t]+"), " ")       // collapse horizontal whitespace
            .replace(Regex("\\n{3,}"), "\n\n")     // collapse 3+ newlines to 2
            .trim()
    }
}
