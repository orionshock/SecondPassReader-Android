package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.reader.design.book.CompactBookPresentation
import java.io.File

internal fun CompactBook.toLibraryCompactBookPresentation(localCover: File? = null) =
    CompactBookPresentation(
        id = id,
        title = title,
        subtitle = subtitle.takeIf(String::isNotBlank),
        authors = authors.joinToString { it.name }.takeIf(String::isNotBlank),
        series =
            series?.let { value ->
                value.seriesIndex?.let { "${value.name} · ${it.value}" } ?: value.name
            },
        publisher = publisher?.takeIf(String::isNotBlank),
        language = language?.takeIf(String::isNotBlank),
        format = fileFormat.takeIf(String::isNotBlank)?.uppercase(),
        cover = cover,
        localCover = localCover
    )
