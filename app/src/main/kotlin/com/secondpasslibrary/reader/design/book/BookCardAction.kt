package com.secondpasslibrary.reader.design.book

sealed interface BookCardAction {
    val bookId: String

    data class BookDetails(override val bookId: String) : BookCardAction

    data class ReadingSessions(override val bookId: String) : BookCardAction

    data class MakeAvailableOffline(override val bookId: String) : BookCardAction

    data class RemoveDownload(override val bookId: String) : BookCardAction

    data class Author(override val bookId: String, val authorId: String, val authorName: String) :
        BookCardAction

    data class Series(override val bookId: String, val seriesId: String, val seriesName: String) :
        BookCardAction
}

internal val BookCardAction.menuLabel: String
    get() = when (this) {
        is BookCardAction.BookDetails -> "Book details"
        is BookCardAction.ReadingSessions -> "Reading Sessions"
        is BookCardAction.MakeAvailableOffline -> "Make available offline"
        is BookCardAction.RemoveDownload -> "Remove download"
        is BookCardAction.Author -> "Author: $authorName"
        is BookCardAction.Series -> "Series: $seriesName"
    }
