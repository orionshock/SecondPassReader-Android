package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibrarySearchOrdering

internal data class LibraryOrderingOption(val ordering: LibraryBooksOrdering, val label: String)

internal fun libraryOrderingOptions(
    mode: LibraryBooksMode,
    filter: LibraryBooksFilter? = null
): List<LibraryOrderingOption> = when (mode) {
    LibraryBooksMode.BROWSE -> when (filter) {
        is LibraryBooksFilter.Author -> authorBooksOrderingOptions

        is LibraryBooksFilter.Series -> seriesBooksOrderingOptions

        null -> listOf(
            browse(BookOrdering.TITLE, "Title A–Z"),
            browse(BookOrdering.TITLE_DESCENDING, "Title Z–A"),
            browse(BookOrdering.AUTHOR, "Author A–Z"),
            browse(BookOrdering.AUTHOR_DESCENDING, "Author Z–A"),
            browse(BookOrdering.SERIES, "Series A–Z"),
            browse(BookOrdering.SERIES_DESCENDING, "Series Z–A"),
            browse(BookOrdering.SERIES_INDEX, "Series order"),
            browse(BookOrdering.SERIES_INDEX_DESCENDING, "Reverse series order"),
            browse(BookOrdering.PUBLISHER, "Publisher A–Z"),
            browse(BookOrdering.PUBLISHER_DESCENDING, "Publisher Z–A")
        )
    }

    LibraryBooksMode.BROAD_SEARCH ->
        listOf(
            broad(LibrarySearchOrdering.TITLE, "Title A–Z"),
            broad(LibrarySearchOrdering.TITLE_DESCENDING, "Title Z–A"),
            broad(LibrarySearchOrdering.AUTHOR, "Author A–Z"),
            broad(LibrarySearchOrdering.AUTHOR_DESCENDING, "Author Z–A"),
            broad(LibrarySearchOrdering.SERIES, "Series A–Z"),
            broad(LibrarySearchOrdering.SERIES_DESCENDING, "Series Z–A")
        )
}

private val authorBooksOrderingOptions =
    listOf(
        browse(BookOrdering.TITLE, "Title A–Z"),
        browse(BookOrdering.TITLE_DESCENDING, "Title Z–A"),
        browse(BookOrdering.SERIES, "Series A–Z"),
        browse(BookOrdering.SERIES_DESCENDING, "Series Z–A"),
        browse(BookOrdering.SERIES_INDEX, "Series order"),
        browse(BookOrdering.SERIES_INDEX_DESCENDING, "Reverse series order"),
        browse(BookOrdering.PUBLISHER, "Publisher A–Z"),
        browse(BookOrdering.PUBLISHER_DESCENDING, "Publisher Z–A")
    )

private val seriesBooksOrderingOptions =
    listOf(
        browse(BookOrdering.SERIES_INDEX, "Series order"),
        browse(BookOrdering.SERIES_INDEX_DESCENDING, "Reverse series order"),
        browse(BookOrdering.TITLE, "Title A–Z"),
        browse(BookOrdering.TITLE_DESCENDING, "Title Z–A"),
        browse(BookOrdering.AUTHOR, "Author A–Z"),
        browse(BookOrdering.AUTHOR_DESCENDING, "Author Z–A")
    )

internal fun LibraryBooksOrdering.label(): String = libraryOrderingOptions(
    if (this is LibraryBooksOrdering.Browse) {
        LibraryBooksMode.BROWSE
    } else {
        LibraryBooksMode.BROAD_SEARCH
    }
).first { it.ordering == this }.label

private fun browse(ordering: BookOrdering, label: String) =
    LibraryOrderingOption(LibraryBooksOrdering.Browse(ordering), label)

private fun broad(ordering: LibrarySearchOrdering, label: String) =
    LibraryOrderingOption(LibraryBooksOrdering.BroadSearch(ordering), label)
