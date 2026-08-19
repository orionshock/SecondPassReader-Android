package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.CreatePersonalShelfInput
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfDetailOptions
import com.secondpasslibrary.client.ShelfItem
import com.secondpasslibrary.client.ShelfItemListOptions
import com.secondpasslibrary.client.ShelfItemPage
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.UpdatePersonalShelfInput
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
import com.secondpasslibrary.reader.FakeAuthenticatedShelvesClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.library.axisBook

internal class RecordingShelvesCapability :
    AuthenticatedShelvesClient by FakeAuthenticatedShelvesClient {
    val listRequests = mutableListOf<ShelfListOptions>()
    val detailRequests = mutableListOf<Pair<String, ShelfDetailOptions>>()
    val itemRequests = mutableListOf<Pair<String, ShelfItemListOptions>>()
    val createRequests = mutableListOf<CreatePersonalShelfInput>()
    val updateRequests = mutableListOf<Pair<String, UpdatePersonalShelfInput>>()
    val deleteRequests = mutableListOf<String>()

    var listCall: suspend (ShelfListOptions) -> ShelfPage = { shelfPage(it.page, emptyList()) }
    var detailCall: suspend (String) -> Shelf = { shelf(it) }
    var itemsCall: suspend (String, ShelfItemListOptions) -> ShelfItemPage =
        { _, options -> shelfItemPage(options.page, emptyList()) }
    var createCall: suspend (CreatePersonalShelfInput) -> Shelf = { shelf("created") }
    var updateCall: suspend (String, UpdatePersonalShelfInput) -> Shelf = { id, _ -> shelf(id) }
    var deleteCall: suspend (String) -> Unit = {}

    override suspend fun list(options: ShelfListOptions): ShelfPage {
        listRequests += options
        return listCall(options)
    }

    override suspend fun get(shelfId: String, options: ShelfDetailOptions): Shelf {
        detailRequests += shelfId to options
        return detailCall(shelfId)
    }

    override suspend fun listItems(shelfId: String, options: ShelfItemListOptions): ShelfItemPage {
        itemRequests += shelfId to options
        return itemsCall(shelfId, options)
    }

    override suspend fun create(input: CreatePersonalShelfInput): Shelf {
        createRequests += input
        return createCall(input)
    }

    override suspend fun update(shelfId: String, input: UpdatePersonalShelfInput): Shelf {
        updateRequests += shelfId to input
        return updateCall(shelfId, input)
    }

    override suspend fun delete(shelfId: String) {
        deleteRequests += shelfId
        deleteCall(shelfId)
    }
}

internal class ShelvesTestClient(override val shelves: AuthenticatedShelvesClient) :
    AuthenticatedSecondPassClient {
    override val library = FakeAuthenticatedLibraryClient()

    override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> =
        error("Recent reading is outside this Shelves fixture.")
}

internal class ShelvesTestClientProvider(client: AuthenticatedSecondPassClient) :
    AuthenticatedClientProvider {
    private val activeClient = client

    override suspend fun forProfile(profile: ConnectionProfile) = activeClient
}

internal fun shelvesProfile() = ConnectionProfile(
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

internal fun shelf(id: String, canEdit: Boolean = false) = Shelf(
    id = id,
    name = "Shelf $id",
    description = null,
    owner = ShelfOwner.User("profile-1", "reader"),
    visibility = ShelfVisibility.PRIVATE,
    itemCount = 2,
    canEdit = canEdit,
    createdBy = null,
    createdAt = "2026-08-18T00:00:00Z",
    updatedAt = "2026-08-18T00:00:00Z",
    matchedItemId = null,
    previewBooks = emptyList()
)

internal fun shelfItem(id: String, position: Int) = ShelfItem(
    id = id,
    shelfId = "shelf-1",
    book = axisBook("book-$id"),
    position = position,
    addedBy = null,
    createdAt = "2026-08-18T00:00:00Z",
    updatedAt = "2026-08-18T00:00:00Z"
)

internal fun shelfPage(
    page: Int,
    shelves: List<Shelf>,
    total: Int = shelves.size,
    hasNext: Boolean = false
) = ShelfPage(total, hasNext, page > 1, shelves, page, SHELVES_PAGE_SIZE)

internal fun shelfItemPage(
    page: Int,
    items: List<ShelfItem>,
    total: Int = items.size,
    hasNext: Boolean = false
) = ShelfItemPage(total, items, hasNext, page > 1, page, SHELVES_PAGE_SIZE)
