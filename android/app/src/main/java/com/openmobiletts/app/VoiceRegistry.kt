package com.openmobiletts.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Static mapping of all 53 Kokoro voices.
 * Matches the Python server's sherpa_backend.py voice registry exactly.
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
        'e' to ("es" to "Spanish"),
        'f' to ("fr" to "French"),
        'h' to ("hi" to "Hindi"),
        'i' to ("it" to "Italian"),
        'j' to ("ja" to "Japanese"),
        'p' to ("pt-br" to "Portuguese"),
        'z' to ("zh" to "Chinese"),
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
        // Spanish
        v("ef_dora", 28), v("em_alex", 29),
        // French
        v("ff_siwis", 30),
        // Hindi
        v("hf_alpha", 31), v("hf_beta", 32), v("hm_omega", 33), v("hm_psi", 34),
        // Italian
        v("if_sara", 35), v("im_nicola", 36),
        // Japanese
        v("jf_alpha", 37), v("jf_gongitsune", 38), v("jf_nezumi", 39), v("jf_tebukuro", 40),
        v("jm_kumo", 41),
        // Portuguese
        v("pf_dora", 42), v("pm_alex", 43), v("pm_santa", 44),
        // Chinese
        v("zf_xiaobei", 45), v("zf_xiaoni", 46), v("zf_xiaoxiao", 47), v("zf_xiaoyi", 48),
        v("zm_yunjian", 49), v("zm_yunxi", 50), v("zm_yunxia", 51), v("zm_yunyang", 52),
    )

    private val byName = voices.associateBy { it.name }

    fun sidForName(name: String): Int? = byName[name]?.sid

    /**
     * Returns JSON array matching the /api/voices response format.
     */
    fun toJsonArray(): String {
        val arr = JSONArray()
        for (v in voices) {
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
}
