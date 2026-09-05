package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsState
import com.secondpasslibrary.reader.library.axis.LibrarySeriesState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import com.secondpasslibrary.reader.library.chrome.LibraryFilterVocabularyState
import com.secondpasslibrary.reader.library.chrome.LibraryTagSelectorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
        activeTagSelector(vocabulary.tagSelector, books, authors, series),
        books,
        authors,
        series
    )

    private fun activeTagSelector(
        scopeTags: LibraryTagSelectorState,
        books: LibraryBooksState,
        authors: LibraryAuthorsState,
        series: LibrarySeriesState
    ): LibraryTagSelectorState {
        val contextual = when {
            resultKind == LibraryResultKind.BOOKS ->
                books.contextualCatalogTags to books.hasContextualCatalogTagsResponse

            axis == LibraryAxis.AUTHORS ->
                authors.contextualCatalogTags to authors.hasContextualCatalogTagsResponse

            else -> series.contextualCatalogTags to series.hasContextualCatalogTagsResponse
        }
        return if (contextual.second) {
            LibraryTagSelectorState(loaded = true, tags = contextual.first)
        } else {
            scopeTags
        }
    }
}

internal val LibraryAxis.indexResultKind: LibraryResultKind
    get() = when (this) {
        LibraryAxis.BOOKS -> LibraryResultKind.BOOKS
        LibraryAxis.AUTHORS -> LibraryResultKind.AUTHOR_INDEX
        LibraryAxis.SERIES -> LibraryResultKind.SERIES_INDEX
    }

internal fun libraryStateFlow(
    scope: CoroutineScope,
    chrome: StateFlow<LibraryChromeState>,
    vocabulary: StateFlow<LibraryFilterVocabularyState>,
    books: StateFlow<LibraryBooksState>,
    authors: StateFlow<LibraryAuthorsState>,
    series: StateFlow<LibrarySeriesState>
): StateFlow<LibraryState> = combine(
    chrome,
    vocabulary,
    books,
    authors,
    series,
    ::toLibraryState
).stateIn(
    scope,
    // Feature state must remain current before Compose or another external collector arrives.
    SharingStarted.Eagerly,
    toLibraryState(
        chrome.value,
        vocabulary.value,
        books.value,
        authors.value,
        series.value
    )
)

private fun toLibraryState(
    chrome: LibraryChromeState,
    vocabulary: LibraryFilterVocabularyState,
    books: LibraryBooksState,
    authors: LibraryAuthorsState,
    series: LibrarySeriesState
): LibraryState = chrome.toState(vocabulary, books, authors, series)
