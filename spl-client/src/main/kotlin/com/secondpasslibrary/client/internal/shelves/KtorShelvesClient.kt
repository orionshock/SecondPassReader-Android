package com.secondpasslibrary.client.internal.shelves

import com.secondpasslibrary.client.AddShelfItemInput
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.CreatePersonalShelfInput
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfDetailOptions
import com.secondpasslibrary.client.ShelfEditorListOptions
import com.secondpasslibrary.client.ShelfEditorPage
import com.secondpasslibrary.client.ShelfItem
import com.secondpasslibrary.client.ShelfItemListOptions
import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfItemPage
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.UpdatePersonalShelfInput
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.decodeProtocolBody
import com.secondpasslibrary.client.internal.transport.previewParameters
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorShelvesClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedShelvesClient {
    private val mutations = KtorShelfMutationClient(requests, json)

    override suspend fun list(options: ShelfListOptions): ShelfPage {
        val parameters = buildList {
            add("scope" to options.scope.queryValue)
            options.ownerGroupId?.let { add("owner_group" to it) }
            options.bookId?.let { add("book" to it) }
            options.ordering?.let { add("ordering" to it.queryValue) }
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
            addAll(previewParameters(options.previewLimit))
        }
        val response = requests.get("shelves/", parameters)
        return json.decodeProtocolBody<ShelfPageWire>(response.body(), "shelf page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun get(shelfId: String, options: ShelfDetailOptions): Shelf {
        val path = shelfPath(shelfId)
        val response = requests.get(path, previewParameters(options.previewLimit))
        return json.decodeProtocolBody<ShelfWire>(response.body(), "shelf").toModel()
    }

    override suspend fun listItems(shelfId: String, options: ShelfItemListOptions): ShelfItemPage {
        val parameters = listOf(
            "ordering" to options.ordering.queryValue,
            "page" to options.page.toString(),
            "page_size" to options.pageSize.toString()
        )
        val response = requests.get("${shelfPath(shelfId)}items/", parameters)
        return json.decodeProtocolBody<ShelfItemPageWire>(response.body(), "shelf item page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun listEditorItems(
        shelfId: String,
        options: ShelfEditorListOptions
    ): ShelfEditorPage {
        val parameters = listOf(
            "view" to "edit",
            "ordering" to ShelfItemOrdering.POSITION.queryValue,
            "page" to options.page.toString(),
            "page_size" to options.pageSize.toString()
        )
        val response = requests.get("${shelfPath(shelfId)}items/", parameters)
        return json.decodeProtocolBody<ShelfEditorPageWire>(response.body(), "shelf editor page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun create(input: CreatePersonalShelfInput): Shelf = mutations.create(input)

    override suspend fun update(shelfId: String, input: UpdatePersonalShelfInput): Shelf =
        mutations.update(shelfId, input)

    override suspend fun delete(shelfId: String) = mutations.delete(shelfId)

    override suspend fun addItem(shelfId: String, input: AddShelfItemInput): ShelfItem =
        mutations.addItem(shelfId, input)

    override suspend fun moveItem(
        shelfId: String,
        itemId: String,
        direction: ShelfItemMove
    ): ShelfItem = mutations.moveItem(shelfId, itemId, direction)

    override suspend fun setItemPosition(
        shelfId: String,
        itemId: String,
        position: Int
    ): ShelfItem = mutations.setItemPosition(shelfId, itemId, position)

    override suspend fun removeItem(shelfId: String, itemId: String) =
        mutations.removeItem(shelfId, itemId)
}

internal fun shelfPath(shelfId: String): String {
    require(shelfId.isNotBlank()) { "Shelf ID must not be blank." }
    return "shelves/${shelfId.encodeURLPathPart()}/"
}

internal fun shelfItemsPath(shelfId: String): String = "${shelfPath(shelfId)}items/"

internal fun shelfItemPath(shelfId: String, itemId: String): String {
    require(itemId.isNotBlank()) { "Shelf item ID must not be blank." }
    return "${shelfItemsPath(shelfId)}${itemId.encodeURLPathPart()}/"
}
