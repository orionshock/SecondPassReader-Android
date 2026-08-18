package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
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
                LibraryFailure.UNREACHABLE,
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
                LibraryFailure.PROTOCOL_INVALID,
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
        assertEquals(listOf("browse"), controller.state.value.books.map { it.id })
        assertTrue(controller.state.value.initialLoading)
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

        assertEquals(LibraryConnectionEvent.AuthenticationRejected, event.await())
        assertEquals(
            LibraryFailure.AUTHENTICATION_REJECTED,
            controller.state.value.error?.failure
        )
    }

    @Test
    fun `display preference loads and changes independently of server query state`() = runTest {
        val preference = FakeDisplayPreferenceStore(LibraryBooksLayout.LIST)
        val controller =
            LibraryBooksController(
                FakeClientProvider(FakeLibraryClient()),
                preference,
                this
            )

        controller.initializeBrowse(profile())
        advanceUntilIdle()
        assertEquals(LibraryBooksLayout.LIST, controller.state.value.layout)

        controller.setLayout(LibraryBooksLayout.GRID)
        advanceUntilIdle()
        assertEquals(LibraryBooksLayout.GRID, controller.state.value.layout)
        assertEquals(LibraryBooksLayout.GRID, preference.layout)
        assertEquals(BookOrdering.TITLE, preferenceUnrelatedOrdering(controller))
    }

    @Test
    fun `scope capability is hidden when disabled and defaults to All Library when enabled`() =
        runTest {
            val disabledClient = FakeLibraryClient()
            val disabled = libraryController(disabledClient)
            disabled.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = false)
            advanceUntilIdle()
            assertFalse(disabled.state.value.advancedGroupsEnabled)
            assertTrue(disabledClient.groupRequests.isEmpty())

            val enabledClient = FakeLibraryClient().apply {
                groupCall = { options ->
                    if (options.page == 1) {
                        groupPage(1, listOf(group("public", true)), hasNext = true)
                    } else {
                        groupPage(2, listOf(group("private", false)))
                    }
                }
            }
            val enabled = libraryController(enabledClient)
            enabled.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
            advanceUntilIdle()

            assertTrue(enabled.state.value.advancedGroupsEnabled)
            assertEquals(LibraryScope.Global, enabled.state.value.scope)
            assertEquals(
                listOf("public", "private"),
                enabled.state.value.groupSelector.groups.map {
                    it.id
                }
            )
            assertEquals(listOf(1, 2), enabledClient.groupRequests.map { it.page })
        }

    @Test
    fun `group scope resets paging and uses group Books capability`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = { groupPage(1, listOf(group("group-1", false))) }
            groupBookCall = { _, options -> page(options.page, listOf("group-book"), 1) }
        }
        val controller = libraryController(client)
        controller.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
        advanceUntilIdle()
        controller.commitSearch("broad metadata")
        advanceUntilIdle()

        controller.selectScope(LibraryScope.Group("group-1"))
        advanceUntilIdle()

        assertEquals(LibraryScope.Group("group-1"), controller.state.value.scope)
        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        assertEquals(1, controller.state.value.books.currentPage)
        assertEquals(listOf("group-book"), controller.state.value.books.books.map { it.id })
        val request = client.groupBookRequests.single()
        assertEquals("group-1", request.first)
        assertEquals("broad metadata", request.second.q)
    }

    @Test
    fun `broad search keeps shared semantics while scope selects endpoint`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = { groupPage(1, listOf(group("group-1", false))) }
        }
        val controller = libraryController(client)
        controller.initialize(
            profile(),
            LibraryBooksEntry.BroadSearch("dune"),
            advancedGroupsEnabled = true
        )
        advanceUntilIdle()
        controller.selectScope(LibraryScope.Group("group-1"))
        advanceUntilIdle()
        controller.selectScope(LibraryScope.Global)
        advanceUntilIdle()

        assertEquals(LibraryBooksMode.BROAD_SEARCH, controller.state.value.books.mode)
        assertEquals("dune", controller.state.value.books.committedQuery)
        assertEquals(
            listOf(LibraryScope.Global, LibraryScope.Group("group-1"), LibraryScope.Global),
            client.scopedSearchRequests.map { it.first }
        )
        assertTrue(client.groupBookRequests.isEmpty())
    }

    @Test
    fun `Home broad-search route preserves selected group scope`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = { groupPage(1, listOf(group("group-1", false))) }
        }
        val controller = libraryController(client)
        controller.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
        advanceUntilIdle()
        controller.selectScope(LibraryScope.Group("group-1"))
        advanceUntilIdle()
        client.groupBookRequests.clear()

        controller.initialize(
            profile(),
            LibraryBooksEntry.BroadSearch("dune"),
            advancedGroupsEnabled = true
        )
        advanceUntilIdle()

        assertEquals(LibraryScope.Group("group-1"), controller.state.value.scope)
        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        val request = client.scopedSearchRequests.last()
        assertEquals(LibraryScope.Group("group-1"), request.first)
        assertEquals("dune", request.second.q)
        assertEquals(1, client.groupRequests.size)
    }

    @Test
    fun `Authors and Series selection preserves scope without fake data loads`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = {
                groupPage(
                    1,
                    listOf(group("group-1", false), group("group-2", false))
                )
            }
        }
        val controller = libraryController(client)
        controller.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
        advanceUntilIdle()
        val bookCalls = client.bookRequests.size

        controller.selectAxis(LibraryAxis.AUTHORS)
        controller.selectScope(LibraryScope.Group("group-1"))
        controller.selectAxis(LibraryAxis.SERIES)
        controller.selectScope(LibraryScope.Group("group-2"))
        advanceUntilIdle()

        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals(LibraryScope.Group("group-2"), controller.state.value.scope)
        assertEquals(bookCalls, client.bookRequests.size)
        assertTrue(client.groupBookRequests.isEmpty())
        assertEquals(1, controller.state.value.books.currentPage)
    }

    private fun TestScope.controller(client: FakeLibraryClient) =
        LibraryBooksController(FakeClientProvider(client), FakeDisplayPreferenceStore(), this)

    private fun TestScope.libraryController(client: FakeLibraryClient) =
        LibraryController(FakeClientProvider(client), FakeDisplayPreferenceStore(), this)

    private fun LibraryBooksController.initializeBrowse(profile: ConnectionProfile) =
        initialize(profile, LibraryBooksMode.BROWSE, "", LibraryScope.Global)

    private fun LibraryBooksController.initializeBroadSearch(
        profile: ConnectionProfile,
        query: String
    ) = initialize(profile, LibraryBooksMode.BROAD_SEARCH, query, LibraryScope.Global)

    private fun preferenceUnrelatedOrdering(controller: LibraryBooksController) =
        (controller.state.value.ordering as LibraryBooksOrdering.Browse).value

    private class FakeDisplayPreferenceStore(
        var layout: LibraryBooksLayout = LibraryBooksLayout.GRID
    ) : LibraryDisplayPreferenceStore {
        override suspend fun read() = layout

        override suspend fun write(layout: LibraryBooksLayout) {
            this.layout = layout
        }
    }

    private class FakeClientProvider(private val client: AuthenticatedSecondPassClient) :
        AuthenticatedClientProvider {
        override suspend fun forProfile(profile: ConnectionProfile) = client
    }

    private class FakeLibraryClient :
        AuthenticatedSecondPassClient,
        AuthenticatedLibraryBooksClient,
        AuthenticatedLibraryGroupsClient {
        override val library = FakeAuthenticatedLibraryClient(books = this, groups = this)

        val bookRequests = mutableListOf<BookListOptions>()
        val searchRequests = mutableListOf<LibrarySearchOptions>()
        val groupRequests = mutableListOf<LibraryGroupListOptions>()
        val groupBookRequests = mutableListOf<Pair<String, BookListOptions>>()
        val scopedSearchRequests = mutableListOf<Pair<LibraryScope, LibrarySearchOptions>>()
        var listCall: suspend (BookListOptions) -> LibraryPage<CompactBook> = {
            page(it.page, emptyList(), 0)
        }
        var searchCall: suspend (LibrarySearchOptions) -> LibraryPage<CompactBook> = {
            page(it.page, emptyList(), 0)
        }
        var groupCall: suspend (LibraryGroupListOptions) -> LibraryPage<LibraryGroupSummary> = {
            LibraryPage(0, emptyList(), false, false, it.page, it.pageSize)
        }
        var groupBookCall:
            suspend (String, BookListOptions) -> LibraryPage<CompactBook> = { _, options ->
                page(options.page, emptyList(), 0)
            }

        override suspend fun getBook(
            bookId: String
        ): com.secondpasslibrary.client.LibraryBookDetail =
            error("Book detail is outside this fixture.")

        override suspend fun list(
            scope: LibraryScope,
            options: BookListOptions
        ): LibraryPage<CompactBook> = when (scope) {
            LibraryScope.Global -> {
                bookRequests += options
                listCall(options)
            }

            is LibraryScope.Group -> {
                groupBookRequests += scope.id to options
                groupBookCall(scope.id, options)
            }
        }

        override suspend fun search(
            scope: LibraryScope,
            options: LibrarySearchOptions
        ): LibraryPage<CompactBook> {
            searchRequests += options
            scopedSearchRequests += scope to options
            return searchCall(options)
        }

        override suspend fun listGroups(
            options: LibraryGroupListOptions
        ): LibraryPage<LibraryGroupSummary> {
            groupRequests += options
            return groupCall(options)
        }

        override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> =
            error("Recent reading is outside this Library fixture.")

        override suspend fun listShelves(options: ShelfListOptions): ShelfPage =
            error("Shelves are outside this Library fixture.")
    }

    private companion object {
        fun group(id: String, public: Boolean) = LibraryGroupSummary(id, id, public)

        fun groupPage(number: Int, groups: List<LibraryGroupSummary>, hasNext: Boolean = false) =
            LibraryPage(
                totalCount = groups.size,
                results = groups,
                hasNext = hasNext,
                hasPrevious = number > 1,
                page = number,
                pageSize = 200
            )

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
