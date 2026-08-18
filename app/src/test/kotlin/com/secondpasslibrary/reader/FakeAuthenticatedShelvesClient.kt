package com.secondpasslibrary.reader

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
import com.secondpasslibrary.client.ShelfItemPage
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.UpdatePersonalShelfInput

internal object FakeAuthenticatedShelvesClient : AuthenticatedShelvesClient {
    override suspend fun list(options: ShelfListOptions): ShelfPage = unsupported()

    override suspend fun get(shelfId: String, options: ShelfDetailOptions): Shelf = unsupported()

    override suspend fun listItems(shelfId: String, options: ShelfItemListOptions): ShelfItemPage =
        unsupported()

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

    private fun unsupported(): Nothing = error("Shelves are outside this test fixture.")
}
