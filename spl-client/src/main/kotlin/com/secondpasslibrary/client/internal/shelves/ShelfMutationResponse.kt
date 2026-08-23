package com.secondpasslibrary.client.internal.shelves

import com.secondpasslibrary.client.ShelfMutationField
import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private const val HTTP_SUCCESS_START = 200
private const val HTTP_SUCCESS_END = 299

internal suspend fun requireShelfMutationSuccess(
    response: HttpResponse,
    operation: ShelfMutationOperation,
    expectedStatus: HttpStatusCode,
    json: Json
) {
    if (response.status == expectedStatus) return
    when (response.status) {
        HttpStatusCode.Unauthorized -> throw SplClientException.AuthenticationRejected()

        HttpStatusCode.Forbidden -> rejectShelf(ShelfMutationRejection.NOT_AUTHORIZED)

        HttpStatusCode.NotFound -> rejectShelf(ShelfMutationRejection.RESOURCE_NOT_FOUND)

        HttpStatusCode.BadRequest -> rejectBadRequest(operation, response, json)

        else -> {
            if (response.status.value in HTTP_SUCCESS_START..HTTP_SUCCESS_END) {
                invalidProtocol("shelf mutation")
            }
            throw SplClientException.AuthenticatedRequestFailed()
        }
    }
}

private suspend fun rejectBadRequest(
    operation: ShelfMutationOperation,
    response: HttpResponse,
    json: Json
): Nothing {
    val fields = parseMutationFields(response.body(), json)
    val reason = when {
        operation == ShelfMutationOperation.ADD_ITEM && ShelfMutationField.BOOK in fields ->
            ShelfMutationRejection.DUPLICATE_BOOK

        operation == ShelfMutationOperation.MOVE_ITEM && ShelfMutationField.MOVE in fields ->
            ShelfMutationRejection.INVALID_MOVE

        operation == ShelfMutationOperation.SET_POSITION && ShelfMutationField.POSITION in fields ->
            ShelfMutationRejection.DIRECT_POSITION_UNAVAILABLE

        else -> ShelfMutationRejection.VALIDATION
    }
    rejectShelf(reason, fields)
}

private fun parseMutationFields(body: String, json: Json): Set<ShelfMutationField> {
    val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return setOf(ShelfMutationField.GENERAL)
    return payload.keys.mapTo(mutableSetOf(), String::toMutationField)
        .ifEmpty { setOf(ShelfMutationField.GENERAL) }
}

private fun String.toMutationField(): ShelfMutationField = when (this) {
    "name" -> ShelfMutationField.NAME
    "description" -> ShelfMutationField.DESCRIPTION
    "visibility" -> ShelfMutationField.VISIBILITY
    "book" -> ShelfMutationField.BOOK
    "position" -> ShelfMutationField.POSITION
    "move" -> ShelfMutationField.MOVE
    else -> ShelfMutationField.GENERAL
}

private fun rejectShelf(
    reason: ShelfMutationRejection,
    fields: Set<ShelfMutationField> = emptySet()
): Nothing = throw SplClientException.ShelfMutationRejected(reason, fields)
