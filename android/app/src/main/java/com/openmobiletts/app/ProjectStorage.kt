package com.openmobiletts.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JSON-based project storage for user sessions.
 *
 * Each project is a folder with project.json (metadata) and content.txt (text).
 * Supports CRUD, auto-cleanup by age, and full export as JSON.
 *
 * Thread safety: all methods synchronize on the projects directory.
 * This is adequate for the expected scale (10-20 projects).
 */
class ProjectStorage(private val baseDir: File) {

    companion object {
        private const val TAG = "ProjectStorage"
        private const val PROJECTS_DIR = "projects"
    }

    private val projectsDir: File = File(baseDir, PROJECTS_DIR).also { it.mkdirs() }

    // ── CRUD ──────────────────────────────────────────

    /**
     * Create a new project. Returns the project ID.
     */
    @Synchronized
    fun create(title: String, type: String, content: String): String {
        val id = generateId()
        val dir = File(projectsDir, id)
        dir.mkdirs()

        val now = System.currentTimeMillis()
        val metadata = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("type", type)  // "tts", "stt", "import"
            put("created", now)
            put("modified", now)
        }

        File(dir, "project.json").writeText(metadata.toString(2))
        File(dir, "content.txt").writeText(content)

        AppLog.i(TAG, "Project created: $id ($title)")
        return id
    }

    /**
     * Get a project by ID. Returns null if not found.
     */
    @Synchronized
    fun get(id: String): JSONObject? {
        val dir = try { validateId(id) } catch (_: Exception) { return null }
        val metaFile = File(dir, "project.json")
        if (!metaFile.exists()) return null

        return try {
            val meta = JSONObject(metaFile.readText())
            val content = File(dir, "content.txt").let {
                if (it.exists()) it.readText() else ""
            }
            meta.put("content", content)
            meta
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to read project $id: ${e.message}")
            null
        }
    }

    /**
     * List all projects, sorted by modified date (newest first).
     */
    @Synchronized
    fun list(): JSONArray {
        val projects = JSONArray()
        val dirs = projectsDir.listFiles { f -> f.isDirectory } ?: return projects

        val sorted = dirs.mapNotNull { dir ->
            val metaFile = File(dir, "project.json")
            if (!metaFile.exists()) return@mapNotNull null
            try {
                JSONObject(metaFile.readText())
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.optLong("modified", 0) }

        for (proj in sorted) {
            projects.put(proj)
        }
        return projects
    }

    /**
     * Update a project's content and/or title. Returns true if found.
     */
    @Synchronized
    fun update(id: String, content: String? = null, title: String? = null): Boolean {
        val dir = try { validateId(id) } catch (_: Exception) { return false }
        val metaFile = File(dir, "project.json")
        if (!metaFile.exists()) return false

        try {
            val meta = JSONObject(metaFile.readText())
            meta.put("modified", System.currentTimeMillis())
            if (title != null) meta.put("title", title)
            metaFile.writeText(meta.toString(2))

            if (content != null) {
                File(dir, "content.txt").writeText(content)
            }

            AppLog.i(TAG, "Project updated: $id")
            return true
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to update project $id: ${e.message}")
            return false
        }
    }

    /**
     * Delete a project and all its files.
     */
    @Synchronized
    fun delete(id: String): Boolean {
        val dir = try { validateId(id) } catch (_: Exception) { return false }
        if (!dir.exists()) return false

        dir.deleteRecursively()
        AppLog.i(TAG, "Project deleted: $id")
        return true
    }

    // ── Auto-cleanup ──────────────────────────────────

    /**
     * Delete projects older than [maxAgeDays] days.
     * Returns the number of projects deleted.
     */
    @Synchronized
    fun cleanup(maxAgeDays: Int): Int {
        if (maxAgeDays <= 0) return 0

        val cutoff = System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000)
        var deleted = 0

        val dirs = projectsDir.listFiles { f -> f.isDirectory } ?: return 0

        for (dir in dirs) {
            val metaFile = File(dir, "project.json")
            if (!metaFile.exists()) {
                // Orphan directory — clean up
                dir.deleteRecursively()
                deleted++
                continue
            }

            try {
                val meta = JSONObject(metaFile.readText())
                val modified = meta.optLong("modified", 0)
                if (modified > 0 && modified < cutoff) {
                    dir.deleteRecursively()
                    deleted++
                }
            } catch (_: Exception) {
                // Corrupt metadata — delete
                dir.deleteRecursively()
                deleted++
            }
        }

        if (deleted > 0) {
            AppLog.i(TAG, "Auto-cleanup: deleted $deleted projects older than $maxAgeDays days")
        }
        return deleted
    }

    // ── Export ──────────────────────────────────────────

    /**
     * Export all projects as a single JSON object.
     * Includes metadata and text content but not audio files.
     */
    @Synchronized
    fun exportAll(): JSONObject {
        val result = JSONObject()
        result.put("exported_at", System.currentTimeMillis())
        result.put("format_version", 1)

        val projects = JSONArray()
        val dirs = projectsDir.listFiles { f -> f.isDirectory } ?: return result

        for (dir in dirs) {
            val project = get(dir.name) ?: continue
            projects.put(project)
        }

        result.put("projects", projects)
        result.put("count", projects.length())

        AppLog.i(TAG, "Exported ${projects.length()} projects")
        return result
    }

    // ── Helpers ──────────────────────────────────────────

    /** Validate project ID to prevent path traversal attacks. */
    private fun validateId(id: String): File {
        val dir = File(projectsDir, id).canonicalFile
        if (!dir.path.startsWith(projectsDir.canonicalPath)) {
            throw IllegalArgumentException("Invalid project ID")
        }
        return dir
    }

    private fun generateId(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val random = (1000..9999).random()
        return "proj_${timestamp}_$random"
    }
}
