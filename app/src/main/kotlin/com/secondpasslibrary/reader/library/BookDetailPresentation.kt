package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.PublicationDatePrecision
import java.text.DecimalFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private const val BYTES_PER_UNIT = 1024.0
private const val BYTES_PER_UNIT_LONG = 1024L

internal data class BookDetailPresentation(
    val title: String,
    val subtitle: String?,
    val seriesLabel: String?,
    val authorsLabel: String?,
    val publicationLabel: String?,
    val description: String?,
    val fileLabel: String?
)

internal fun LibraryBookDetail.toPresentation() = BookDetailPresentation(
    title = title,
    subtitle = subtitle.takeIf(String::isNotBlank),
    seriesLabel = series?.let { value ->
        value.seriesIndex?.let { "${value.name} \u00b7 ${it.value}" } ?: value.name
    },
    authorsLabel = authors.joinToString(", ") { it.name }.takeIf(String::isNotBlank),
    publicationLabel = publicationLabel(),
    description = BookDescriptionPresenter.present(description),
    fileLabel = file?.let {
        "${it.format.uppercase(Locale.ROOT)} \u00b7 ${fileSizeLabel(it.fileSize)}"
    }
)

internal object BookDescriptionPresenter {
    private val hiddenBlocks = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val lineBreaks = Regex("(?i)<br\\s*/?>|</p\\s*>|</div\\s*>|</li\\s*>|</h[1-6]\\s*>")
    private val listItems = Regex("(?i)<li(?:\\s[^>]*)?>")
    private val tags = Regex("(?s)<[^>]+>")
    private val horizontalWhitespace = Regex("[\\t\\x0B\\f\\r ]+")
    private val numericEntity = Regex("&#(\\d+);")

    fun present(value: String): String? {
        val text = value
            .replace(hiddenBlocks, "")
            .replace(lineBreaks, "\n")
            .replace(listItems, "\n\u2022 ")
            .replace(tags, "")
            .decodeEntities()
            .lineSequence()
            .map { it.replace(horizontalWhitespace, " ").trim() }
            .filter(String::isNotBlank)
            .joinToString("\n")
            .trim()
        return text.takeIf(String::isNotBlank)
    }

    private fun String.decodeEntities(): String = numericEntity.replace(
        replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
    ) { match ->
        match.groupValues[1].toIntOrNull()?.takeIf(Character::isValidCodePoint)
            ?.let(Character::toChars)?.concatToString() ?: ""
    }
}

internal fun fileSizeLabel(bytes: Long): String {
    require(bytes >= 0) { "File size must not be negative." }
    if (bytes < BYTES_PER_UNIT_LONG) return "$bytes B"
    val units = listOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= BYTES_PER_UNIT && unitIndex < units.lastIndex) {
        value /= BYTES_PER_UNIT
        unitIndex += 1
    }
    return "${DecimalFormat("0.#").format(value)} ${units[unitIndex]}"
}

private fun LibraryBookDetail.publicationLabel(): String? {
    val year = publishedYear ?: return null
    return when (publicationDatePrecision) {
        PublicationDatePrecision.UNSPECIFIED -> null

        PublicationDatePrecision.YEAR -> year.toString()

        PublicationDatePrecision.MONTH -> publishedMonth?.monthName()?.let { "$it $year" }

        PublicationDatePrecision.DAY -> publishedMonth?.monthName()?.let { month ->
            publishedDay?.let { "$month $it, $year" }
        }
    }
}

private fun Int.monthName(): String? = runCatching {
    Month.of(this).getDisplayName(TextStyle.SHORT, Locale.getDefault())
}.getOrNull()
