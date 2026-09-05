package com.secondpasslibrary.reader.design.richtext

import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/** Defense-in-depth mirror of the server's descriptive-rich-text contract. */
internal object ServerRichTextSanitizer {
    private const val SAFE_FAILURE_TEXT = "Content unavailable."
    private val safelist = Safelist().addTags(
        "p",
        "br",
        "b",
        "strong",
        "i",
        "em",
        "ul",
        "ol",
        "li"
    )
    private val cleaner = Cleaner(safelist)

    fun sanitize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return try {
            val source = Jsoup.parseBodyFragment(value)
            source.select("script, style").remove()
            val clean = cleaner.clean(source)
            clean.outputSettings().prettyPrint(false)
            clean.body().html().takeIf(String::isNotBlank)
        } catch (_: Exception) {
            SAFE_FAILURE_TEXT
        }
    }
}
