package com.buggybeginner.autotranslate

object Langs {
    val list: List<Pair<String, String>> = listOf(
        "English" to "en",
        "Hindi" to "hi",
        "Marathi" to "mr",
        "Bengali" to "bn",
        "Gujarati" to "gu",
        "Tamil" to "ta",
        "Telugu" to "te",
        "Kannada" to "kn",
        "Urdu" to "ur",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Portuguese" to "pt",
        "Russian" to "ru",
        "Arabic" to "ar",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Chinese" to "zh"
    )

    fun indexOf(code: String): Int = list.indexOfFirst { it.second == code }.coerceAtLeast(0)
}
