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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionLifecycleClientTest {
    @Test
    fun `active lookup maps nullable active Session annotations and closed first page`() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val noActiveSession = BOOTSTRAP.replaceFirst("\"session\":$SESSION", "\"session\":null")
            val responses = ArrayDeque(listOf(BOOTSTRAP, noActiveSession))
            val client = authenticatedClient { request ->
                requests += request
                jsonResponse(responses.removeFirst())
            }

            val active = client.marginalia.books.getActiveSession("book / 1")
            val absent = client.marginalia.books.getActiveSession("book / 1")

            assertFalse(active.created)
            assertEquals("session-1", active.activeSession?.summary?.id)
            assertEquals(2, active.annotations.size)
            assertTrue(active.annotations[0] is MarginaliaAnnotation.Highlight)
            assertTrue(active.annotations[1] is MarginaliaAnnotation.Bookmark)
            val highlight = active.annotations[0] as MarginaliaAnnotation.Highlight
            assertEquals(MarginaliaHighlightColor.ORANGE, highlight.body.color)
            assertEquals(4, active.closedSessions.totalCount)
            assertTrue(active.closedSessions.hasNext)
            assertNull(absent.activeSession)
            assertEquals(
                "/api/v1/marginalia/books/book%20%2F%201/active-session/",
                requests[0].url.encodedPath
            )
        }

    @Test
    fun `open maps created status and sends optional initial metadata`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val responses =
            ArrayDeque(
                listOf(BOOTSTRAP, BOOTSTRAP.replace("\"created\":false", "\"created\":true"))
            )
        val statuses = ArrayDeque(listOf(HttpStatusCode.OK, HttpStatusCode.Created))
        val client = authenticatedClient { request ->
            requests += request
            jsonResponse(responses.removeFirst(), statuses.removeFirst())
        }

        assertFalse(client.marginalia.books.openSession("book-1").created)
        assertTrue(
            client.marginalia.books.openSession(
                "book-1",
                ReadingSessionMetadataInput(name = "Pass two", notes = "Notes")
            ).created
        )

        assertEquals(emptySet<String>(), requests[0].payload().keys)
        assertEquals("Pass two", requests[1].payload().string("name"))
        assertEquals("Notes", requests[1].payload().string("notes"))
        assertEquals(HttpMethod.Post, requests[1].method)
    }

    @Test
    fun `metadata update is partial and closed rejection is structured`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(DETAIL)
        }

        client.marginalia.sessions.updateMetadata(
            "session 1",
            ReadingSessionMetadataInput(notes = "Changed")
        )

        assertEquals(setOf("notes"), requireNotNull(request).payload().keys)
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/v1/marginalia/sessions/session%201/", request.url.encodedPath)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                client.marginalia.sessions.updateMetadata(
                    "session-1",
                    ReadingSessionMetadataInput()
                )
            }
        }
        val failure = assertThrows(SplClientException.ReadingSessionLifecycleRejected::class.java) {
            runBlocking {
                authenticatedClient {
                    jsonResponse("""{"code":"SESSION_CLOSED"}""", HttpStatusCode.Conflict)
                }.marginalia.sessions.updateMetadata(
                    "session-1",
                    ReadingSessionMetadataInput(name = "Too late")
                )
            }
        }
        assertEquals(ReadingSessionLifecycleRejection.SESSION_CLOSED, failure.reason)
    }

    @Test
    fun `close supports empty retry and atomic final progress`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient { request ->
            requests += request
            jsonResponse(DETAIL)
        }

        client.marginalia.sessions.close("session-1")
        val finalization = ReadingSessionFinalization(
            name = "Finished",
            progress = ReadingProgressInput("epubcfi(/6/8)", "Chapter 4")
        )
        client.marginalia.sessions.close("session-1", finalization)
        client.marginalia.sessions.close("session-1", finalization)

        assertEquals(emptySet<String>(), requests[0].payload().keys)
        val finalPayload = requests[1].payload()
        assertEquals("Finished", finalPayload.string("name"))
        assertEquals("epubcfi(/6/8)", finalPayload.objectValue("progress").string("cfi"))
        assertEquals("Chapter 4", finalPayload.objectValue("progress").string("location_label"))
        assertEquals(requests[1].payload(), requests[2].payload())
    }

    @Test
    fun `start over requires stable bounded key and preserves it across executions`() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client = authenticatedClient { request ->
                requests += request
                jsonResponse(
                    BOOTSTRAP.replace("\"created\":false", "\"created\":true"),
                    HttpStatusCode.Created
                )
            }
            val key = MarginaliaIdempotencyKey.fromStableValue("  logical-operation-1  ")

            client.marginalia.books.startOver("book-1", key)
            client.marginalia.books.startOver(
                "book-1",
                key,
                ReadingSessionFinalization(notes = "Final notes")
            )

            assertEquals(
                listOf("logical-operation-1", "logical-operation-1"),
                requests.map { it.headers["Idempotency-Key"] }
            )
            assertEquals(emptySet<String>(), requests[0].payload().keys)
            assertEquals("Final notes", requests[1].payload().string("notes"))
            assertThrows(IllegalArgumentException::class.java) {
                MarginaliaIdempotencyKey.fromStableValue(" ")
            }
            assertThrows(IllegalArgumentException::class.java) {
                MarginaliaIdempotencyKey.fromStableValue("x".repeat(129))
            }
            assertThrows(IllegalArgumentException::class.java) {
                MarginaliaIdempotencyKey.fromStableValue("bad\nkey")
            }
            val conflict =
                assertThrows(SplClientException.ReadingSessionLifecycleRejected::class.java) {
                    runBlocking {
                        authenticatedClient {
                            jsonResponse(
                                """{"code":"INVALID_REQUEST"}""",
                                HttpStatusCode.Conflict
                            )
                        }.marginalia.books.startOver(
                            "book-1",
                            key,
                            ReadingSessionFinalization(notes = "No active Session")
                        )
                    }
                }
            assertEquals(ReadingSessionLifecycleRejection.INVALID_REQUEST, conflict.reason)
            Unit
        }

    @Test
    fun `lifecycle authorization resource validation and conflicts remain distinct`() {
        assertThrows(SplClientException.AuthenticationRejected::class.java) {
            runBlocking { failingOpen(HttpStatusCode.Unauthorized, "{}") }
        }
        assertEquals(
            ReadingSessionLifecycleRejection.PERMISSION_DENIED,
            lifecycleFailure(HttpStatusCode.Forbidden, "{}").reason
        )
        assertEquals(
            ReadingSessionLifecycleRejection.RESOURCE_NOT_FOUND,
            lifecycleFailure(HttpStatusCode.NotFound, "{}").reason
        )
        val validation = lifecycleFailure(HttpStatusCode.BadRequest, """{"name":["invalid"]}""")
        assertEquals(ReadingSessionLifecycleRejection.VALIDATION, validation.reason)
        assertEquals(setOf(ReadingSessionMutationField.NAME), validation.fields)
        assertEquals(
            ReadingSessionLifecycleRejection.INVALID_REQUEST,
            lifecycleFailure(HttpStatusCode.Conflict, """{"code":"INVALID_REQUEST"}""").reason
        )
    }

    private fun lifecycleFailure(
        status: HttpStatusCode,
        body: String
    ): SplClientException.ReadingSessionLifecycleRejected =
        assertThrows(SplClientException.ReadingSessionLifecycleRejected::class.java) {
            runBlocking { failingOpen(status, body) }
        }

    private suspend fun failingOpen(status: HttpStatusCode, body: String) {
        authenticatedClient { jsonResponse(body, status) }
            .marginalia.books.openSession("book-1")
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

    private suspend fun HttpRequestData.payload(): JsonObject {
        val text = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        return Json.parseToJsonElement(text) as JsonObject
    }

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.objectValue(name: String) = getValue(name) as JsonObject

    private companion object {
        const val BOOK = """{"id":"book-1","title":"Book","cover_url":null,"can_open":true}"""
        const val SESSION =
            """{"id":"session-1","name":"","notes":"","status":"active",""" +
                """"started_at":"2026-08-01T00:00:00Z","closed_at":null,""" +
                """"updated_at":"2026-08-02T00:00:00Z",""" +
                """"last_activity_at":"2026-08-02T00:00:00Z",""" +
                """"annotation_count":2,"progress":null}"""
        const val ANNOTATIONS =
            """[{"id":"a-1","client_id":"c-1","kind":"highlight",""" +
                """"location":{"cfi":"epubcfi(/6/2)","location_label":"Chapter 1"},""" +
                """"body":{"text":"Text","prefix":"Before","suffix":"After",""" +
                """"color":"orange","note":"Note"},"created_at":"2026-08-01T00:00:00Z",""" +
                """"updated_at":"2026-08-01T00:00:00Z"},{"id":"a-2","client_id":"c-2",""" +
                """"kind":"bookmark","location":{"cfi":"epubcfi(/6/4)",""" +
                """"location_label":"Chapter 2"},"created_at":"2026-08-01T00:00:00Z",""" +
                """"updated_at":"2026-08-01T00:00:00Z"}]"""
        const val BOOTSTRAP =
            """{"created":false,"context":{"book":$BOOK},"session":$SESSION,""" +
                """"annotations":$ANNOTATIONS,"closed_sessions":{"count":4,"next":"next",""" +
                """"previous":null,"results":[$SESSION]}}"""
        const val DETAIL = """{"context":{"book":$BOOK},"session":$SESSION}"""
    }
}
