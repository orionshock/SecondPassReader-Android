package com.secondpasslibrary.reader.bookdetail

internal sealed interface BookDetailNavigationIntent {
    data class Author(val id: String) : BookDetailNavigationIntent

    data class Series(val id: String) : BookDetailNavigationIntent

    data class Tag(val id: String, val slug: String) : BookDetailNavigationIntent

    data class ReadingSessions(val bookId: String) : BookDetailNavigationIntent

    data class ReadBook(val bookId: String) : BookDetailNavigationIntent

    data object ManageShelves : BookDetailNavigationIntent
}
