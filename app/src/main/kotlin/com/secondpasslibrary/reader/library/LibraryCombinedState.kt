package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal val LibraryChromeState.isSelectedEntityBooks: Boolean
    get() = resultKind == LibraryResultKind.BOOKS && axis != LibraryAxis.BOOKS

internal data class LibraryChromeState(
    val axis: LibraryAxis = LibraryAxis.BOOKS,
    val resultKind: LibraryResultKind = LibraryResultKind.BOOKS,
    val scope: LibraryScope = LibraryScope.Global,
    val advancedGroupsEnabled: Boolean = false,
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState(),
    val selectedTag: LibraryCatalogTag? = null,
    val tagSelector: LibraryTagSelectorState = LibraryTagSelectorState()
) {
    fun toState(
        books: LibraryBooksState,
        authors: LibraryAuthorsState,
        series: LibrarySeriesState,
        bookDetail: LibraryBookDetailState
    ) = LibraryState(
        axis,
        resultKind,
        scope,
        advancedGroupsEnabled,
        groupSelector,
        selectedTag,
        tagSelector,
        books,
        authors,
        series,
        bookDetail
    )
}

internal val LibraryAxis.indexResultKind: LibraryResultKind
    get() = when (this) {
        LibraryAxis.BOOKS -> LibraryResultKind.BOOKS
        LibraryAxis.AUTHORS -> LibraryResultKind.AUTHOR_INDEX
        LibraryAxis.SERIES -> LibraryResultKind.SERIES_INDEX
    }

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class LibraryStateFlow(
    private val chrome: StateFlow<LibraryChromeState>,
    private val books: StateFlow<LibraryBooksState>,
    private val authors: StateFlow<LibraryAuthorsState>,
    private val series: StateFlow<LibrarySeriesState>,
    private val bookDetail: StateFlow<LibraryBookDetailState>
) : StateFlow<LibraryState> {
    override val value: LibraryState
        get() = chrome.value.toState(books.value, authors.value, series.value, bookDetail.value)

    override val replayCache: List<LibraryState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<LibraryState>): Nothing {
        combine(chrome, books, authors, series, bookDetail) {
                parent,
                booksState,
                authorsState,
                seriesState,
                detailState
            ->
            parent.toState(booksState, authorsState, seriesState, detailState)
        }.collect(collector)
        error("Library state sources completed unexpectedly.")
    }
}
