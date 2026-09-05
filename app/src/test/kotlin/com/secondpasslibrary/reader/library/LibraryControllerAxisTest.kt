package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.library.axis.FakeLibraryAxisClient
import com.secondpasslibrary.reader.library.axis.FakeLibraryAxisClientProvider
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsController
import com.secondpasslibrary.reader.library.axis.LibrarySeriesController
import com.secondpasslibrary.reader.library.axis.author
import com.secondpasslibrary.reader.library.axis.axisBook
import com.secondpasslibrary.reader.library.axis.axisPage
import com.secondpasslibrary.reader.library.axis.catalogTag
import com.secondpasslibrary.reader.library.axis.libraryPage
import com.secondpasslibrary.reader.library.axis.libraryProfile
import com.secondpasslibrary.reader.library.axis.series
import com.secondpasslibrary.reader.library.books.LibraryBooksController
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry
import com.secondpasslibrary.reader.library.books.LibraryBooksLayout
import com.secondpasslibrary.reader.library.books.LibraryBooksOrdering
import com.secondpasslibrary.reader.library.books.LibraryDisplayPreferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryControllerAxisTest {
    @Test
    fun `connection identity change resets aggregate parent and child state`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            authorList = { options -> axisPage(options.page, listOf(author("author-1"))) }
        }
        val controller = controller(client)
        val profile = libraryProfile()
        controller.initialize(profile, LibraryBooksEntry.Browse, false)
        advanceUntilIdle()
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()
        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(listOf("author-1"), controller.state.value.authors.items.map { it.id })

        controller.initialize(
            profile.copy(clientSessionId = "replacement-session"),
            LibraryBooksEntry.Browse,
            false
        )
        advanceUntilIdle()

        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        assertTrue(controller.state.value.authors.items.isEmpty())
    }

    @Test
    fun `parent delegates Books ordering and refresh through aggregate state`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            bookList = { axisPage(it.page, listOf(axisBook("book"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.changeBrowseOrdering(BookOrdering.AUTHOR)
        advanceUntilIdle()
        val requestsBeforeRefresh = client.bookRequests.size
        controller.refreshBooks()
        advanceUntilIdle()

        assertEquals(BookOrdering.AUTHOR, client.bookRequests.last().ordering)
        assertEquals(requestsBeforeRefresh + 1, client.bookRequests.size)
        assertEquals(listOf("book"), controller.state.value.books.books.map { it.id })

        controller.initialize(
            libraryProfile(),
            LibraryBooksEntry.BroadSearch("history"),
            false
        )
        advanceUntilIdle()
        controller.changeBroadSearchOrdering(LibrarySearchOrdering.AUTHOR_DESCENDING)
        advanceUntilIdle()

        assertEquals(
            LibrarySearchOrdering.AUTHOR_DESCENDING,
            client.searchRequests.last().second.ordering
        )
        assertEquals(
            LibrarySearchOrdering.AUTHOR_DESCENDING,
            (controller.state.value.books.ordering as LibraryBooksOrdering.BroadSearch).value
        )
    }

    @Test
    fun `parent delegates Author and Series ordering without changing active axis`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectAxis(LibraryAxis.AUTHORS)
        controller.changeAuthorOrdering(AuthorOrdering.BOOK_COUNT_DESCENDING)
        advanceUntilIdle()

        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(AuthorOrdering.BOOK_COUNT_DESCENDING, client.authorRequests.last().ordering)
        assertEquals(AuthorOrdering.BOOK_COUNT_DESCENDING, controller.state.value.authors.ordering)

        controller.selectAxis(LibraryAxis.SERIES)
        controller.changeSeriesOrdering(SeriesOrdering.BOOK_COUNT_DESCENDING)
        advanceUntilIdle()

        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals(SeriesOrdering.BOOK_COUNT_DESCENDING, client.seriesRequests.last().ordering)
        assertEquals(SeriesOrdering.BOOK_COUNT_DESCENDING, controller.state.value.series.ordering)
    }

    @Test
    fun `parent delegates Books layout persistence`() = runTest {
        val client = FakeLibraryAxisClient()
        val preferences = TrackingDisplayPreferenceStore()
        val controller = controller(client, preferences)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.setBookLayout(LibraryBooksLayout.LIST)
        advanceUntilIdle()

        assertEquals(LibraryBooksLayout.LIST, controller.state.value.books.layout)
        assertEquals(listOf(LibraryBooksLayout.LIST), preferences.writes)
    }

    @Test
    fun `parent delegates Author and Series detail retry`() = runTest {
        var authorFails = true
        var seriesFails = true
        val client = FakeLibraryAxisClient().apply {
            authorDetail = {
                if (authorFails) throw SplClientException.ProtocolInvalid("author")
                author(it)
            }
            seriesDetail = {
                if (seriesFails) throw SplClientException.ProtocolInvalid("series")
                series(it)
            }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectAuthor("author-1")
        advanceUntilIdle()
        assertEquals(
            LibraryFailure.PROTOCOL_INVALID,
            controller.state.value.authors.selected?.failure
        )
        authorFails = false
        controller.retryAuthorDetail()
        advanceUntilIdle()
        assertEquals("author-1", controller.state.value.authors.selected?.detail?.id)

        controller.selectSeries("series-1")
        advanceUntilIdle()
        assertEquals(
            LibraryFailure.PROTOCOL_INVALID,
            controller.state.value.series.selected?.failure
        )
        seriesFails = false
        controller.retrySeriesDetail()
        advanceUntilIdle()
        assertEquals("series-1", controller.state.value.series.selected?.detail?.id)
    }

    @Test
    fun `pending tag navigation resolves only after authoritative vocabulary loads`() = runTest {
        val requested = catalogTag("tag-1", "fiction")
        val releaseTags = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            tags = { _, options ->
                releaseTags.await()
                libraryPage(options.page, listOf(requested))
            }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        runCurrent()

        controller.navigateTo(LibraryExternalNavigation.Tag(requested.id, requested.slug))
        assertEquals(null, controller.state.value.selectedTag)

        releaseTags.complete(Unit)
        advanceUntilIdle()

        assertEquals(requested, controller.state.value.selectedTag)
        assertEquals("fiction", client.bookRequests.last().tagSlug)
    }

    @Test
    fun `external Book Detail metadata intent enters coordinated Library context`() = runTest {
        val tag = catalogTag("tag-1", "fiction")
        val client = FakeLibraryAxisClient().apply {
            tags = { _, options -> libraryPage(options.page, listOf(tag)) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.navigateTo(LibraryExternalNavigation.Author("author-1"))
        advanceUntilIdle()
        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals("author-1", client.bookRequests.last().authorId)

        controller.navigateTo(LibraryExternalNavigation.Series("series-1"))
        advanceUntilIdle()
        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals("series-1", client.bookRequests.last().seriesId)

        controller.navigateTo(LibraryExternalNavigation.Tag(tag.id, tag.slug))
        advanceUntilIdle()
        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        assertEquals("fiction", client.bookRequests.last().tagSlug)
    }

    @Test
    fun `axis switching activates children and preserves valid child state`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            authorList = { axisPage(it.page, listOf(author("author"))) }
            seriesList = { axisPage(it.page, listOf(series("series"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()
        controller.selectAxis(LibraryAxis.SERIES)
        advanceUntilIdle()
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()

        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(listOf("author"), controller.state.value.authors.items.map { it.id })
        assertEquals(listOf("series"), controller.state.value.series.items.map { it.id })
        assertEquals(1, client.authorRequests.size)
        assertEquals(1, client.seriesRequests.size)
    }

    @Test
    fun `scope change propagates to active child and preserves axis`() = runTest {
        val group = LibraryGroupSummary("group-1", "Group", false)
        val client = FakeLibraryAxisClient().apply {
            groups = { libraryPage(it.page, listOf(group)) }
            groupAuthorList = { _, options -> axisPage(options.page, listOf(author("scoped"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, true)
        advanceUntilIdle()
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()

        controller.selectScope(LibraryScope.Group(group.id))
        advanceUntilIdle()

        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(LibraryScope.Group(group.id), controller.state.value.scope)
        assertEquals(listOf("scoped"), controller.state.value.authors.items.map { it.id })
        assertEquals(group.id, client.groupAuthorRequests.single().first)
        assertTrue(client.groupSeriesRequests.isEmpty())
    }

    @Test
    fun `parent routes Author and Series selections without sibling dependencies`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectAuthor("author-1")
        advanceUntilIdle()
        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(LibraryResultKind.BOOKS, controller.state.value.resultKind)
        assertTrue(controller.state.value.resultKind.supportsBookLayout)
        assertEquals("author-1", controller.state.value.authors.selected?.detail?.id)
        assertEquals("author-1", client.bookRequests.last().authorId)
        assertEquals(BookOrdering.TITLE, client.bookRequests.last().ordering)

        controller.selectSeries("series-1")
        advanceUntilIdle()
        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals("series-1", controller.state.value.series.selected?.detail?.id)
        assertEquals("series-1", client.bookRequests.last().seriesId)
        assertEquals(BookOrdering.SERIES_INDEX, client.bookRequests.last().ordering)

        val childTypes =
            listOf(LibraryAuthorsController::class.java, LibrarySeriesController::class.java)
        assertFalse(
            childTypes.flatMap { it.declaredConstructors.toList() }
                .flatMap { it.parameterTypes.toList() }
                .any { it == LibraryBooksController::class.java }
        )
    }

    @Test
    fun `clearing selected Author restores preserved Author index`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            authorList = { axisPage(it.page, listOf(author("author-1"))) }
            bookList = { axisPage(it.page, listOf(axisBook("filtered"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()

        controller.selectAuthor("author-1")
        advanceUntilIdle()
        controller.clearSelectedAuthorSeries()

        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(LibraryResultKind.AUTHOR_INDEX, controller.state.value.resultKind)
        assertEquals(listOf("author-1"), controller.state.value.authors.items.map { it.id })
        assertEquals(null, controller.state.value.authors.selected)
    }

    @Test
    fun `selecting the active entity axis returns to its preserved index`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            authorList = { axisPage(it.page, listOf(author("author-1"))) }
            bookList = { axisPage(it.page, listOf(axisBook("filtered"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()
        controller.selectAuthor("author-1")
        advanceUntilIdle()

        controller.selectAxis(LibraryAxis.AUTHORS)

        assertEquals(LibraryResultKind.AUTHOR_INDEX, controller.state.value.resultKind)
        assertEquals(null, controller.state.value.authors.selected)
        assertEquals(listOf("author-1"), controller.state.value.authors.items.map { it.id })
    }

    @Test
    fun `selected entity search remains inside filtered Books`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectSeries("series-1")
        advanceUntilIdle()
        controller.commitSearch("foundation")
        advanceUntilIdle()

        val request = client.bookRequests.last()
        assertEquals("series-1", request.seriesId)
        assertEquals("foundation", request.q)
        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals(LibraryResultKind.BOOKS, controller.state.value.resultKind)
    }

    @Test
    fun `group selected entity Books use scoped filters and scope change clears context`() =
        runTest {
            val groupOne = LibraryGroupSummary("group-1", "One", false)
            val groupTwo = LibraryGroupSummary("group-2", "Two", false)
            val client = FakeLibraryAxisClient().apply {
                groups = { libraryPage(it.page, listOf(groupOne, groupTwo)) }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, true)
            advanceUntilIdle()
            controller.selectAxis(LibraryAxis.AUTHORS)
            controller.selectScope(LibraryScope.Group(groupOne.id))
            advanceUntilIdle()

            controller.selectAuthor("author-1")
            advanceUntilIdle()

            assertEquals(groupOne.id, client.groupBookRequests.last().first)
            assertEquals("author-1", client.groupBookRequests.last().second.authorId)

            controller.selectScope(LibraryScope.Group(groupTwo.id))
            advanceUntilIdle()

            assertEquals(LibraryResultKind.AUTHOR_INDEX, controller.state.value.resultKind)
            assertEquals(null, controller.state.value.authors.selected)
            assertEquals(LibraryScope.Group(groupTwo.id), controller.state.value.scope)
        }

    @Test
    fun `detail failure does not erase filtered Books and paging stays with Books child`() =
        runTest {
            val client = FakeLibraryAxisClient().apply {
                authorDetail = { throw SplClientException.ProtocolInvalid("author") }
                bookList = { options ->
                    if (options.page == 1) {
                        axisPage(1, listOf(axisBook("one")), total = 2, hasNext = true)
                    } else {
                        axisPage(2, listOf(axisBook("two")), total = 2)
                    }
                }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
            advanceUntilIdle()

            controller.selectAuthor("author-1")
            advanceUntilIdle()

            assertEquals(
                LibraryFailure.PROTOCOL_INVALID,
                controller.state.value.authors.selected?.failure
            )
            assertEquals(listOf("one"), controller.state.value.books.books.map { it.id })

            controller.loadNextPage()
            advanceUntilIdle()

            assertEquals(listOf("one", "two"), controller.state.value.books.books.map { it.id })
            assertEquals(listOf(1, 2), client.bookRequests.takeLast(2).map { it.page })
        }

    @Test
    fun `tag vocabulary is complete and selected tag filters every axis`() = runTest {
        val selected = catalogTag("fiction", "fiction")
        val client = FakeLibraryAxisClient().apply {
            bookList = { options ->
                axisPage(options.page, emptyList(), hasNext = options.page == 1)
            }
            tags = { _, options ->
                if (options.page == 1) {
                    LibraryPage(2, listOf(selected), true, false, 1, DEFAULT_LIBRARY_PAGE_SIZE)
                } else {
                    LibraryPage(
                        2,
                        listOf(catalogTag("history")),
                        false,
                        true,
                        2,
                        DEFAULT_LIBRARY_PAGE_SIZE
                    )
                }
            }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()
        controller.commitSearch("dune")
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        controller.selectTag(selected)
        advanceUntilIdle()
        assertEquals("fiction", client.bookRequests.last().tagSlug)
        assertEquals("dune", client.bookRequests.last().q)
        assertEquals(1, client.bookRequests.last().page)
        assertEquals(
            listOf("fiction", "history"),
            controller.state.value.tagSelector.tags.map {
                it.slug
            }
        )
        assertEquals(selected, controller.state.value.selectedTag)

        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()
        assertEquals("fiction", client.authorRequests.last().tagSlug)

        controller.selectAxis(LibraryAxis.SERIES)
        advanceUntilIdle()
        assertEquals("fiction", client.seriesRequests.last().tagSlug)
    }

    @Test
    fun `selected entity Books retain shared tag and selecting it again clears it`() = runTest {
        val selected = catalogTag("fiction", "fiction")
        val client = FakeLibraryAxisClient().apply {
            tags = { _, options -> libraryPage(options.page, listOf(selected)) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()
        controller.selectTag(selected)
        controller.selectAuthor("author-1")
        advanceUntilIdle()

        assertEquals("author-1", client.bookRequests.last().authorId)
        assertEquals("fiction", client.bookRequests.last().tagSlug)

        controller.selectTag(selected)
        advanceUntilIdle()
        assertEquals(null, controller.state.value.selectedTag)
        assertEquals(null, client.bookRequests.last().tagSlug)
        assertEquals("author-1", client.bookRequests.last().authorId)
    }

    @Test
    fun `scope change clears tag selection reloads vocabulary and retains Library results`() =
        runTest {
            val group = LibraryGroupSummary("group-1", "Group", false)
            val globalTag = catalogTag("global")
            val scopedTag = catalogTag("scoped")
            val client = FakeLibraryAxisClient().apply {
                groups = { libraryPage(it.page, listOf(group)) }
                tags = { scope, options ->
                    libraryPage(
                        options.page,
                        listOf(if (scope == LibraryScope.Global) globalTag else scopedTag)
                    )
                }
                bookList = { axisPage(it.page, listOf(axisBook("book"))) }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, true)
            advanceUntilIdle()
            controller.selectTag(globalTag)
            advanceUntilIdle()

            controller.selectScope(LibraryScope.Group(group.id))
            advanceUntilIdle()

            assertEquals(null, controller.state.value.selectedTag)
            assertEquals(listOf("scoped"), controller.state.value.tagSelector.tags.map { it.slug })
            assertEquals(
                listOf(LibraryScope.Global, LibraryScope.Group(group.id)),
                client.tagRequests.map {
                    it.first
                }
            )
            assertEquals(LibraryScope.Group(group.id), controller.state.value.scope)
        }

    @Test
    fun `tag vocabulary failure does not destroy loaded Books`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            bookList = { axisPage(it.page, listOf(axisBook("book"))) }
            tags = { _, _ -> throw SplClientException.ProtocolInvalid("tags") }
        }
        val controller = controller(client)

        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        assertEquals(listOf("book"), controller.state.value.books.books.map { it.id })
        assertEquals(LibraryFailure.PROTOCOL_INVALID, controller.state.value.tagSelector.failure)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        client: FakeLibraryAxisClient,
        preferences: LibraryDisplayPreferenceStore = TrackingDisplayPreferenceStore()
    ) = LibraryController(
        FakeLibraryAxisClientProvider(client),
        preferences,
        CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
        )
    )

    private class TrackingDisplayPreferenceStore : LibraryDisplayPreferenceStore {
        val writes = mutableListOf<LibraryBooksLayout>()

        override suspend fun read() = LibraryBooksLayout.GRID

        override suspend fun write(layout: LibraryBooksLayout) {
            writes += layout
        }
    }
}
