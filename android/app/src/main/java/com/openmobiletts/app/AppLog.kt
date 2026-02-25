package com.openmobiletts.app

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors

/**
 * Persistent application logger with in-memory ring buffer and file-backed storage.
 *
 * All log entries are appended to daily log files (JSONL format) in the app's
 * internal storage, surviving process restarts and crashes. The in-memory ring
 * buffer provides fast access for the current session.
 *
 * Log files are rotated daily and retained for 7 days. The /api/logs endpoint
 * returns all persisted logs, including entries from previous sessions.
 *
 * Thread-safe: in-memory buffer uses ConcurrentLinkedDeque, file writes are
 * serialized through a single-threaded executor to avoid blocking callers.
 */
object AppLog {
    private const val MAX_MEMORY_ENTRIES = 1000
    private const val RETENTION_DAYS = 7
    private const val LOG_DIR = "logs"

    private data class Entry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String,
    )

    private val buffer = ConcurrentLinkedDeque<Entry>()
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    }
    private val dayFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    // Single-threaded executor for non-blocking file writes
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "applog-writer").apply { isDaemon = true }
    }

    private var logDir: File? = null
    private var currentWriter: BufferedWriter? = null
    private var currentDay: String? = null

    /**
     * Initialize file-backed logging. Must be called once from Application.onCreate()
     * before any other AppLog calls. Without this, logs are still written to logcat
     * and the in-memory buffer, but won't survive process restarts.
     */
    fun init(filesDir: File) {
        val dir = File(filesDir, LOG_DIR)
        dir.mkdirs()
        logDir = dir
        cleanOldLogs(dir)
        i("AppLog", "=== Session started ===")
    }

    private fun add(level: String, tag: String, msg: String) {
        val now = Date()
        val timestamp = dateFormat.get()!!.format(now)
        val entry = Entry(timestamp, level, tag, msg)

        // In-memory ring buffer (current session)
        buffer.addLast(entry)
        while (buffer.size > MAX_MEMORY_ENTRIES) {
            buffer.pollFirst()
        }

        // Async file write (non-blocking for callers)
        val dir = logDir ?: return
        val day = dayFormat.get()!!.format(now)
        writeExecutor.execute {
            try {
                if (day != currentDay) {
                    currentWriter?.close()
                    currentWriter = BufferedWriter(FileWriter(File(dir, "tts-$day.log"), true))
                    currentDay = day
                }
                currentWriter?.run {
                    write(entryToJson(entry).toString())
                    newLine()
                    flush()
                }
            } catch (e: Exception) {
                Log.e("AppLog", "Log file write failed", e)
            }
        }
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        add("DEBUG", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        add("INFO", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        add("WARNING", tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, msg, throwable)
            add("ERROR", tag, "$msg: ${throwable.message}")
        } else {
            Log.e(tag, msg)
            add("ERROR", tag, msg)
        }
    }

    /**
     * Export all persisted logs as JSON matching the Python server format.
     * Reads from daily log files on disk, so entries from previous sessions
     * (up to [RETENTION_DAYS] days old) are included.
     */
    fun exportJson(): String {
        // Flush pending writes so disk is up-to-date
        try {
            writeExecutor.submit { }.get()
        } catch (_: Exception) {}

        val arr = JSONArray()
        val dir = logDir

        if (dir != null && dir.exists()) {
            dir.listFiles { f -> f.name.startsWith("tts-") && f.name.endsWith(".log") }
                ?.sorted()
                ?.forEach { file ->
                    try {
                        file.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                if (line.isNotBlank()) {
                                    try {
                                        arr.put(JSONObject(line))
                                    } catch (_: Exception) {
                                        // Skip malformed lines
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppLog", "Failed to read ${file.name}", e)
                    }
                }
        } else {
            // Fallback: disk logging not initialized, use in-memory buffer
            for (entry in buffer) {
                arr.put(entryToJson(entry))
            }
        }

        return JSONObject().apply {
            put("logs", arr)
            put("count", arr.length())
        }.toString()
    }

    private fun entryToJson(entry: Entry) = JSONObject().apply {
        put("timestamp", entry.timestamp)
        put("level", entry.level)
        put("logger", entry.tag)
        put("message", entry.message)
    }

    private fun cleanOldLogs(dir: File) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60 * 60 * 1000
        dir.listFiles { f -> f.name.startsWith("tts-") && f.name.endsWith(".log") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach {
                it.delete()
                Log.d("AppLog", "Cleaned old log: ${it.name}")
            }
    }
}
