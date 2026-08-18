package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBooksControllerTest {
    @Test
    fun `normal Books initial load uses page one and preserves server order`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { page(1, listOf("second", "first"), total = 8, hasNext = true) }
        }
        val controller = controller(client)

        controller.initializeBrowse(profile())
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(LibraryBooksMode.BROWSE, state.mode)
        assertEquals(LibraryBooksOrdering.Browse(BookOrdering.TITLE), state.ordering)
        assertEquals(DEFAULT_LIBRARY_PAGE_SIZE, state.pageSize)
        assertEquals(listOf("second", "first"), state.books.map { it.id })
        assertEquals(8, state.totalCount)
        assertTrue(state.hasNext)
        assertEquals(1, state.currentPage)
        assertFalse(state.initialLoading)
        assertEquals(listOf(1), client.bookRequests.map { it.page })
        assertTrue(client.searchRequests.isEmpty())
    }

    @Test
    fun `broad search route initializes explicit broad mode`() = runTest {
        val client = FakeLibraryClient().apply {
            searchCall = { page(1, listOf("broad-result"), total = 1) }
        }
        val controller = controller(client)

        controller.initializeBroadSearch(profile(), "octavia butler")
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(LibraryBooksMode.BROAD_SEARCH, state.mode)
        assertEquals("octavia butler", state.committedQuery)
        assertEquals(
            LibraryBooksOrdering.BroadSearch(LibrarySearchOrdering.TITLE),
            state.ordering
        )
        assertEquals(listOf("broad-result"), state.books.map { it.id })
        assertEquals("octavia butler", client.searchRequests.single().q)
        assertTrue(client.bookRequests.isEmpty())
    }

    @Test
    fun `page two appends without reordering prior results`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.page == 1) {
                    page(1, listOf("b", "a"), 4, hasNext = true)
                } else {
                    page(2, listOf("d", "c"), 4)
                }
            }
        }
        val controller = controller(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.loadNextPage()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(listOf("b", "a", "d", "c"), state.books.map { it.id })
        assertEquals(2, state.currentPage)
        assertEquals(4, state.totalCount)
        assertFalse(state.hasNext)
        assertEquals(listOf(1, 2), client.bookRequests.map { it.page })
    }

    @Test
    fun `duplicate next page requests are suppressed while one is active`() = runTest {
        val pageTwoGate = CompletableDeferred<Unit>()
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.page == 1) {
                    page(1, listOf("first"), 2, hasNext = true)
                } else {
                    pageTwoGate.await()
                    page(2, listOf("second"), 2)
                }
            }
        }
        val controller = controller(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.loadNextPage()
        controller.loadNextPage()
        runCurrent()

        assertEquals(listOf(1, 2), client.bookRequests.map { it.page })
        assertTrue(controller.state.value.nextPageLoading)
        pageTwoGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `stale response is ignored after committed query changes`() = runTest {
        val staleGate = CompletableDeferred<Unit>()
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                when (request.q) {
                    "old" -> withContext(NonCancellable) {
                        staleGate.await()
                        page(1, listOf("stale"), 1)
                    }

                    "new" -> page(1, listOf("current"), 1)

                    else -> page(1, emptyList(), 0)
                }
            }
        }
        val controller = controller(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.commitBrowseQuery("old")
        runCurrent()
        controller.commitBrowseQuery("new")
        runCurrent()
        staleGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("new", controller.state.value.committedQuery)
        assertEquals(listOf("current"), controller.state.value.books.map { it.id })
    }

    @Test
    fun `next page failure retains accumulated results and can retry`() = runTest {
        var pageTwoAttempts = 0
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.page == 1) {
                    page(1, listOf("first"), 2, hasNext = true)
                } else {
                    pageTwoAttempts += 1
                    if (pageTwoAttempts == 1) throw SplClientException.ServerUnreachable()
                    page(2, listOf("second"), 2)
                }
            }
        }
        val controller = controller(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("first"), controller.state.value.books.map { it.id })
        assertEquals(
            LibraryBooksLoadError(
                LibraryBooksFailure.UNREACHABLE,
                LibraryBooksLoadPhase.NEXT_PAGE
            ),
            controller.state.value.error
        )
        assertTrue(controller.state.value.hasNext)

        controller.retry()
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), controller.state.value.books.map { it.id })
    }

    @Test
    fun `initial failure exposes retry without results`() = runTest {
        var attempts = 0
        val client = FakeLibraryClient().apply {
            listCall = {
                attempts += 1
                if (attempts == 1) throw SplClientException.ProtocolInvalid("book page")
                page(1, listOf("recovered"), 1)
            }
        }
        val controller = controller(client)

        controller.initializeBrowse(profile())
        advanceUntilIdle()

        assertTrue(controller.state.value.books.isEmpty())
        assertEquals(0, controller.state.value.currentPage)
        assertEquals(
            LibraryBooksLoadError(
                LibraryBooksFailure.PROTOCOL_INVALID,
                LibraryBooksLoadPhase.INITIAL
            ),
            controller.state.value.error
        )

        controller.retry()
        advanceUntilIdle()
        assertEquals(listOf("recovered"), controller.state.value.books.map { it.id })
    }

    @Test
    fun `ordering change resets accumulated paging to page one`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.ordering == BookOrdering.AUTHOR) {
                    page(1, listOf("author-ordered"), 1)
                } else if (request.page == 1) {
                    page(1, listOf("first"), 2, hasNext = true)
                } else {
                    page(2, listOf("second"), 2)
                }
            }
        }
        val controller = controller(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        controller.changeBrowseOrdering(BookOrdering.AUTHOR)
        advanceUntilIdle()

        assertEquals(listOf("author-ordered"), controller.state.value.books.map { it.id })
        assertEquals(1, controller.state.value.currentPage)
        assertEquals(
            LibraryBooksOrdering.Browse(BookOrdering.AUTHOR),
            controller.state.value.ordering
        )
        assertEquals(BookOrdering.AUTHOR, client.bookRequests.last().ordering)
    }

    @Test
    fun `committed normal search resets paging and stays on Books endpoint`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.q == "dune") {
                    page(1, listOf("searched"), 1)
                } else {
                    page(1, listOf("browse"), 2, hasNext = true)
                }
            }
        }
        val controller = controller(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.commitBrowseQuery("dune")
        advanceUntilIdle()

        assertEquals(LibraryBooksMode.BROWSE, controller.state.value.mode)
        assertEquals("dune", controller.state.value.committedQuery)
        assertEquals(listOf("searched"), controller.state.value.books.map { it.id })
        assertEquals(1, controller.state.value.currentPage)
        assertEquals("dune", client.bookRequests.last().q)
        assertTrue(client.searchRequests.isEmpty())
    }

    @Test
    fun `refresh failure retains loaded results`() = runTest {
        var refresh = false
        val client = FakeLibraryClient().apply {
            listCall = {
                if (refresh) throw SplClientException.ServerUnreachable()
                page(1, listOf("visible"), 1)
            }
        }
        val controller = controller(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()
        refresh = true

        controller.refresh()
        advanceUntilIdle()

        assertEquals(listOf("visible"), controller.state.value.books.map { it.id })
        assertEquals(
            LibraryBooksLoadPhase.REFRESH,
            controller.state.value.error?.phase
        )
        assertFalse(controller.state.value.refreshing)
    }

    @Test
    fun `authentication rejection remains distinct and reaches connection ownership`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { throw SplClientException.AuthenticationRejected() }
        }
        val controller = controller(client)
        val event = async { controller.connectionEvents.first() }
        runCurrent()

        controller.initializeBrowse(profile())
        advanceUntilIdle()

        assertEquals(LibraryBooksConnectionEvent.AuthenticationRejected, event.await())
        assertEquals(
            LibraryBooksFailure.AUTHENTICATION_REJECTED,
            controller.state.value.error?.failure
        )
    }

    private fun TestScope.controller(client: FakeLibraryClient) =
        LibraryBooksController(FakeClientProvider(client), this)

    private class FakeClientProvider(private val client: AuthenticatedSecondPassClient) :
        AuthenticatedClientProvider {
        override suspend fun forProfile(profile: ConnectionProfile) = client
    }

    private class FakeLibraryClient : AuthenticatedSecondPassClient {
        val bookRequests = mutableListOf<BookListOptions>()
        val searchRequests = mutableListOf<LibrarySearchOptions>()
        var listCall: suspend (BookListOptions) -> LibraryPage<CompactBook> = {
            page(it.page, emptyList(), 0)
        }
        var searchCall: suspend (LibrarySearchOptions) -> LibraryPage<CompactBook> = {
            page(it.page, emptyList(), 0)
        }

        override suspend fun listBooks(options: BookListOptions): LibraryPage<CompactBook> {
            bookRequests += options
            return listCall(options)
        }

        override suspend fun searchLibrary(
            options: LibrarySearchOptions
        ): LibraryPage<CompactBook> {
            searchRequests += options
            return searchCall(options)
        }

        override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> =
            error("Recent reading is outside this Library fixture.")

        override suspend fun listShelves(options: ShelfListOptions): ShelfPage =
            error("Shelves are outside this Library fixture.")
    }

    private companion object {
        fun page(number: Int, ids: List<String>, total: Int, hasNext: Boolean = false) =
            LibraryPage(
                totalCount = total,
                results = ids.map(::book),
                hasNext = hasNext,
                hasPrevious = number > 1,
                page = number,
                pageSize = DEFAULT_LIBRARY_PAGE_SIZE
            )

        fun book(id: String) = CompactBook(
            id = id,
            title = id,
            sortTitle = id,
            subtitle = "",
            authors = emptyList(),
            series = null,
            catalogTags = emptyList(),
            language = null,
            publisher = null,
            publishedYear = null,
            publishedMonth = null,
            publishedDay = null,
            publicationDatePrecision = PublicationDatePrecision.UNSPECIFIED,
            cover = null,
            fileFormat = "epub"
        )

        fun profile() = ConnectionProfile(
            serverOrigin = "https://library.example",
            serverBaseUrl = "https://library.example/",
            apiBaseUrl = "https://library.example/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "",
            clientSessionId = "client-session",
            clientName = "Tablet",
            clientType = "second-pass-android-client"
        )
    }
}
