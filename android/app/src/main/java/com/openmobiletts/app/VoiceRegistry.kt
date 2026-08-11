package com.openmobiletts.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Product-accepted English voices from the 53-speaker Kokoro package.
 * Speaker prefixes are not treated as proof of language support.
 */
object VoiceRegistry {

    data class VoiceInfo(
        val name: String,
        val sid: Int,
        val language: String,
        val languageName: String,
        val gender: String,
        val displayName: String,
    )

    private val LANG_MAP = mapOf(
        'a' to ("en-us" to "English (US)"),
        'b' to ("en-gb" to "English (UK)"),
    )

    val voices: List<VoiceInfo> = listOf(
        // English US — Female
        v("af_alloy", 0), v("af_aoede", 1), v("af_bella", 2), v("af_heart", 3),
        v("af_jessica", 4), v("af_kore", 5), v("af_nicole", 6), v("af_nova", 7),
        v("af_river", 8), v("af_sarah", 9), v("af_sky", 10),
        // English US — Male
        v("am_adam", 11), v("am_echo", 12), v("am_eric", 13), v("am_fenrir", 14),
        v("am_liam", 15), v("am_michael", 16), v("am_onyx", 17), v("am_puck", 18),
        v("am_santa", 19),
        // English UK — Female
        v("bf_alice", 20), v("bf_emma", 21), v("bf_isabella", 22), v("bf_lily", 23),
        // English UK — Male
        v("bm_daniel", 24), v("bm_fable", 25), v("bm_george", 26), v("bm_lewis", 27),
    )

    private val kittenVoices: List<VoiceInfo> = listOf(
        kitten("Bella", 1, "female"),
        kitten("Jasper", 0, "male"),
        kitten("Luna", 3, "female"),
        kitten("Bruno", 2, "male"),
        kitten("Rosie", 5, "female"),
        kitten("Hugo", 4, "male"),
        kitten("Kiki", 7, "female"),
        kitten("Leo", 6, "male"),
    )

    fun voicesFor(modelId: String): List<VoiceInfo> =
        if (modelId.startsWith("kitten-")) kittenVoices else voices

    fun sidForName(name: String, modelId: String): Int? =
        voicesFor(modelId).firstOrNull { it.name == name }?.sid

    fun defaultSidFor(modelId: String): Int = voicesFor(modelId).first().sid

    /**
     * Returns JSON array matching the /api/voices response format.
     */
    fun toJsonArray(modelId: String): String {
        val arr = JSONArray()
        for (v in voicesFor(modelId)) {
            arr.put(JSONObject().apply {
                put("name", v.name)
                put("language", v.language)
                put("language_name", v.languageName)
                put("gender", v.gender)
                put("display_name", v.displayName)
            })
        }
        return arr.toString()
    }

    /** Build a VoiceInfo from voice name and SID, deriving language/gender/display. */
    private fun v(name: String, sid: Int): VoiceInfo {
        val prefix = name[0]
        val genderChar = name[1]
        val (langCode, langName) = LANG_MAP[prefix] ?: ("en-us" to "English (US)")
        val display = name.substringAfter('_').replaceFirstChar { it.uppercase() }
        val gender = if (genderChar == 'f') "female" else "male"
        return VoiceInfo(name, sid, langCode, langName, gender, display)
    }

    private fun kitten(name: String, sid: Int, gender: String) = VoiceInfo(
        name = name,
        sid = sid,
        language = "en-us",
        languageName = "English",
        gender = gender,
        displayName = name,
    )
}
