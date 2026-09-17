package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsState
import com.secondpasslibrary.reader.library.axis.LibrarySeriesState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import com.secondpasslibrary.reader.library.chrome.LibraryGroupSelectorState
import com.secondpasslibrary.reader.library.chrome.LibraryTagSelectorState

internal enum class LibraryAxis {
    BOOKS,
    AUTHORS,
    SERIES
}

internal enum class LibraryAuthorityMode {
    ONLINE,
    OFFLINE
}

internal sealed interface LibraryResultState {
    val axis: LibraryAxis

    data class Books(val state: LibraryBooksState = LibraryBooksState()) : LibraryResultState {
        override val axis = LibraryAxis.BOOKS
    }

    data class AuthorIndex(
        val state: LibraryAuthorsState = PagedLibraryAxisState(ordering = AuthorOrdering.NAME)
    ) : LibraryResultState {
        override val axis = LibraryAxis.AUTHORS
    }

    data class AuthorBooks(
        val author: PagedLibraryAxisDetailState<LibraryAuthor>,
        val indexEntry: LibraryAuthor? = null,
        val books: LibraryBooksState
    ) : LibraryResultState {
        override val axis = LibraryAxis.AUTHORS
    }

    data class SeriesIndex(
        val state: LibrarySeriesState = PagedLibraryAxisState(ordering = SeriesOrdering.NAME)
    ) : LibraryResultState {
        override val axis = LibraryAxis.SERIES
    }

    data class SeriesBooks(
        val series: PagedLibraryAxisDetailState<LibrarySeries>,
        val indexEntry: LibrarySeries? = null,
        val books: LibraryBooksState
    ) : LibraryResultState {
        override val axis = LibraryAxis.SERIES
    }
}

internal val LibraryResultState.isBookResults: Boolean
    get() = when (this) {
        is LibraryResultState.Books,
        is LibraryResultState.AuthorBooks,
        is LibraryResultState.SeriesBooks -> true

        is LibraryResultState.AuthorIndex,
        is LibraryResultState.SeriesIndex -> false
    }

internal fun LibraryResultState.booksStateOrNull(): LibraryBooksState? = when (this) {
    is LibraryResultState.Books -> state

    is LibraryResultState.AuthorBooks -> books

    is LibraryResultState.SeriesBooks -> books

    is LibraryResultState.AuthorIndex,
    is LibraryResultState.SeriesIndex -> null
}

internal data class LibraryState(
    val result: LibraryResultState = LibraryResultState.Books(),
    val scope: LibraryScope = LibraryScope.Global,
    val advancedGroupsEnabled: Boolean = false,
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState(),
    val selectedTag: LibraryCatalogTag? = null,
    val tagSelector: LibraryTagSelectorState = LibraryTagSelectorState()
) {
    val axis: LibraryAxis
        get() = result.axis
}

internal enum class LibraryFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    OTHER
}

internal sealed interface LibraryConnectionEvent {
    data object AuthenticationRejected : LibraryConnectionEvent
}

internal const val DEFAULT_LIBRARY_PAGE_SIZE = 50
