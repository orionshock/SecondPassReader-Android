package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsState
import com.secondpasslibrary.reader.library.axis.LibrarySeriesState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import com.secondpasslibrary.reader.library.chrome.LibraryFilterVocabularyState
import com.secondpasslibrary.reader.library.chrome.LibraryTagSelectorState
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
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCombinedStateTest {
    @Test
    fun `result surface owns the axis instead of storing a constructible parallel pair`() {
        val surfaces =
            listOf(
                LibraryResultState.Books(),
                LibraryResultState.AuthorIndex(),
                LibraryResultState.AuthorBooks(
                    com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState("author"),
                    books = LibraryBooksState()
                ),
                LibraryResultState.SeriesIndex(),
                LibraryResultState.SeriesBooks(
                    com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState("series"),
                    books = LibraryBooksState()
                )
            )

        assertEquals(
            listOf(
                LibraryAxis.BOOKS,
                LibraryAxis.AUTHORS,
                LibraryAxis.AUTHORS,
                LibraryAxis.SERIES,
                LibraryAxis.SERIES
            ),
            surfaces.map { it.axis }
        )
        assertFalse(
            LibraryState::class.java.declaredFields.any {
                it.name == "axis" || it.name == "resultKind"
            }
        )
        assertFalse(
            surfaces.flatMap { it::class.java.declaredConstructors.toList() }
                .flatMap { it.parameterTypes.toList() }
                .contains(LibraryAxis::class.java)
        )
    }

    @Test
    fun `scope tags are the fallback before the first contextual response`() = runTest {
        val scopeTag = tag("scope", 40)
        val sources = LibraryStateSources(
            vocabulary = vocabulary(scopeTag),
            books = LibraryBooksState(initialLoading = true)
        )

        val state = sources.aggregate(backgroundScope)

        assertEquals(listOf(scopeTag), state.value.tagSelector.tags)
    }

    @Test
    fun `Books contextual tags replace scope totals without deriving from page results`() =
        runTest {
            val contextual = tag("fiction", 84)
            val sources = LibraryStateSources(
                vocabulary = vocabulary(tag("scope", 400)),
                books = LibraryBooksState(
                    contextualCatalogTags = listOf(contextual),
                    hasContextualCatalogTagsResponse = true,
                    currentPage = 1
                )
            )

            val state = sources.aggregate(backgroundScope)

            assertEquals(
                listOf("fiction" to 84),
                state.value.tagSelector.tags.map {
                    it.slug to it.bookCount
                }
            )
        }

    @Test
    fun `author and series axes use their Book-derived contextual tags`() = runTest {
        val sources = LibraryStateSources(
            vocabulary = vocabulary(tag("scope", 400)),
            authors = PagedLibraryAxisState(
                ordering = AuthorOrdering.NAME,
                contextualCatalogTags = listOf(tag("author-context", 12)),
                hasContextualCatalogTagsResponse = true
            ),
            series = PagedLibraryAxisState(
                ordering = SeriesOrdering.NAME,
                contextualCatalogTags = listOf(tag("series-context", 7)),
                hasContextualCatalogTagsResponse = true
            )
        )
        val state = sources.aggregate(backgroundScope)
        runCurrent()

        sources.chrome.value = LibraryChromeState(
            result = LibraryResultState.AuthorIndex()
        )
        runCurrent()
        assertEquals(listOf("author-context"), state.value.tagSelector.tags.map { it.slug })

        sources.chrome.value = LibraryChromeState(
            result = LibraryResultState.SeriesIndex()
        )
        runCurrent()
        assertEquals(listOf("series-context"), state.value.tagSelector.tags.map { it.slug })
    }

    @Test
    fun `authoritative empty contextual tags do not fall back to scope totals`() = runTest {
        val sources = LibraryStateSources(
            vocabulary = vocabulary(tag("stale-scope", 99)),
            books = LibraryBooksState(
                contextualCatalogTags = emptyList(),
                hasContextualCatalogTagsResponse = true,
                currentPage = 1
            )
        )

        val state = sources.aggregate(backgroundScope)

        assertEquals(emptyList<LibraryCatalogTag>(), state.value.tagSelector.tags)
        assertEquals(true, state.value.tagSelector.loaded)
    }

    @Test
    fun `context switch clears old contextual counts until its response arrives`() = runTest {
        val sources = LibraryStateSources(
            vocabulary = vocabulary(tag("scope-fallback", 100)),
            books = LibraryBooksState(
                contextualCatalogTags = listOf(tag("book-context", 20)),
                hasContextualCatalogTagsResponse = true
            )
        )
        val state = sources.aggregate(backgroundScope)
        runCurrent()
        assertEquals(listOf("book-context"), state.value.tagSelector.tags.map { it.slug })

        sources.chrome.value = LibraryChromeState(
            result = LibraryResultState.AuthorIndex()
        )
        sources.authors.value = PagedLibraryAxisState(
            ordering = AuthorOrdering.NAME,
            initialLoading = true
        )
        runCurrent()

        assertEquals(listOf("scope-fallback"), state.value.tagSelector.tags.map { it.slug })
        assertEquals(false, state.value.tagSelector.tags.any { it.slug == "book-context" })
    }

    @Test
    fun `initial value uses current source snapshots before collection starts`() = runTest {
        val sources = LibraryStateSources(
            chrome = LibraryChromeState(
                result = LibraryResultState.AuthorIndex(),
                scope = LibraryScope.Group("group-1")
            ),
            authors = PagedLibraryAxisState(ordering = AuthorOrdering.NAME, totalCount = 12)
        )

        val state = sources.aggregate(backgroundScope)

        assertEquals(LibraryAxis.AUTHORS, state.value.axis)
        assertEquals(LibraryScope.Group("group-1"), state.value.scope)
        assertEquals(12, (state.value.result as LibraryResultState.AuthorIndex).state.totalCount)
    }

    @Test
    fun `eager aggregate tracks parent and child sources without an external collector`() =
        runTest {
            val sources = LibraryStateSources()
            val state = sources.aggregate(backgroundScope)
            runCurrent()

            sources.chrome.value = LibraryChromeState(
                result = LibraryResultState.SeriesIndex()
            )
            sources.series.value = PagedLibraryAxisState(
                ordering = SeriesOrdering.NAME,
                totalCount = 7
            )
            runCurrent()

            assertEquals(LibraryAxis.SERIES, state.value.axis)
            assertEquals(7, (state.value.result as LibraryResultState.SeriesIndex).state.totalCount)
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

private fun tag(slug: String, count: Int) = LibraryCatalogTag("tag-$slug", slug, slug, count)

private fun vocabulary(vararg tags: LibraryCatalogTag) = LibraryFilterVocabularyState(
    tagSelector = LibraryTagSelectorState(loaded = true, tags = tags.toList())
)

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
