package com.secondpasslibrary.client.internal.shelves

import com.secondpasslibrary.client.AddShelfItemInput
import com.secondpasslibrary.client.CreatePersonalShelfInput
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfItem
import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.UpdatePersonalShelfInput
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class ShelfMutationOperation {
    CREATE,
    UPDATE,
    DELETE,
    ADD_ITEM,
    MOVE_ITEM,
    SET_POSITION,
    REMOVE_ITEM
}

internal class KtorShelfMutationClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) {
    suspend fun create(input: CreatePersonalShelfInput): Shelf {
        val payload = CreateShelfWire(
            name = input.name.trim(),
            description = input.description,
            visibility = input.visibility.wireValue,
            ownerType = "user"
        )
        val response = requests.post("shelves/", json.encodeToString(payload))
        requireShelfMutationSuccess(
            response,
            ShelfMutationOperation.CREATE,
            HttpStatusCode.Created,
            json
        )
        return requests.decode<ShelfWire>(response, "shelf creation").toModel()
    }

    suspend fun update(shelfId: String, input: UpdatePersonalShelfInput): Shelf {
        val payload = UpdateShelfWire(
            name = input.name?.trim(),
            description = input.description,
            visibility = input.visibility?.wireValue
        )
        val response = requests.patch(shelfPath(shelfId), json.encodeToString(payload))
        requireShelfMutationSuccess(
            response,
            ShelfMutationOperation.UPDATE,
            HttpStatusCode.OK,
            json
        )
        return requests.decode<ShelfWire>(response, "shelf update").toModel()
    }

    suspend fun delete(shelfId: String) {
        val response = requests.delete(shelfPath(shelfId))
        requireShelfMutationSuccess(
            response,
            ShelfMutationOperation.DELETE,
            HttpStatusCode.NoContent,
            json
        )
    }

    suspend fun addItem(shelfId: String, input: AddShelfItemInput): ShelfItem {
        val payload = AddShelfItemWire(input.bookId.trim(), input.position)
        val response = requests.post(shelfItemsPath(shelfId), json.encodeToString(payload))
        requireShelfMutationSuccess(
            response,
            ShelfMutationOperation.ADD_ITEM,
            HttpStatusCode.Created,
            json
        )
        return requests.decode<ShelfItemWire>(response, "shelf item creation").toItemModel()
    }

    suspend fun moveItem(shelfId: String, itemId: String, direction: ShelfItemMove): ShelfItem {
        val payload = MoveShelfItemWire(direction.queryValue)
        return patchItem(
            shelfId,
            itemId,
            json.encodeToString(payload),
            ShelfMutationOperation.MOVE_ITEM
        )
    }

    suspend fun setItemPosition(shelfId: String, itemId: String, position: Int): ShelfItem {
        require(position >= 0) { "Shelf item position must not be negative." }
        val payload = PositionShelfItemWire(position)
        return patchItem(
            shelfId,
            itemId,
            json.encodeToString(payload),
            ShelfMutationOperation.SET_POSITION
        )
    }

    suspend fun removeItem(shelfId: String, itemId: String) {
        val response = requests.delete(shelfItemPath(shelfId, itemId))
        requireShelfMutationSuccess(
            response,
            ShelfMutationOperation.REMOVE_ITEM,
            HttpStatusCode.NoContent,
            json
        )
    }

    private suspend fun patchItem(
        shelfId: String,
        itemId: String,
        payload: String,
        operation: ShelfMutationOperation
    ): ShelfItem {
        val response = requests.patch(shelfItemPath(shelfId, itemId), payload)
        requireShelfMutationSuccess(response, operation, HttpStatusCode.OK, json)
        return requests.decode<ShelfItemWire>(response, "shelf item update").toItemModel()
    }
}

internal val ShelfVisibility.wireValue: String
    get() = when (this) {
        ShelfVisibility.PRIVATE -> "private"
        ShelfVisibility.LISTED -> "listed"
    }
