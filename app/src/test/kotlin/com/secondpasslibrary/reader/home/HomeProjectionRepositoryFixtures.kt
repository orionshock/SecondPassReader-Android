package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.AddShelfItemInput
import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.CreatePersonalShelfInput
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfDetailOptions
import com.secondpasslibrary.client.ShelfEditorListOptions
import com.secondpasslibrary.client.ShelfEditorPage
import com.secondpasslibrary.client.ShelfItem
import com.secondpasslibrary.client.ShelfItemListOptions
import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.client.ShelfItemPage
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.UpdatePersonalShelfInput
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
import com.secondpasslibrary.reader.FakeAuthenticatedMarginaliaClient
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
    override val library = FakeAuthenticatedLibraryClient()
    override val marginalia = object : AuthenticatedMarginaliaClient {
        override val books = FakeAuthenticatedMarginaliaClient.books
        override val sessions = object : AuthenticatedReadingSessionsClient {
            override suspend fun list(
                options: ReadingSessionListOptions
            ): MarginaliaPage<ReadingSessionListItem> = unsupported()

            override suspend fun recent(options: RecentReadingOptions): List<RecentReadingItem> {
                recentRequests += options
                return recentCall(options)
            }

            override suspend fun get(sessionId: String): ReadingSessionDetailResult = unsupported()
        }
    }
    override val shelves = object : AuthenticatedShelvesClient {
        override suspend fun list(options: ShelfListOptions): ShelfPage {
            shelfRequests += options
            val items = shelfCall(options).map(ShelfSummary::toFixtureShelf)
            return ShelfPage(items.size, false, false, items, options.page, options.pageSize)
        }

        override suspend fun get(shelfId: String, options: ShelfDetailOptions): Shelf =
            unsupported()

        override suspend fun listItems(
            shelfId: String,
            options: ShelfItemListOptions
        ): ShelfItemPage = unsupported()

        override suspend fun listEditorItems(
            shelfId: String,
            options: ShelfEditorListOptions
        ): ShelfEditorPage = unsupported()

        override suspend fun create(input: CreatePersonalShelfInput): Shelf = unsupported()

        override suspend fun update(shelfId: String, input: UpdatePersonalShelfInput): Shelf =
            unsupported()

        override suspend fun delete(shelfId: String): Unit = unsupported()

        override suspend fun addItem(shelfId: String, input: AddShelfItemInput): ShelfItem =
            unsupported()

        override suspend fun moveItem(
            shelfId: String,
            itemId: String,
            direction: ShelfItemMove
        ): ShelfItem = unsupported()

        override suspend fun setItemPosition(
            shelfId: String,
            itemId: String,
            position: Int
        ): ShelfItem = unsupported()

        override suspend fun removeItem(shelfId: String, itemId: String): Unit = unsupported()
    }

    val recentRequests = mutableListOf<RecentReadingOptions>()
    val shelfRequests = mutableListOf<ShelfListOptions>()
    var recentCall: suspend (RecentReadingOptions) -> List<RecentReadingItem> = { emptyList() }
    var shelfCall: suspend (ShelfListOptions) -> List<ShelfSummary> = { emptyList() }
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
    owner = ShelfOwner.User("profile-1", "reader"),
    visibility = ShelfVisibility.PRIVATE,
    itemCount = 1,
    canEdit = true,
    previewBooks = null
)

private fun ShelfSummary.toFixtureShelf() = Shelf(
    id = id,
    name = name,
    description = description,
    owner = owner,
    visibility = visibility,
    itemCount = itemCount,
    canEdit = canEdit,
    createdBy = null,
    createdAt = "2026-08-01T00:00:00Z",
    updatedAt = "2026-08-02T00:00:00Z",
    matchedItemId = null,
    previewBooks = previewBooks
)

private fun unsupported(): Nothing = error("Shelf operation is outside this Home fixture.")

internal val OLD_FETCHED_AT: Instant = Instant.parse("2026-08-16T10:00:00Z")
internal val NEW_FETCHED_AT: Instant = Instant.parse("2026-08-16T16:00:00Z")
