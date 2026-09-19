package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.reader.design.book.BookCardAction

internal fun CompactBook.bookCardActions(
    downloaded: Boolean = false,
    offline: Boolean = false,
    busy: Boolean = false
): List<BookCardAction> = buildList {
    if (!busy && fileFormat.equals("epub", ignoreCase = true)) {
        add(
            if (downloaded) {
                BookCardAction.RemoveDownload(
                    id
                )
            } else {
                BookCardAction.MakeAvailableOffline(id)
            }
        )
    }
    if (offline) return@buildList
    add(BookCardAction.BookDetails(id))
    add(BookCardAction.ReadingSessions(id))
    authors.singleOrNull()?.let { author ->
        add(BookCardAction.Author(id, author.id, author.name))
    }
    series?.let { value ->
        add(BookCardAction.Series(id, value.id, value.name))
    }
}
