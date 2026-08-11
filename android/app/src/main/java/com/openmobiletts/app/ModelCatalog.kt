package com.openmobiletts.app

import android.content.Context
import kotlin.math.round
import org.json.JSONObject

/** Repository-owned model metadata shared with the desktop server. */
class ModelCatalog private constructor(
    val schemaVersion: Int,
    val reviewedAt: String,
    val rawJson: String,
    private val models: Map<String, ModelSpec>,
) {
    data class ModelSpec(
        val modelId: String,
        val label: String,
        val family: String,
        val version: String,
        val precision: String,
        val role: String,
        val status: String,
        val runtime: String,
        val minimumRuntimeVersion: String,
        val sourceUrl: String,
        val weightsLicense: String,
        val archiveLicense: String,
        val voiceLicense: String,
        val supportedLanguages: List<String>,
        val verifiedLanguages: List<String>,
        val exposedLanguages: List<String>,
        val url: String,
        val archiveBytes: Long,
        val installedBytes: Long,
        val sha256: String,
        val requiredFiles: List<String>,
        val requiredDirectories: List<String>,
        val minimumAppVersion: String,
        val smokeTest: String,
        val migration: String,
        val rollback: String,
    ) {
        val archiveSizeMb: Double
            get() = round(archiveBytes.toDouble() / 1024.0 / 1024.0 * 10.0) / 10.0

        val installedSizeMb: Double
            get() = round(installedBytes.toDouble() / 1024.0 / 1024.0 * 10.0) / 10.0
    }

    fun require(modelId: String): ModelSpec = models[modelId]
        ?: error("Model is missing from the shared catalog: $modelId")

    fun managedForRole(role: String, runtime: String? = null): List<ModelSpec> =
        models.values.filter {
            it.role == role &&
                (runtime == null || it.runtime == runtime) &&
                it.url.isNotBlank() &&
                it.archiveBytes > 0L &&
                it.sha256.isNotBlank()
        }

    fun requireRole(role: String): ModelSpec {
        val matches = models.values.filter {
            it.role == role && it.url.isNotBlank() && it.archiveBytes > 0L && it.sha256.isNotBlank()
        }
        check(matches.size == 1) { "Expected one managed $role model, found ${matches.size}" }
        return matches.single()
    }

    companion object {
        private const val ASSET_NAME = "model-catalog.v1.json"

        @Volatile
        private var cached: ModelCatalog? = null

        fun load(context: Context): ModelCatalog = cached ?: synchronized(this) {
            cached ?: parse(
                context.applicationContext.assets.open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() },
            ).also { cached = it }
        }

        internal fun parse(rawJson: String): ModelCatalog {
            val root = JSONObject(rawJson)
            val schemaVersion = root.getInt("schema_version")
            check(schemaVersion == 1) { "Unsupported model catalog schema: $schemaVersion" }
            val array = root.getJSONArray("models")
            check(array.length() > 0) { "Model catalog is empty" }

            val parsed = LinkedHashMap<String, ModelSpec>()
            for (index in 0 until array.length()) {
                val entry = array.getJSONObject(index)
                val id = entry.getString("id")
                check(id !in parsed) { "Duplicate model catalog ID: $id" }
                val runtime = entry.getJSONObject("runtime")
                val source = entry.getJSONObject("source")
                val license = entry.getJSONObject("license")
                parsed[id] = ModelSpec(
                    modelId = id,
                    label = entry.getString("label"),
                    family = entry.getString("family"),
                    version = entry.getString("version"),
                    precision = entry.getString("precision"),
                    role = entry.getString("role"),
                    status = entry.optString("status", "stable"),
                    runtime = runtime.getString("name"),
                    minimumRuntimeVersion = runtime.getString("minimum_version"),
                    sourceUrl = source.getString("url"),
                    weightsLicense = license.getString("weights"),
                    archiveLicense = license.getString("archive"),
                    voiceLicense = license.getString("voices"),
                    supportedLanguages = entry.getJSONArray("supported_languages").toStringList(),
                    verifiedLanguages = entry.getJSONArray("verified_languages").toStringList(),
                    exposedLanguages = entry.getJSONArray("exposed_languages").toStringList(),
                    url = entry.optionalString("archive_url"),
                    archiveBytes = entry.optLong("archive_bytes"),
                    installedBytes = entry.optLong("installed_bytes"),
                    sha256 = entry.optionalString("sha256"),
                    requiredFiles = entry.getJSONArray("required_files").toStringList(),
                    requiredDirectories = entry.getJSONArray("required_directories").toStringList(),
                    minimumAppVersion = entry.getString("minimum_app_version"),
                    smokeTest = entry.getString("smoke_test"),
                    migration = entry.getString("migration"),
                    rollback = entry.getString("rollback"),
                )
            }
            return ModelCatalog(
                schemaVersion = schemaVersion,
                reviewedAt = root.getString("reviewed_at"),
                rawJson = root.toString(),
                models = parsed,
            )
        }

        private fun org.json.JSONArray.toStringList(): List<String> =
            (0 until length()).map { getString(it) }

        private fun JSONObject.optionalString(key: String): String =
            if (isNull(key)) "" else optString(key, "")
    }
}
