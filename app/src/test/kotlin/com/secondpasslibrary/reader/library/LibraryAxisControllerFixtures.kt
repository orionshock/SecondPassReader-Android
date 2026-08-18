package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedLibraryAuthorsClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile

internal class FakeLibraryAxisClient :
    AuthenticatedSecondPassClient,
    AuthenticatedLibraryAuthorsClient,
    AuthenticatedLibrarySeriesClient,
    AuthenticatedLibraryGroupsClient {
    override val library =
        FakeAuthenticatedLibraryClient(authors = this, series = this, groups = this)

    val authorRequests = mutableListOf<AuthorListOptions>()
    val groupAuthorRequests = mutableListOf<Pair<String, AuthorListOptions>>()
    val authorDetailRequests = mutableListOf<Pair<String, LibraryEntityDetailOptions>>()
    val seriesRequests = mutableListOf<SeriesListOptions>()
    val groupSeriesRequests = mutableListOf<Pair<String, SeriesListOptions>>()
    val seriesDetailRequests = mutableListOf<Pair<String, LibraryEntityDetailOptions>>()
    val groupRequests = mutableListOf<LibraryGroupListOptions>()

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

    override suspend fun listAuthors(options: AuthorListOptions): LibraryPage<LibraryAuthor> {
        authorRequests += options
        return authorList(options)
    }

    override suspend fun listGroupAuthors(
        groupId: String,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> {
        groupAuthorRequests += groupId to options
        return groupAuthorList(groupId, options)
    }

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor {
        authorDetailRequests += authorId to options
        return authorDetail(authorId)
    }

    override suspend fun listSeries(options: SeriesListOptions): LibraryPage<LibrarySeries> {
        seriesRequests += options
        return seriesList(options)
    }

    override suspend fun listGroupSeries(
        groupId: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> {
        groupSeriesRequests += groupId to options
        return groupSeriesList(groupId, options)
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

    override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> =
        error("Recent reading is outside this Library fixture.")

    override suspend fun listShelves(options: ShelfListOptions): ShelfPage =
        error("Shelves are outside this Library fixture.")
}

internal class FakeLibraryAxisClientProvider(private val client: AuthenticatedSecondPassClient) :
    AuthenticatedClientProvider {
    override suspend fun forProfile(profile: ConnectionProfile) = client
}

internal fun author(id: String) = LibraryAuthor(id, id, id, "Biography $id", 3, emptyList())

internal fun series(id: String) = LibrarySeries(id, id, id, "Summary $id", 4, emptyList())

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
