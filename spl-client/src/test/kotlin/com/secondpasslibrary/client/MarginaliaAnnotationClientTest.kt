package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginaliaAnnotationClientTest {
    @Test
    fun `annotation read preserves server order nullable fields and all colors`() = runBlocking {
        var request: HttpRequestData? = null
        val colors = listOf("yellow", "green", "blue", "pink", "purple", "orange")
        val response = annotationCollection(
            listOf(bookmarkWire("bookmark")) + colors.mapIndexed(::highlightWire)
        )
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(response)
        }

        val annotations = client.marginalia.sessions.listAnnotations("session / 1")

        assertTrue(annotations.first() is MarginaliaAnnotation.Bookmark)
        assertEquals(
            MarginaliaHighlightColor.entries,
            annotations.drop(1).map { (it as MarginaliaAnnotation.Highlight).body.color }
        )
        val firstHighlight = annotations[1] as MarginaliaAnnotation.Highlight
        assertNull(firstHighlight.location.locationLabel)
        assertNull(firstHighlight.body.prefix)
        assertNull(firstHighlight.body.suffix)
        assertNull(firstHighlight.body.note)
        assertEquals(
            listOf("bookmark") + colors.mapIndexed { index, _ -> "highlight-$index" },
            annotations.map { it.clientId }
        )
        assertEquals(
            "/api/v1/marginalia/sessions/session%20%2F%201/annotations/",
            request?.url?.encodedPath
        )
    }

    @Test
    fun `malformed Bookmark body and missing Highlight body are protocol invalid`() {
        val bookmarkWithBody = bookmarkWire("bookmark").dropLast(1) + ",\"body\":{}}"
        val highlightWithoutBody =
            """{"id":"a","client_id":"highlight","kind":"highlight","location":{"location":"cfi","location_label":null},"created_at":"now","updated_at":"now"}"""

        assertMalformed(annotationCollection(listOf(bookmarkWithBody)))
        assertMalformed(annotationCollection(listOf(highlightWithoutBody)))
    }

    @Test
    fun `mixed synchronization maps Bookmark Highlight and Delete explicitly`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(annotationCollection(listOf(bookmarkWire("bookmark-client"))))
        }
        val operations = listOf(
            MarginaliaAnnotationOperation.Upsert(
                MarginaliaAnnotationDraft.Bookmark(
                    "bookmark-client",
                    MarginaliaAnnotationLocationInput(" bookmark-cfi ", null)
                )
            ),
            MarginaliaAnnotationOperation.Upsert(
                MarginaliaAnnotationDraft.Highlight(
                    "highlight-client",
                    MarginaliaAnnotationLocationInput("highlight-cfi", "Location"),
                    MarginaliaHighlightBodyInput(
                        text = "Selected text",
                        prefix = "Before",
                        suffix = "After",
                        color = MarginaliaHighlightColor.PURPLE,
                        note = "Note"
                    )
                )
            ),
            MarginaliaAnnotationOperation.Delete("deleted-client")
        )

        val authoritative = client.marginalia.sessions.synchronizeAnnotations(
            "session-1",
            operations
        )

        assertEquals("bookmark-client", authoritative.single().clientId)
        assertEquals("server-bookmark-client", authoritative.single().id)
        val wireOperations = requireNotNull(request).payload().array("operations")
        assertEquals(
            listOf("upsert", "upsert", "delete"),
            wireOperations.map {
                it.objectValue().string("action")
            }
        )
        val bookmark = wireOperations[0].objectValue().objectValue("annotation")
        assertEquals("bookmark", bookmark.string("kind"))
        assertTrue("body" !in bookmark)
        assertEquals(" bookmark-cfi ", bookmark.objectValue("location").string("location"))
        val highlight = wireOperations[1].objectValue().objectValue("annotation")
        assertEquals("purple", highlight.objectValue("body").string("color"))
        assertEquals("deleted-client", wireOperations[2].objectValue().string("client_id"))
    }

    @Test
    fun `historical Web CFI is preserved through annotation read and write mapping`() =
        runBlocking {
            val historical =
                "epubcfi(/6/34!/4[x9780451492128_EPUB-15]/2,/310/1:0,/314/1:17)"
            var request: HttpRequestData? = null
            val response = annotationCollection(
                listOf(
                    """{"id":"server-bookmark","client_id":"bookmark","kind":"bookmark",""" +
                        """"location":{"location":"$historical","location_label":null},""" +
                        """"created_at":"now","updated_at":"now"}"""
                )
            )
            val client = authenticatedClient { captured ->
                request = captured
                jsonResponse(response)
            }

            val read = client.marginalia.sessions.listAnnotations("session-1").single()
            assertEquals(historical, read.location.location)
            client.marginalia.sessions.synchronizeAnnotations(
                "session-1",
                listOf(
                    MarginaliaAnnotationOperation.Upsert(
                        MarginaliaAnnotationDraft.Bookmark(
                            "bookmark",
                            MarginaliaAnnotationLocationInput(historical)
                        )
                    )
                )
            )

            val annotation = requireNotNull(request).payload()
                .array("operations")[0].objectValue()
                .objectValue("annotation")
            assertEquals(historical, annotation.objectValue("location").string("location"))
        }

    @Test
    fun `batch validation rejects empty oversized and duplicate client IDs`() {
        val delete = MarginaliaAnnotationOperation.Delete("client")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                noTransportClient().marginalia.sessions.synchronizeAnnotations("s", emptyList())
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                noTransportClient().marginalia.sessions.synchronizeAnnotations(
                    "s",
                    List(MAX_ANNOTATION_BATCH_SIZE + 1) {
                        MarginaliaAnnotationOperation.Delete("client-$it")
                    }
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                noTransportClient().marginalia.sessions.synchronizeAnnotations(
                    "s",
                    listOf(delete, delete)
                )
            }
        }
    }

    @Test
    fun `draft validation enforces client location and Highlight bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaAnnotationOperation.Delete(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaAnnotationDraft.Bookmark(
                "x".repeat(256),
                MarginaliaAnnotationLocationInput("cfi")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaAnnotationLocationInput(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaAnnotationLocationInput("x".repeat(8 * 1024 + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaAnnotationLocationInput("cfi", "x".repeat(256))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaHighlightBodyInput(" ", color = MarginaliaHighlightColor.YELLOW)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaHighlightBodyInput(
                "x".repeat(64 * 1024 + 1),
                color = MarginaliaHighlightColor.YELLOW
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaHighlightBodyInput(
                "text",
                prefix = "x".repeat(501),
                color = MarginaliaHighlightColor.YELLOW
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaHighlightBodyInput(
                "text",
                suffix = "x".repeat(501),
                color = MarginaliaHighlightColor.YELLOW
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaHighlightBodyInput(
                "text",
                color = MarginaliaHighlightColor.YELLOW,
                note = "x".repeat(64 * 1024 + 1)
            )
        }
    }

    @Test
    fun `unknown delete is successful no-op and write failures are normalized`() = runBlocking {
        val empty = authenticatedClient { jsonResponse("""{"annotations":[]}""") }
            .marginalia.sessions.synchronizeAnnotations(
                "session-1",
                listOf(MarginaliaAnnotationOperation.Delete("unknown"))
            )
        assertTrue(empty.isEmpty())

        val closed =
            synchronizationFailure(HttpStatusCode.Conflict, """{"code":"SESSION_CLOSED"}""")
        val visibility = synchronizationFailure(
            HttpStatusCode.Forbidden,
            """{"code":"CURRENT_BOOK_ACCESS_REQUIRED"}"""
        )
        assertEquals(ReadingSessionLifecycleRejection.SESSION_CLOSED, closed.reason)
        assertEquals(
            ReadingSessionLifecycleRejection.CURRENT_BOOK_ACCESS_REQUIRED,
            visibility.reason
        )
    }

    private fun synchronizationFailure(
        status: HttpStatusCode,
        body: String
    ): SplClientException.ReadingSessionLifecycleRejected =
        assertThrows(SplClientException.ReadingSessionLifecycleRejected::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(body, status) }
                    .marginalia.sessions.synchronizeAnnotations(
                        "session-1",
                        listOf(MarginaliaAnnotationOperation.Delete("client"))
                    )
            }
        }

    private fun assertMalformed(body: String) {
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(body) }
                    .marginalia.sessions.listAnnotations("session-1")
            }
        }
    }

    private fun noTransportClient() = authenticatedClient { error("Transport must not run.") }

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

    private fun JsonObject.array(name: String) = getValue(name) as JsonArray
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.objectValue(name: String) = getValue(name) as JsonObject
    private fun kotlinx.serialization.json.JsonElement.objectValue() = this as JsonObject

    private fun annotationCollection(items: List<String>) =
        """{"annotations":[${items.joinToString(",")}]}"""

    private fun bookmarkWire(clientId: String) =
        """{"id":"server-$clientId","client_id":"$clientId","kind":"bookmark","location":{"location":"bookmark-cfi","location_label":null},"created_at":"now","updated_at":"now"}"""

    private fun highlightWire(index: Int, color: String) =
        """{"id":"server-$index","client_id":"highlight-$index","kind":"highlight","location":{"location":"cfi-$index","location_label":null},"body":{"text":"Text","prefix":null,"suffix":null,"color":"$color","note":null},"created_at":"now","updated_at":"now"}"""
}
