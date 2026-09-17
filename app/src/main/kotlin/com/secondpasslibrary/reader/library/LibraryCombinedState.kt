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
    get() = result is LibraryResultState.AuthorBooks || result is LibraryResultState.SeriesBooks

internal data class LibraryChromeState(
    val result: LibraryResultState = LibraryResultState.Books(),
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
        result.withLatestState(books, authors, series),
        scope,
        advancedGroupsEnabled,
        vocabulary.groupSelector,
        selectedTag,
        activeTagSelector(vocabulary.tagSelector, books, authors, series)
    )

    private fun activeTagSelector(
        scopeTags: LibraryTagSelectorState,
        books: LibraryBooksState,
        authors: LibraryAuthorsState,
        series: LibrarySeriesState
    ): LibraryTagSelectorState {
        val contextual = when (result) {
            is LibraryResultState.Books,
            is LibraryResultState.AuthorBooks,
            is LibraryResultState.SeriesBooks ->
                books.contextualCatalogTags to books.hasContextualCatalogTagsResponse

            is LibraryResultState.AuthorIndex ->
                authors.contextualCatalogTags to authors.hasContextualCatalogTagsResponse

            is LibraryResultState.SeriesIndex ->
                series.contextualCatalogTags to series.hasContextualCatalogTagsResponse
        }
        return if (contextual.second) {
            LibraryTagSelectorState(loaded = true, tags = contextual.first)
        } else {
            scopeTags
        }
    }
}

private fun LibraryResultState.withLatestState(
    books: LibraryBooksState,
    authors: LibraryAuthorsState,
    series: LibrarySeriesState
): LibraryResultState = when (this) {
    is LibraryResultState.Books -> copy(state = books)

    is LibraryResultState.AuthorIndex -> copy(state = authors)

    is LibraryResultState.AuthorBooks ->
        copy(
            author = authors.selected?.takeIf { it.id == author.id } ?: author,
            indexEntry = authors.items.firstOrNull { it.id == author.id },
            books = books
        )

    is LibraryResultState.SeriesIndex -> copy(state = series)

    is LibraryResultState.SeriesBooks ->
        copy(
            series = series.selected?.takeIf { it.id == this.series.id } ?: this.series,
            indexEntry = series.items.firstOrNull { it.id == this.series.id },
            books = books
        )
}

internal fun LibraryAxis.indexResultState(
    books: LibraryBooksState,
    authors: LibraryAuthorsState,
    series: LibrarySeriesState
): LibraryResultState = when (this) {
    LibraryAxis.BOOKS -> LibraryResultState.Books(books)
    LibraryAxis.AUTHORS -> LibraryResultState.AuthorIndex(authors)
    LibraryAxis.SERIES -> LibraryResultState.SeriesIndex(series)
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
