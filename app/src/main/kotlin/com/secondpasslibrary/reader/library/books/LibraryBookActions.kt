package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.reader.design.book.BookCardAction

internal fun CompactBook.bookCardActions(): List<BookCardAction> = buildList {
    add(BookCardAction.BookDetails(id))
    add(BookCardAction.ReadingSessions(id))
    authors.singleOrNull()?.let { author ->
        add(BookCardAction.Author(id, author.id, author.name))
    }
    series?.let { value ->
        add(BookCardAction.Series(id, value.id, value.name))
    }
}
