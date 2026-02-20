package com.openmobiletts.app

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * In-app log ring buffer. Mirrors android.util.Log but also stores entries
 * so they can be exported via the /api/logs endpoint (matching the Python
 * server's JSON format).
 *
 * Thread-safe: uses ConcurrentLinkedDeque for lock-free access.
 */
object AppLog {
    private const val MAX_ENTRIES = 500

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

    private fun add(level: String, tag: String, msg: String) {
        val entry = Entry(
            timestamp = dateFormat.get()!!.format(Date()),
            level = level,
            tag = tag,
            message = msg,
        )
        buffer.addLast(entry)
        while (buffer.size > MAX_ENTRIES) {
            buffer.pollFirst()
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

    /** Export all buffered entries as a JSON string matching the Python server format. */
    fun exportJson(): String {
        val arr = JSONArray()
        for (entry in buffer) {
            arr.put(JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("level", entry.level)
                put("logger", entry.tag)
                put("message", entry.message)
            })
        }
        return JSONObject().apply {
            put("logs", arr)
            put("count", arr.length())
        }.toString()
    }
}
