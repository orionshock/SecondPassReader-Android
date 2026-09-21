package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingSessionProgressClientTest {
    @Test
    fun `progress read preserves null and opaque values`() = runBlocking {
        val responses = ArrayDeque(listOf("""{"progress":null}""", POPULATED_PROGRESS))
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient { request ->
            requests += request
            jsonResponse(responses.removeFirst())
        }

        assertNull(client.marginalia.sessions.getProgress("session / 1"))
        val progress = client.marginalia.sessions.getProgress("session / 1")

        assertEquals("  epubcfi(/6/2)  ", progress?.cfi)
        assertEquals("  Chapter 1 - 5%  ", progress?.locationLabel)
        assertEquals("2026-08-19T00:00:00Z", progress?.updatedAt)
        assertEquals(
            "/api/v1/marginalia/sessions/session%20%2F%201/progress/",
            requests[0].url.encodedPath
        )
        assertEquals("Bearer spl_secret", requests[0].headers[HttpHeaders.Authorization])
    }

    @Test
    fun `progress replacement is whole-value PUT without server timestamp input`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(POPULATED_PROGRESS)
        }

        val result = client.marginalia.sessions.replaceProgress(
            "session-1",
            ReadingProgressInput("epubcfi(/6/8)", null)
        )

        assertEquals(HttpMethod.Put, request?.method)
        assertEquals(setOf("cfi"), requireNotNull(request).payload().keys)
        assertEquals("epubcfi(/6/8)", request.payload().string("cfi"))
        assertEquals("2026-08-19T00:00:00Z", result.updatedAt)
    }

    @Test
    fun `historical Web CFI is preserved through progress read and write mapping`() = runBlocking {
        val historical =
            "epubcfi(/6/34!/4[x9780451492128_EPUB-15]/2,/310/1:0,/314/1:17)"
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(
                """{"progress":{"cfi":"$historical","location_label":null,"updated_at":"now"}}"""
            )
        }

        assertEquals(historical, client.marginalia.sessions.getProgress("session-1")?.cfi)
        client.marginalia.sessions.replaceProgress(
            "session-1",
            ReadingProgressInput(historical)
        )

        assertEquals(historical, requireNotNull(request).payload().string("cfi"))
    }

    @Test
    fun `progress input enforces CFI and location bounds without trimming`() {
        assertEquals(" cfi ", ReadingProgressInput(" cfi ").cfi)
        assertThrows(IllegalArgumentException::class.java) { ReadingProgressInput(" ") }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingProgressInput("x".repeat(8 * 1024 + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingProgressInput("cfi", "x".repeat(256))
        }
    }

    @Test
    fun `progress write normalizes closed and current Book access failures`() {
        val closed = progressFailure(HttpStatusCode.Conflict, """{"code":"SESSION_CLOSED"}""")
        val visibility = progressFailure(
            HttpStatusCode.Forbidden,
            """{"code":"BOOK_ACCESS_REQUIRED"}"""
        )

        assertEquals(ReadingSessionLifecycleRejection.SESSION_CLOSED, closed.reason)
        assertEquals(
            ReadingSessionLifecycleRejection.CURRENT_BOOK_ACCESS_REQUIRED,
            visibility.reason
        )
    }

    @Test
    fun `progress read rejects missing resource auth and malformed response`() {
        assertThrows(SplClientException.AuthenticationRejected::class.java) {
            runBlocking { failingRead(HttpStatusCode.Unauthorized, "{}") }
        }
        val missing = assertThrows(SplClientException.ReadingSessionLifecycleRejected::class.java) {
            runBlocking { failingRead(HttpStatusCode.NotFound, "{}") }
        }
        assertEquals(ReadingSessionLifecycleRejection.RESOURCE_NOT_FOUND, missing.reason)
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { failingRead(HttpStatusCode.OK, "{}") }
        }
    }

    private fun progressFailure(
        status: HttpStatusCode,
        body: String
    ): SplClientException.ReadingSessionLifecycleRejected =
        assertThrows(SplClientException.ReadingSessionLifecycleRejected::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(body, status) }
                    .marginalia.sessions.replaceProgress(
                        "session-1",
                        ReadingProgressInput("cfi")
                    )
            }
        }

    private suspend fun failingRead(status: HttpStatusCode, body: String) {
        authenticatedClient { jsonResponse(body, status) }
            .marginalia.sessions.getProgress("session-1")
    }

    private fun authenticatedClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AuthenticatedSecondPassClient = KtorSecondPassClient(
        HttpClient(MockEngine(handler)) { expectSuccess = false }
    ).authenticated("https://library.example", BearerCredential.restore("spl_secret"))

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.payload(): JsonObject = Json.parseToJsonElement(
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
    ) as JsonObject

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content

    private companion object {
        const val POPULATED_PROGRESS =
            """{"progress":{"cfi":"  epubcfi(/6/2)  ","location_label":"  Chapter 1 - 5%  ","updated_at":"2026-08-19T00:00:00Z"}}"""
    }
}
