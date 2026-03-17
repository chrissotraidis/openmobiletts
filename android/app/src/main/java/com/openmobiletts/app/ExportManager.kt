package com.openmobiletts.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/**
 * Handles document export to PDF, Markdown, and plain text.
 *
 * PDF uses Android's built-in PdfDocument API (zero dependencies).
 * MD and TXT are simple byte array construction.
 */
object ExportManager {

    private const val TAG = "ExportManager"

    // PDF layout constants
    private const val PAGE_WIDTH = 595   // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842  // A4 height in points
    private const val MARGIN_LEFT = 56f
    private const val MARGIN_RIGHT = 56f
    private const val MARGIN_TOP = 72f
    private const val MARGIN_BOTTOM = 72f
    private const val TITLE_SIZE = 18f
    private const val BODY_SIZE = 12f
    private const val LINE_SPACING = 1.4f
    private const val PAGE_NUMBER_SIZE = 10f

    /**
     * Export text as PDF bytes.
     * Creates pages with title, body text, and page numbers.
     */
    fun exportPdf(text: String, title: String): ByteArray {
        val document = PdfDocument()
        val usableWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val usableHeight = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = TITLE_SIZE
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = BODY_SIZE
            color = android.graphics.Color.DKGRAY
            isAntiAlias = true
        }

        val pageNumberPaint = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = PAGE_NUMBER_SIZE
            color = android.graphics.Color.GRAY
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val bodyLineHeight = BODY_SIZE * LINE_SPACING

        // Wrap text into lines that fit the usable width
        val lines = wrapText(text, bodyPaint, usableWidth)

        var pageNumber = 1
        var lineIndex = 0

        while (lineIndex < lines.size || pageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var yPos = MARGIN_TOP

            // Title on first page
            if (pageNumber == 1 && title.isNotBlank()) {
                canvas.drawText(title, MARGIN_LEFT, yPos + TITLE_SIZE, titlePaint)
                yPos += TITLE_SIZE * 2
            }

            // Body text
            val maxY = PAGE_HEIGHT - MARGIN_BOTTOM - PAGE_NUMBER_SIZE * 2
            while (lineIndex < lines.size && yPos + bodyLineHeight <= maxY) {
                canvas.drawText(lines[lineIndex], MARGIN_LEFT, yPos + BODY_SIZE, bodyPaint)
                yPos += bodyLineHeight
                lineIndex++
            }

            // Page number
            canvas.drawText(
                "$pageNumber",
                PAGE_WIDTH / 2f,
                PAGE_HEIGHT - MARGIN_BOTTOM / 2,
                pageNumberPaint
            )

            document.finishPage(page)
            pageNumber++

            // Safety: don't create empty pages beyond the first
            if (lineIndex >= lines.size) break
        }

        val outputStream = java.io.ByteArrayOutputStream()
        document.writeTo(outputStream)
        document.close()

        AppLog.i(TAG, "PDF exported: ${pageNumber - 1} pages, ${outputStream.size()} bytes")
        return outputStream.toByteArray()
    }

    /**
     * Export text as Markdown bytes.
     * Adds a title as an H1 header if provided.
     */
    fun exportMarkdown(text: String, title: String): ByteArray {
        val content = buildString {
            if (title.isNotBlank()) {
                appendLine("# $title")
                appendLine()
            }
            append(text)
        }
        return content.toByteArray(Charsets.UTF_8)
    }

    /**
     * Export text as plain text bytes.
     */
    fun exportPlainText(text: String, title: String): ByteArray {
        val content = buildString {
            if (title.isNotBlank()) {
                appendLine(title)
                appendLine("=".repeat(title.length.coerceAtMost(60)))
                appendLine()
            }
            append(text)
        }
        return content.toByteArray(Charsets.UTF_8)
    }

    /**
     * Word-wrap text to fit within a given width using the specified paint.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()

        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add("")
                continue
            }

            val words = paragraph.split(" ")
            val currentLine = StringBuilder()

            for (word in words) {
                val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(test) <= maxWidth) {
                    currentLine.clear().append(test)
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                    }
                    // Handle words wider than the page
                    if (paint.measureText(word) > maxWidth) {
                        lines.add(word) // force it on its own line
                    } else {
                        currentLine.clear().append(word)
                    }
                }
            }

            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }

        return lines
    }
}
