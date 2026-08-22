package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsState
import com.secondpasslibrary.reader.library.axis.LibrarySeriesState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import com.secondpasslibrary.reader.library.chrome.LibraryFilterVocabularyState
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal val LibraryChromeState.isSelectedAuthorSeriesBooks: Boolean
    get() = resultKind == LibraryResultKind.BOOKS && axis != LibraryAxis.BOOKS

internal data class LibraryChromeState(
    val axis: LibraryAxis = LibraryAxis.BOOKS,
    val resultKind: LibraryResultKind = LibraryResultKind.BOOKS,
    val scope: LibraryScope = LibraryScope.Global,
    val advancedGroupsEnabled: Boolean = false,
    val selectedTag: LibraryCatalogTag? = null
) {
    fun toState(
        vocabulary: LibraryFilterVocabularyState,
        books: LibraryBooksState,
        authors: LibraryAuthorsState,
        series: LibrarySeriesState
    ) = LibraryState(
        axis,
        resultKind,
        scope,
        advancedGroupsEnabled,
        vocabulary.groupSelector,
        selectedTag,
        vocabulary.tagSelector,
        books,
        authors,
        series
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
    private val vocabulary: StateFlow<LibraryFilterVocabularyState>,
    private val books: StateFlow<LibraryBooksState>,
    private val authors: StateFlow<LibraryAuthorsState>,
    private val series: StateFlow<LibrarySeriesState>
) : StateFlow<LibraryState> {
    override val value: LibraryState
        get() = chrome.value.toState(vocabulary.value, books.value, authors.value, series.value)

    override val replayCache: List<LibraryState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<LibraryState>): Nothing {
        combine(chrome, vocabulary, books, authors, series) {
                parent,
                vocabularyState,
                booksState,
                authorsState,
                seriesState
            ->
            parent.toState(vocabularyState, booksState, authorsState, seriesState)
        }.collect(collector)
        error("Library state sources completed unexpectedly.")
    }
}
