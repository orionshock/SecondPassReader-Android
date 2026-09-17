package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingProgressInput
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.AuthenticatedResponse
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.validateAnnotationOperations
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
        return decodeProgress(response)?.toModel()
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
        return decodeProgress(response)?.toModel()
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

    private fun decodeAnnotations(response: AuthenticatedResponse): List<MarginaliaAnnotation> =
        requests.decode<MarginaliaAnnotationCollectionWire>(
            response,
            "Reading Session annotations"
        ).annotations?.map { it.toModel() } ?: invalidProtocol("Reading Session annotations")

    private fun decodeProgress(response: AuthenticatedResponse): ReadingProgressWire? {
        val payload = runCatching { json.parseToJsonElement(response.body).jsonObject }
            .getOrElse { invalidProtocol("Reading Session progress") }
        if ("progress" !in payload) invalidProtocol("Reading Session progress")
        return requests.decode<ReadingProgressResponseWire>(
            response,
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
