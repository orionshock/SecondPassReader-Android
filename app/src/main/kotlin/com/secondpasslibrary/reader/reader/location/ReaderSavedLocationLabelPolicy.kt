package com.secondpasslibrary.reader.reader.location

import kotlin.math.roundToInt

/** Formats presentation-only location metadata persisted with new Reader writes. */
internal object ReaderSavedLocationLabelPolicy {
    fun create(totalProgression: Double?, sectionLabel: String?, spineOrdinal: Int?): String {
        val percentage = totalProgression
            ?.takeIf(Double::isFinite)
            ?.coerceIn(0.0, 1.0)
            ?.times(100)
            ?.roundToInt()
            ?: 0
        val prefix = percentage.toString().padStart(3, '0') + "% - "
        val suffix = usefulSectionLabel(sectionLabel)
            ?: boundaryLabel(percentage)
            ?: spineOrdinal?.takeIf { it > 0 }?.let {
                "Chapter ${it.toString().padStart(2, '0')}"
            }
            ?: "Location"
        return prefix + suffix.take(MAX_LOCATION_LABEL_LENGTH - prefix.length)
    }

    private fun usefulSectionLabel(value: String?): String? {
        val label = value?.trim()?.replace(WHITESPACE, " ")?.takeIf(String::isNotBlank)
            ?: return null
        return label.takeUnless {
            it.contains("epubcfi(", ignoreCase = true) ||
                it.contains("://") ||
                it.startsWith('/') ||
                it.startsWith("./") ||
                it.startsWith("../") ||
                it.contains('\\') ||
                it.contains('#') ||
                RENDERED_PAGE_FRACTION.containsMatchIn(it) ||
                it.endsWith(".html", ignoreCase = true) ||
                it.endsWith(".xhtml", ignoreCase = true)
        }
    }

    private fun boundaryLabel(percentage: Int): String? = when {
        percentage == 0 -> "Start"
        percentage >= END_PERCENTAGE -> "End"
        else -> null
    }
}

private val WHITESPACE = Regex("\\s+")
private val RENDERED_PAGE_FRACTION = Regex("\\bp\\d+/\\d+\\b", RegexOption.IGNORE_CASE)
private const val END_PERCENTAGE = 99
private const val MAX_LOCATION_LABEL_LENGTH = 255
