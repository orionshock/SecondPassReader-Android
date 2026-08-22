package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsState
import com.secondpasslibrary.reader.library.axis.LibrarySeriesState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import com.secondpasslibrary.reader.library.chrome.LibraryFilterVocabularyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCombinedStateTest {
    @Test
    fun `initial value uses current source snapshots before collection starts`() = runTest {
        val sources = LibraryStateSources(
            chrome = LibraryChromeState(
                axis = LibraryAxis.AUTHORS,
                resultKind = LibraryResultKind.AUTHOR_INDEX,
                scope = LibraryScope.Group("group-1")
            ),
            books = LibraryBooksState(totalCount = 12)
        )

        val state = sources.aggregate(backgroundScope)

        assertEquals(LibraryAxis.AUTHORS, state.value.axis)
        assertEquals(LibraryScope.Group("group-1"), state.value.scope)
        assertEquals(12, state.value.books.totalCount)
    }

    @Test
    fun `eager aggregate tracks parent and child sources without an external collector`() =
        runTest {
            val sources = LibraryStateSources()
            val state = sources.aggregate(backgroundScope)
            runCurrent()

            sources.chrome.value = LibraryChromeState(
                axis = LibraryAxis.SERIES,
                resultKind = LibraryResultKind.SERIES_INDEX
            )
            sources.books.value = LibraryBooksState(totalCount = 7)
            runCurrent()

            assertEquals(LibraryAxis.SERIES, state.value.axis)
            assertEquals(7, state.value.books.totalCount)
            assertEquals(state.value, async { state.first() }.await())
        }

    @Test
    fun `aggregate stops changing when its owning scope is cancelled`() = runTest {
        val owner = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val sources = LibraryStateSources()
        val state = sources.aggregate(owner)
        runCurrent()
        val lastOwnedState = state.value

        owner.cancel()
        sources.books.value = LibraryBooksState(totalCount = 99)
        runCurrent()

        assertEquals(lastOwnedState, state.value)
    }
}

private class LibraryStateSources(
    chrome: LibraryChromeState = LibraryChromeState(),
    vocabulary: LibraryFilterVocabularyState = LibraryFilterVocabularyState(),
    books: LibraryBooksState = LibraryBooksState(),
    authors: LibraryAuthorsState = PagedLibraryAxisState(ordering = AuthorOrdering.NAME),
    series: LibrarySeriesState = PagedLibraryAxisState(ordering = SeriesOrdering.NAME)
) {
    val chrome = MutableStateFlow(chrome)
    val vocabulary = MutableStateFlow(vocabulary)
    val books = MutableStateFlow(books)
    val authors = MutableStateFlow(authors)
    val series = MutableStateFlow(series)

    fun aggregate(scope: CoroutineScope) = libraryStateFlow(
        scope,
        chrome,
        vocabulary,
        books,
        authors,
        series
    )
}
