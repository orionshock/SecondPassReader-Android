package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.MarginaliaAnnotationCollectionWire
import com.secondpasslibrary.client.internal.ReadingProgressResponseWire
import com.secondpasslibrary.client.internal.ReadingProgressWire
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal class KtorMarginaliaSynchronizationClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) {
    suspend fun getProgress(sessionId: String): ReadingProgress? {
        val response = requests.getResponse(progressPath(sessionId))
        requireReadingSessionLifecycleSuccess(response, setOf(HttpStatusCode.OK), json)
        return decodeProgress(response.body())?.toModel()
    }

    suspend fun replaceProgress(
        sessionId: String,
        progress: ReadingProgressInput
    ): ReadingProgress {
        val response = requests.put(
            progressPath(sessionId),
            json.encodeToString(progress.toWire())
        )
        requireReadingSessionLifecycleSuccess(response, setOf(HttpStatusCode.OK), json)
        return decodeProgress(response.body())?.toModel()
            ?: invalidProtocol("Reading Session progress")
    }

    suspend fun listAnnotations(sessionId: String): List<MarginaliaAnnotation> {
        val response = requests.getResponse(annotationsPath(sessionId))
        requireReadingSessionLifecycleSuccess(response, setOf(HttpStatusCode.OK), json)
        return decodeAnnotations(response)
    }

    suspend fun synchronizeAnnotations(
        sessionId: String,
        operations: List<MarginaliaAnnotationOperation>
    ): List<MarginaliaAnnotation> {
        validateAnnotationOperations(operations)
        val response = requests.post(
            "${annotationsPath(sessionId)}batch/",
            json.encodeToString(operations.toWire())
        )
        requireReadingSessionLifecycleSuccess(response, setOf(HttpStatusCode.OK), json)
        return decodeAnnotations(response)
    }

    private suspend fun decodeAnnotations(response: HttpResponse): List<MarginaliaAnnotation> =
        json.decodeLibrary<MarginaliaAnnotationCollectionWire>(
            response.body(),
            "Reading Session annotations"
        ).annotations?.map { it.toModel() } ?: invalidProtocol("Reading Session annotations")

    private fun decodeProgress(body: String): ReadingProgressWire? {
        val payload = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { invalidProtocol("Reading Session progress") }
        if ("progress" !in payload) invalidProtocol("Reading Session progress")
        return json.decodeLibrary<ReadingProgressResponseWire>(
            body,
            "Reading Session progress"
        ).progress
    }

    private fun progressPath(sessionId: String): String =
        "marginalia/sessions/${validatedSessionId(sessionId)}/progress/"

    private fun annotationsPath(sessionId: String): String =
        "marginalia/sessions/${validatedSessionId(sessionId)}/annotations/"

    private fun validatedSessionId(sessionId: String): String {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        return sessionId.encodeURLPathPart()
    }
}
