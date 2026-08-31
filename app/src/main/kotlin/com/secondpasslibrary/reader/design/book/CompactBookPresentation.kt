package com.secondpasslibrary.reader.design.book

import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.PublicBookCoverReference

internal data class CompactBookPresentation(
    val id: String,
    val title: String,
    val subtitle: String?,
    val authors: String?,
    val series: String?,
    val publisher: String?,
    val language: String?,
    val format: String?,
    val cover: PublicBookCoverReference?
)

internal fun CompactBook.toCompactBookPresentation() = CompactBookPresentation(
    id = id,
    title = title,
    subtitle = subtitle.takeIf(String::isNotBlank),
    authors = authors.joinToString { it.name }.takeIf(String::isNotBlank),
    series =
        series?.let { value ->
            value.seriesIndex?.let { "${value.name} #${it.value}" } ?: value.name
        },
    publisher = publisher?.takeIf(String::isNotBlank),
    language = language?.takeIf(String::isNotBlank),
    format = fileFormat.takeIf(String::isNotBlank)?.uppercase(),
    cover = cover
)
