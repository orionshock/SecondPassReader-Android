package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.GroupBookListOptions
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionSnapshot
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projection.HomeShelfVariant
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class FakeHomeProjectionStore : HomeProjectionStore {
    private val recent =
        mutableMapOf<
            Pair<String, HomeRecentReadingVariant>,
            HomeProjectionSnapshot<RecentReadingItem>
            >()
    private val shelves =
        mutableMapOf<Pair<String, HomeShelfVariant>, HomeProjectionSnapshot<ShelfSummary>>()
    var recentReplacements = 0
    var shelfReplacements = 0

    override suspend fun readRecentReading(
        account: HomeAccountScopeKey,
        variant: HomeRecentReadingVariant
    ) = recent[account.value to variant]

    override suspend fun replaceRecentReading(
        account: HomeAccountScopeKey,
        variant: HomeRecentReadingVariant,
        items: List<RecentReadingItem>,
        fetchedAt: Instant
    ) {
        recentReplacements += 1
        recent[account.value to variant] = HomeProjectionSnapshot(items, fetchedAt)
    }

    override suspend fun readShelves(account: HomeAccountScopeKey, variant: HomeShelfVariant) =
        shelves[account.value to variant]

    override suspend fun replaceShelves(
        account: HomeAccountScopeKey,
        variant: HomeShelfVariant,
        shelves: List<ShelfSummary>,
        fetchedAt: Instant
    ) {
        shelfReplacements += 1
        this.shelves[account.value to variant] = HomeProjectionSnapshot(shelves, fetchedAt)
    }

    fun seedRecent(
        account: HomeProjectionAccount,
        variant: HomeRecentReadingVariant,
        items: List<RecentReadingItem>,
        fetchedAt: Instant = OLD_FETCHED_AT
    ) {
        recent[account.scopeKey.value to variant] = HomeProjectionSnapshot(items, fetchedAt)
    }

    fun seedShelves(
        account: HomeProjectionAccount,
        items: List<ShelfSummary>,
        fetchedAt: Instant = OLD_FETCHED_AT
    ) {
        shelves[account.scopeKey.value to HomeShelfVariant.FirstPageWithPreviews] =
            HomeProjectionSnapshot(items, fetchedAt)
    }
}

internal class FakeHomeAuthenticatedClient : AuthenticatedSecondPassClient {
    val recentRequests = mutableListOf<RecentReadingOptions>()
    val shelfRequests = mutableListOf<ShelfListOptions>()
    var recentCall: suspend (RecentReadingOptions) -> List<RecentReadingItem> = { emptyList() }
    var shelfCall: suspend (ShelfListOptions) -> List<ShelfSummary> = { emptyList() }

    override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> {
        recentRequests += options
        return recentCall(options)
    }

    override suspend fun listShelves(options: ShelfListOptions): ShelfPage {
        shelfRequests += options
        val items = shelfCall(options)
        return ShelfPage(items.size, false, false, items)
    }

    override suspend fun listBooks(options: BookListOptions): LibraryPage<CompactBook> =
        error("Book listing is outside this Home projection fixture.")

    override suspend fun searchLibrary(options: LibrarySearchOptions): LibraryPage<CompactBook> =
        error("Library search is outside this Home projection fixture.")

    override suspend fun listLibraryGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> =
        error("Library groups are outside this Home projection fixture.")

    override suspend fun listGroupBooks(
        groupId: String,
        options: GroupBookListOptions
    ): LibraryPage<CompactBook> = error("Group books are outside this Home projection fixture.")

    override suspend fun listAuthors(options: AuthorListOptions): LibraryPage<LibraryAuthor> =
        error("Authors are outside this Home projection fixture.")

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor = error("Authors are outside this Home projection fixture.")

    override suspend fun listGroupAuthors(
        groupId: String,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> = error("Authors are outside this Home projection fixture.")

    override suspend fun listSeries(options: SeriesListOptions): LibraryPage<LibrarySeries> =
        error("Series are outside this Home projection fixture.")

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries = error("Series are outside this Home projection fixture.")

    override suspend fun listGroupSeries(
        groupId: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> = error("Series are outside this Home projection fixture.")
}

internal fun homeRepository(
    store: HomeProjectionStore,
    client: AuthenticatedSecondPassClient,
    fetchedAt: Instant = NEW_FETCHED_AT
) = HomeProjectionRepository(store, provider(client), Clock.fixed(fetchedAt, ZoneOffset.UTC))

private fun provider(client: AuthenticatedSecondPassClient) = object : AuthenticatedClientProvider {
    override suspend fun forProfile(profile: ConnectionProfile) = client
}

internal fun projectionAccount(
    profileId: String = "profile-1",
    serverOrigin: String = "https://library.example"
) = HomeProjectionAccount(
    profile =
        ConnectionProfile(
            serverOrigin = serverOrigin,
            serverBaseUrl = "$serverOrigin/",
            apiBaseUrl = "$serverOrigin/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "",
            clientSessionId = "client-session",
            clientName = "Tablet",
            clientType = "second-pass-android-client"
        ),
    profileId = profileId
)

internal fun recentItem(id: String) = RecentReadingItem(
    sessionId = id,
    sessionName = id,
    status = ReadingSessionStatus.ACTIVE,
    lastActivityAt = "2026-08-16T12:00:00Z",
    book = RecentReadingBook("book-$id", "Book $id", null, true),
    progress = null
)

internal fun shelfItem(id: String) = ShelfSummary(
    id = id,
    name = id,
    description = null,
    owner = ShelfOwner.User("profile-1", "reader", null, null),
    visibility = "private",
    itemCount = 1,
    canEdit = true,
    previewBooks = null
)

internal val OLD_FETCHED_AT: Instant = Instant.parse("2026-08-16T10:00:00Z")
internal val NEW_FETCHED_AT: Instant = Instant.parse("2026-08-16T16:00:00Z")
