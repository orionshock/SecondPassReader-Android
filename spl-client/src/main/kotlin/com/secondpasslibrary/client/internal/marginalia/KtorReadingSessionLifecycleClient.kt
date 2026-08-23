package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.MarginaliaIdempotencyKey
import com.secondpasslibrary.client.ReadingSessionBootstrap
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionFinalization
import com.secondpasslibrary.client.ReadingSessionMetadataInput
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.decodeProtocolBody
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class KtorReadingSessionLifecycleClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) {
    suspend fun getActiveSession(bookId: String): ReadingSessionBootstrap {
        requireBookId(bookId)
        val response = requests.getResponse(
            "marginalia/books/${bookId.encodeURLPathPart()}/active-session/"
        )
        requireReadingSessionLifecycleSuccess(response, setOf(HttpStatusCode.OK), json)
        return decodeBootstrap(response.body()).also { bootstrap ->
            if (bootstrap.created) invalidProtocol("active Reading Session lookup")
        }
    }

    suspend fun openSession(
        bookId: String,
        metadata: ReadingSessionMetadataInput
    ): ReadingSessionBootstrap {
        requireBookId(bookId)
        val response = requests.post(
            "marginalia/books/${bookId.encodeURLPathPart()}/open/",
            json.encodeToString(metadata.toWire())
        )
        requireReadingSessionLifecycleSuccess(
            response,
            setOf(HttpStatusCode.OK, HttpStatusCode.Created),
            json
        )
        return decodeBootstrap(response.body()).also { bootstrap ->
            val statusCreated = response.status == HttpStatusCode.Created
            if (bootstrap.created != statusCreated || bootstrap.activeSession == null) {
                invalidProtocol("open Reading Session")
            }
        }
    }

    suspend fun startOver(
        bookId: String,
        idempotencyKey: MarginaliaIdempotencyKey,
        finalization: ReadingSessionFinalization
    ): ReadingSessionBootstrap {
        requireBookId(bookId)
        val response = requests.post(
            "marginalia/books/${bookId.encodeURLPathPart()}/start-over/",
            json.encodeToString(finalization.toWire()),
            idempotencyKey.value
        )
        requireReadingSessionLifecycleSuccess(response, setOf(HttpStatusCode.Created), json)
        return decodeBootstrap(response.body()).also { bootstrap ->
            if (!bootstrap.created || bootstrap.activeSession == null) {
                invalidProtocol("start-over Reading Session")
            }
        }
    }

    suspend fun updateMetadata(
        sessionId: String,
        metadata: ReadingSessionMetadataInput
    ): ReadingSessionDetailResult {
        requireSessionId(sessionId)
        require(metadata.name != null || metadata.notes != null) {
            "Reading Session metadata update must contain a field."
        }
        val response = requests.patch(
            "marginalia/sessions/${sessionId.encodeURLPathPart()}/",
            json.encodeToString(metadata.toWire())
        )
        return decodeDetail(response, HttpStatusCode.OK)
    }

    suspend fun close(
        sessionId: String,
        finalization: ReadingSessionFinalization
    ): ReadingSessionDetailResult {
        requireSessionId(sessionId)
        val response = requests.post(
            "marginalia/sessions/${sessionId.encodeURLPathPart()}/close/",
            json.encodeToString(finalization.toWire())
        )
        return decodeDetail(response, HttpStatusCode.OK)
    }

    private suspend fun decodeDetail(
        response: HttpResponse,
        expectedStatus: HttpStatusCode
    ): ReadingSessionDetailResult {
        requireReadingSessionLifecycleSuccess(response, setOf(expectedStatus), json)
        return json.decodeProtocolBody<ReadingSessionDetailWire>(
            response.body(),
            "Reading Session detail"
        )
            .toModel()
    }

    private fun decodeBootstrap(body: String): ReadingSessionBootstrap =
        json.decodeProtocolBody<ReadingSessionBootstrapWire>(
            body,
            "Reading Session bootstrap"
        ).toModel()

    private fun requireBookId(bookId: String) {
        require(bookId.isNotBlank()) { "Marginalia Book ID must not be blank." }
    }

    private fun requireSessionId(sessionId: String) {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
    }
}
