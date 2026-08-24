package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
import com.secondpasslibrary.reader.FakeAuthenticatedMarginaliaClient
import com.secondpasslibrary.reader.FakeAuthenticatedShelvesClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.library.DEFAULT_LIBRARY_PAGE_SIZE
import com.secondpasslibrary.reader.library.LibraryController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

internal fun TestScope.libraryBooksController(client: FakeLibraryClient) =
    LibraryBooksController(FakeClientProvider(client), FakeDisplayPreferenceStore(), this)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.libraryController(client: FakeLibraryClient) = LibraryController(
    FakeClientProvider(client),
    FakeDisplayPreferenceStore(),
    CoroutineScope(
        backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
    )
)

internal fun LibraryBooksController.initializeBrowse(profile: ConnectionProfile) =
    initialize(profile, LibraryBooksMode.BROWSE, "", LibraryScope.Global)

internal fun LibraryBooksController.initializeBroadSearch(
    profile: ConnectionProfile,
    query: String
) = initialize(profile, LibraryBooksMode.BROAD_SEARCH, query, LibraryScope.Global)

internal class FakeDisplayPreferenceStore(
    var layout: LibraryBooksLayout = LibraryBooksLayout.GRID
) : LibraryDisplayPreferenceStore {
    override suspend fun read() = layout

    override suspend fun write(layout: LibraryBooksLayout) {
        this.layout = layout
    }
}

internal class FakeClientProvider(private val client: AuthenticatedSecondPassClient) :
    AuthenticatedClientProvider {
    override suspend fun forProfile(profile: ConnectionProfile) = client
}

internal class FakeLibraryClient :
    AuthenticatedSecondPassClient,
    AuthenticatedLibraryBooksClient,
    AuthenticatedLibraryGroupsClient {
    override val library = FakeAuthenticatedLibraryClient(books = this, groups = this)
    override val shelves = FakeAuthenticatedShelvesClient
    override val marginalia = FakeAuthenticatedMarginaliaClient

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
    var groupBookCall: suspend (String, BookListOptions) -> LibraryPage<CompactBook> =
        { _, options -> page(options.page, emptyList(), 0) }

    override suspend fun getBook(bookId: String): com.secondpasslibrary.client.LibraryBookDetail =
        error("Book detail is outside this fixture.")

    override suspend fun downloadBook(
        reference: com.secondpasslibrary.client.AuthenticatedBookDownloadReference,
        destination: java.io.OutputStream
    ) = error("Book download is outside this fixture.")

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
}

internal fun group(id: String, public: Boolean) = LibraryGroupSummary(id, id, public)

internal fun groupPage(number: Int, groups: List<LibraryGroupSummary>, hasNext: Boolean = false) =
    LibraryPage(
        totalCount = groups.size,
        results = groups,
        hasNext = hasNext,
        hasPrevious = number > 1,
        page = number,
        pageSize = 200
    )

internal fun page(number: Int, ids: List<String>, total: Int, hasNext: Boolean = false) =
    LibraryPage(
        totalCount = total,
        results = ids.map(::book),
        hasNext = hasNext,
        hasPrevious = number > 1,
        page = number,
        pageSize = DEFAULT_LIBRARY_PAGE_SIZE
    )

internal fun book(id: String) = CompactBook(
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

internal fun profile() = ConnectionProfile(
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
