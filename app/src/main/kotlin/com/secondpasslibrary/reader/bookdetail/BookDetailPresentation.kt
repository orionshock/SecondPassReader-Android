package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation
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
    val seriesNavigationId: String?,
    val authorsLabel: String?,
    val authorNavigationId: String?,
    val publicationLabel: String?,
    val description: String?,
    val fileLabel: String?
)

internal fun BookDetailState.appBarPresentation(context: String) = AppBarPresentation(
    navigation = AppBarNavigation.BACK,
    context = context,
    title = detail?.title ?: "Book"
)

internal fun LibraryBookDetail.toPresentation() = BookDetailPresentation(
    title = title,
    subtitle = subtitle.takeIf(String::isNotBlank),
    seriesLabel = series?.let { value ->
        value.seriesIndex?.let { "${value.name} \u00b7 ${it.value}" } ?: value.name
    },
    seriesNavigationId = series?.id,
    authorsLabel = authors.joinToString(", ") { it.name }.takeIf(String::isNotBlank),
    authorNavigationId = authors.singleOrNull()?.id,
    publicationLabel = publicationLabel(),
    description = description.takeIf(String::isNotBlank),
    fileLabel = file?.let {
        "${it.format.uppercase(Locale.ROOT)} \u00b7 ${fileSizeLabel(it.fileSize)}"
    }
)

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
