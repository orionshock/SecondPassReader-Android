package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedLibraryAuthorsClient
import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.AuthenticatedLibraryTagsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.CatalogTagListOptions
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile

internal class FakeLibraryAxisClient :
    AuthenticatedSecondPassClient,
    AuthenticatedLibraryBooksClient,
    AuthenticatedLibraryAuthorsClient,
    AuthenticatedLibrarySeriesClient,
    AuthenticatedLibraryGroupsClient,
    AuthenticatedLibraryTagsClient {
    override val library =
        FakeAuthenticatedLibraryClient(
            books = this,
            authors = this,
            series = this,
            groups = this,
            tags = this
        )
    override val shelves = com.secondpasslibrary.reader.FakeAuthenticatedShelvesClient

    val bookRequests = mutableListOf<BookListOptions>()
    val groupBookRequests = mutableListOf<Pair<String, BookListOptions>>()
    val searchRequests = mutableListOf<Pair<LibraryScope, LibrarySearchOptions>>()

    val authorRequests = mutableListOf<AuthorListOptions>()
    val groupAuthorRequests = mutableListOf<Pair<String, AuthorListOptions>>()
    val authorDetailRequests = mutableListOf<Pair<String, LibraryEntityDetailOptions>>()
    val seriesRequests = mutableListOf<SeriesListOptions>()
    val groupSeriesRequests = mutableListOf<Pair<String, SeriesListOptions>>()
    val seriesDetailRequests = mutableListOf<Pair<String, LibraryEntityDetailOptions>>()
    val groupRequests = mutableListOf<LibraryGroupListOptions>()
    val tagRequests = mutableListOf<Pair<LibraryScope, CatalogTagListOptions>>()

    var authorList: suspend (AuthorListOptions) -> LibraryPage<LibraryAuthor> = {
        axisPage(it.page, emptyList())
    }
    var groupAuthorList: suspend (String, AuthorListOptions) -> LibraryPage<LibraryAuthor> =
        { _, options -> axisPage(options.page, emptyList()) }
    var authorDetail: suspend (String) -> LibraryAuthor = { author(it) }
    var seriesList: suspend (SeriesListOptions) -> LibraryPage<LibrarySeries> = {
        axisPage(it.page, emptyList())
    }
    var groupSeriesList: suspend (String, SeriesListOptions) -> LibraryPage<LibrarySeries> =
        { _, options -> axisPage(options.page, emptyList()) }
    var seriesDetail: suspend (String) -> LibrarySeries = { series(it) }
    var groups: suspend (LibraryGroupListOptions) -> LibraryPage<LibraryGroupSummary> = {
        axisPage(it.page, emptyList())
    }
    var tags: suspend (LibraryScope, CatalogTagListOptions) -> LibraryPage<LibraryCatalogTag> =
        { _, options -> axisPage(options.page, emptyList()) }
    var bookList: suspend (BookListOptions) -> LibraryPage<CompactBook> = {
        axisPage(it.page, emptyList())
    }
    var groupBookList: suspend (String, BookListOptions) -> LibraryPage<CompactBook> =
        { _, options -> axisPage(options.page, emptyList()) }
    var bookDetail: suspend (String) -> LibraryBookDetail = { libraryBookDetail(it) }
    val bookDetailRequests = mutableListOf<String>()

    override suspend fun getBook(bookId: String): LibraryBookDetail {
        bookDetailRequests += bookId
        return bookDetail(bookId)
    }

    override suspend fun list(
        scope: LibraryScope,
        options: BookListOptions
    ): LibraryPage<CompactBook> = when (scope) {
        LibraryScope.Global -> {
            bookRequests += options
            bookList(options)
        }

        is LibraryScope.Group -> {
            groupBookRequests += scope.id to options
            groupBookList(scope.id, options)
        }
    }

    override suspend fun search(
        scope: LibraryScope,
        options: LibrarySearchOptions
    ): LibraryPage<CompactBook> {
        searchRequests += scope to options
        return axisPage(options.page, emptyList())
    }

    override suspend fun list(
        scope: LibraryScope,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> = when (scope) {
        LibraryScope.Global -> {
            authorRequests += options
            authorList(options)
        }

        is LibraryScope.Group -> {
            groupAuthorRequests += scope.id to options
            groupAuthorList(scope.id, options)
        }
    }

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor {
        authorDetailRequests += authorId to options
        return authorDetail(authorId)
    }

    override suspend fun list(
        scope: LibraryScope,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> = when (scope) {
        LibraryScope.Global -> {
            seriesRequests += options
            seriesList(options)
        }

        is LibraryScope.Group -> {
            groupSeriesRequests += scope.id to options
            groupSeriesList(scope.id, options)
        }
    }

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries {
        seriesDetailRequests += seriesId to options
        return seriesDetail(seriesId)
    }

    override suspend fun listGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> {
        groupRequests += options
        return groups(options)
    }

    override suspend fun list(
        scope: LibraryScope,
        options: CatalogTagListOptions
    ): LibraryPage<LibraryCatalogTag> {
        tagRequests += scope to options
        return tags(scope, options)
    }

    override suspend fun get(tagId: String): LibraryCatalogTag =
        error("Catalog tag detail is outside this Library fixture.")

    override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> =
        error("Recent reading is outside this Library fixture.")
}

internal class FakeLibraryAxisClientProvider(private val client: AuthenticatedSecondPassClient) :
    AuthenticatedClientProvider {
    override suspend fun forProfile(profile: ConnectionProfile) = client
}

internal fun author(id: String) = LibraryAuthor(id, id, id, "Biography $id", 3, emptyList())

internal fun series(id: String) = LibrarySeries(id, id, id, "Summary $id", 4, emptyList())

internal fun catalogTag(id: String, slug: String = id) = LibraryCatalogTag(id, id, slug, 5)

internal fun axisBook(id: String) = CompactBook(
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

internal fun libraryBookDetail(id: String) = LibraryBookDetail(
    id = id,
    title = id,
    sortTitle = id,
    subtitle = "",
    authors = emptyList(),
    series = null,
    language = null,
    publisher = null,
    publishedYear = null,
    publishedMonth = null,
    publishedDay = null,
    publicationDatePrecision = PublicationDatePrecision.UNSPECIFIED,
    cover = null,
    description = "",
    identifiers = emptyList(),
    catalogTags = emptyList(),
    file = null,
    groups = emptyList()
)

internal fun <T> axisPage(
    page: Int,
    items: List<T>,
    total: Int = items.size,
    hasNext: Boolean = false
) = LibraryPage(total, items, hasNext, page > 1, page, DEFAULT_LIBRARY_PAGE_SIZE)

internal fun libraryProfile() = ConnectionProfile(
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
