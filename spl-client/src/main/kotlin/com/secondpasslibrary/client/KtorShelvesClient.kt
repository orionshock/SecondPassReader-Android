package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.ShelfEditorPageWire
import com.secondpasslibrary.client.internal.ShelfItemPageWire
import com.secondpasslibrary.client.internal.ShelfPageWire
import com.secondpasslibrary.client.internal.ShelfWire
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorShelvesClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedShelvesClient {
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
        return json.decodeLibrary<ShelfPageWire>(response.body(), "shelf page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun get(shelfId: String, options: ShelfDetailOptions): Shelf {
        val path = shelfPath(shelfId)
        val response = requests.get(path, previewParameters(options.previewLimit))
        return json.decodeLibrary<ShelfWire>(response.body(), "shelf").toModel()
    }

    override suspend fun listItems(shelfId: String, options: ShelfItemListOptions): ShelfItemPage {
        val parameters = listOf(
            "ordering" to options.ordering.queryValue,
            "page" to options.page.toString(),
            "page_size" to options.pageSize.toString()
        )
        val response = requests.get("${shelfPath(shelfId)}items/", parameters)
        return json.decodeLibrary<ShelfItemPageWire>(response.body(), "shelf item page")
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
        return json.decodeLibrary<ShelfEditorPageWire>(response.body(), "shelf editor page")
            .toModel(options.page, options.pageSize)
    }

    private fun shelfPath(shelfId: String): String {
        require(shelfId.isNotBlank()) { "Shelf ID must not be blank." }
        return "shelves/${shelfId.encodeURLPathPart()}/"
    }
}
