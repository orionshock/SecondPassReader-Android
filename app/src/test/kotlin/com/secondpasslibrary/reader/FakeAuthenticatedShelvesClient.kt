package com.secondpasslibrary.reader

import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfDetailOptions
import com.secondpasslibrary.client.ShelfEditorListOptions
import com.secondpasslibrary.client.ShelfEditorPage
import com.secondpasslibrary.client.ShelfItemListOptions
import com.secondpasslibrary.client.ShelfItemPage
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfPage

internal object FakeAuthenticatedShelvesClient : AuthenticatedShelvesClient {
    override suspend fun list(options: ShelfListOptions): ShelfPage = unsupported()

    override suspend fun get(shelfId: String, options: ShelfDetailOptions): Shelf = unsupported()

    override suspend fun listItems(shelfId: String, options: ShelfItemListOptions): ShelfItemPage =
        unsupported()

    override suspend fun listEditorItems(
        shelfId: String,
        options: ShelfEditorListOptions
    ): ShelfEditorPage = unsupported()

    private fun unsupported(): Nothing = error("Shelves are outside this test fixture.")
}
