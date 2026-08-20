package com.secondpasslibrary.client

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private const val SUCCESS_START = 200
private const val SUCCESS_END = 299

internal suspend fun requireReadingSessionLifecycleSuccess(
    response: HttpResponse,
    expectedStatuses: Set<HttpStatusCode>,
    json: Json
) {
    if (response.status in expectedStatuses) return
    when (response.status) {
        HttpStatusCode.Unauthorized -> throw SplClientException.AuthenticationRejected()

        HttpStatusCode.Forbidden -> rejectLifecycle(
            ReadingSessionLifecycleRejection.PERMISSION_DENIED
        )

        HttpStatusCode.NotFound -> rejectLifecycle(
            ReadingSessionLifecycleRejection.RESOURCE_NOT_FOUND
        )

        HttpStatusCode.BadRequest, HttpStatusCode.Conflict -> rejectLifecycleResponse(
            response,
            json
        )

        else -> {
            if (response.status.value in SUCCESS_START..SUCCESS_END) {
                invalidProtocol("Reading Session lifecycle")
            }
            throw SplClientException.AuthenticatedRequestFailed()
        }
    }
}

private suspend fun rejectLifecycleResponse(response: HttpResponse, json: Json): Nothing {
    val payload = runCatching { json.parseToJsonElement(response.body<String>()).jsonObject }
        .getOrNull()
    val code = payload?.findCode()?.uppercase()
    val reason = when (code) {
        "SESSION_CLOSED" -> ReadingSessionLifecycleRejection.SESSION_CLOSED

        "PERMISSION_DENIED" -> ReadingSessionLifecycleRejection.PERMISSION_DENIED

        "INVALID_REQUEST",
        "IDEMPOTENCY_CONFLICT" -> ReadingSessionLifecycleRejection.INVALID_REQUEST

        else -> if (response.status == HttpStatusCode.Conflict) {
            ReadingSessionLifecycleRejection.INVALID_REQUEST
        } else {
            ReadingSessionLifecycleRejection.VALIDATION
        }
    }
    val fields = payload?.keys
        ?.filterNot { it == "code" || it == "error_code" || it == "detail" }
        ?.mapTo(mutableSetOf(), String::toLifecycleField)
        .orEmpty()
    rejectLifecycle(reason, fields)
}

private fun JsonObject.findCode(): String? = primitive("code") ?: primitive("error_code")
    ?: (get("detail") as? JsonObject)?.primitive("code")

private fun JsonObject.primitive(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull

private fun String.toLifecycleField(): ReadingSessionMutationField = when (this) {
    "name" -> ReadingSessionMutationField.NAME
    "notes" -> ReadingSessionMutationField.NOTES
    "progress" -> ReadingSessionMutationField.PROGRESS
    "cfi" -> ReadingSessionMutationField.CFI
    "location_label" -> ReadingSessionMutationField.LOCATION_LABEL
    "idempotency_key" -> ReadingSessionMutationField.IDEMPOTENCY_KEY
    else -> ReadingSessionMutationField.GENERAL
}

private fun rejectLifecycle(
    reason: ReadingSessionLifecycleRejection,
    fields: Set<ReadingSessionMutationField> = emptySet()
): Nothing = throw SplClientException.ReadingSessionLifecycleRejected(reason, fields)
