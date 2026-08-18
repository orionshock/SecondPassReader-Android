package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.AddShelfItemWire
import com.secondpasslibrary.client.internal.CreateShelfWire
import com.secondpasslibrary.client.internal.MoveShelfItemWire
import com.secondpasslibrary.client.internal.PositionShelfItemWire
import com.secondpasslibrary.client.internal.ShelfItemWire
import com.secondpasslibrary.client.internal.ShelfWire
import com.secondpasslibrary.client.internal.UpdateShelfWire
import io.ktor.client.call.body
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
        return json.decodeLibrary<ShelfWire>(response.body(), "shelf creation").toModel()
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
        return json.decodeLibrary<ShelfWire>(response.body(), "shelf update").toModel()
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
        return json.decodeLibrary<ShelfItemWire>(
            response.body(),
            "shelf item creation"
        ).toItemModel()
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
        return json.decodeLibrary<ShelfItemWire>(response.body(), "shelf item update").toItemModel()
    }
}

internal val ShelfVisibility.wireValue: String
    get() = when (this) {
        ShelfVisibility.PRIVATE -> "private"
        ShelfVisibility.LISTED -> "listed"
    }
