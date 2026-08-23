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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfMutationsClientTest {
    @Test
    fun `create injects personal ownership and returns the shared Shelf model`() = runBlocking {
        var request: HttpRequestData? = null
        val shelf = authenticatedClient { captured ->
            request = captured
            jsonResponse(SHELF_DETAIL, HttpStatusCode.Created)
        }.shelves.create(
            CreatePersonalShelfInput("  Later  ", "To read", ShelfVisibility.LISTED)
        )

        val payload = requireNotNull(request).requirePayload()
        assertEquals("Later", payload.string("name"))
        assertEquals("To read", payload.string("description"))
        assertEquals("listed", payload.string("visibility"))
        assertEquals("user", payload.string("owner_type"))
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/shelves/", request.url.encodedPath)
        assertEquals("Bearer spl_secret", request.headers[HttpHeaders.Authorization])
        assertNull(request.headers["Idempotency-Key"])
        assertEquals("shelf 1", shelf.id)
    }

    @Test
    fun `create and update validate bounded names and nonempty updates`() {
        assertThrows(IllegalArgumentException::class.java) { CreatePersonalShelfInput(" ") }
        assertThrows(IllegalArgumentException::class.java) {
            CreatePersonalShelfInput("x".repeat(256))
        }
        assertThrows(IllegalArgumentException::class.java) { UpdatePersonalShelfInput(name = "") }
        assertThrows(IllegalArgumentException::class.java) { UpdatePersonalShelfInput() }
    }

    @Test
    fun `update sends only supplied mutable fields and never owner fields`() = runBlocking {
        var request: HttpRequestData? = null
        authenticatedClient { captured ->
            request = captured
            jsonResponse(SHELF_DETAIL)
        }.shelves.update("shelf 1", UpdatePersonalShelfInput(description = "Changed"))

        val payload = requireNotNull(request).requirePayload()
        assertEquals(setOf("description"), payload.keys)
        assertEquals("Changed", payload.string("description"))
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/v1/shelves/shelf%201/", request.url.encodedPath)
    }

    @Test
    fun `delete and remove require 204 and send no idempotency key`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient { request ->
            requests += request
            jsonResponse("", HttpStatusCode.NoContent)
        }

        client.shelves.delete("shelf-1")
        client.shelves.removeItem("shelf-1", "item 1")

        assertEquals(HttpMethod.Delete, requests[0].method)
        assertEquals("/api/v1/shelves/shelf-1/", requests[0].url.encodedPath)
        assertEquals("/api/v1/shelves/shelf-1/items/item%201/", requests[1].url.encodedPath)
        requests.forEach { request ->
            assertEquals("Bearer spl_secret", request.headers[HttpHeaders.Authorization])
            assertNull(request.headers["Idempotency-Key"])
        }
    }

    @Test
    fun `add item omits position normally and sends zero-based direct position when supplied`() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client = authenticatedClient { request ->
                requests += request
                jsonResponse(SHELF_ITEM, HttpStatusCode.Created)
            }

            val first = client.shelves.addItem("shelf-1", AddShelfItemInput("book-1"))
            client.shelves.addItem("shelf-1", AddShelfItemInput("book-1", position = 0))

            assertEquals("item-1", first.id)
            assertEquals(setOf("book"), requests[0].requirePayload().keys)
            assertEquals("book-1", requests[0].requirePayload().string("book"))
            assertEquals(0, requests[1].requirePayload()["position"]?.jsonPrimitive?.int)
            assertNull(requests[0].headers["Idempotency-Key"])
        }

    @Test
    fun `duplicate Book is normalized from structured add field failure`() {
        val failure = assertThrows(SplClientException.ShelfMutationRejected::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(BAD_BOOK, HttpStatusCode.BadRequest) }
                    .shelves.addItem("shelf-1", AddShelfItemInput("book-1"))
            }
        }

        assertEquals(ShelfMutationRejection.DUPLICATE_BOOK, failure.reason)
        assertEquals(setOf(ShelfMutationField.BOOK), failure.fields)
    }

    @Test
    fun `relative move and direct position use mutually exclusive payloads`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient { request ->
            requests += request
            jsonResponse(SHELF_ITEM)
        }

        ShelfItemMove.entries.forEach { client.shelves.moveItem("shelf-1", "item-1", it) }
        client.shelves.setItemPosition("shelf-1", "item-1", 7)

        assertEquals(
            listOf("up", "down"),
            requests.take(2).map {
                it.requirePayload().string("move")
            }
        )
        assertTrue(requests.take(2).all { "position" !in it.requirePayload() })
        assertEquals(setOf("position"), requests[2].requirePayload().keys)
        assertEquals(7, requests[2].requirePayload()["position"]?.jsonPrimitive?.int)
        assertEquals(HttpMethod.Patch, requests[2].method)
    }

    @Test
    fun `direct-position restriction and invalid move are distinct structured errors`() {
        val position = mutationFailure(BAD_POSITION, directPosition = true)
        val move = mutationFailure(BAD_MOVE, directPosition = false)

        assertEquals(ShelfMutationRejection.DIRECT_POSITION_UNAVAILABLE, position.reason)
        assertEquals(setOf(ShelfMutationField.POSITION), position.fields)
        assertEquals(ShelfMutationRejection.INVALID_MOVE, move.reason)
        assertEquals(setOf(ShelfMutationField.MOVE), move.fields)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(SHELF_ITEM) }
                    .shelves.setItemPosition("shelf-1", "item-1", -1)
            }
        }
    }

    @Test
    fun `authorization missing resource validation and malformed success stay distinct`() {
        assertThrows(SplClientException.AuthenticationRejected::class.java) {
            runBlocking { failingDelete(HttpStatusCode.Unauthorized) }
        }
        assertShelfRejection(HttpStatusCode.Forbidden, ShelfMutationRejection.NOT_AUTHORIZED)
        assertShelfRejection(HttpStatusCode.NotFound, ShelfMutationRejection.RESOURCE_NOT_FOUND)

        val validation = assertThrows(SplClientException.ShelfMutationRejected::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(BAD_NAME, HttpStatusCode.BadRequest) }
                    .shelves.create(CreatePersonalShelfInput("Valid"))
            }
        }
        assertEquals(ShelfMutationRejection.VALIDATION, validation.reason)
        assertEquals(setOf(ShelfMutationField.NAME), validation.fields)
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse("{}", HttpStatusCode.Created) }
                    .shelves.create(CreatePersonalShelfInput("Valid"))
            }
        }
    }

    @Test
    fun `Group or shared ownership is left to ordinary server authorization`() {
        var request: HttpRequestData? = null
        val failure = assertThrows(SplClientException.ShelfMutationRejected::class.java) {
            runBlocking {
                authenticatedClient { captured ->
                    request = captured
                    jsonResponse("{}", HttpStatusCode.Forbidden)
                }.shelves.update(
                    "group-shelf",
                    UpdatePersonalShelfInput(name = "Still read-only")
                )
            }
        }

        assertEquals(ShelfMutationRejection.NOT_AUTHORIZED, failure.reason)
        assertEquals(HttpMethod.Patch, request?.method)
        assertEquals("/api/v1/shelves/group-shelf/", request?.url?.encodedPath)
    }

    private fun mutationFailure(
        responseBody: String,
        directPosition: Boolean
    ): SplClientException.ShelfMutationRejected =
        assertThrows(SplClientException.ShelfMutationRejected::class.java) {
            runBlocking {
                val shelves = authenticatedClient {
                    jsonResponse(responseBody, HttpStatusCode.BadRequest)
                }.shelves
                if (directPosition) {
                    shelves.setItemPosition("shelf-1", "item-1", 2)
                } else {
                    shelves.moveItem("shelf-1", "item-1", ShelfItemMove.UP)
                }
            }
        }

    private fun assertShelfRejection(status: HttpStatusCode, reason: ShelfMutationRejection) {
        val failure = assertThrows(SplClientException.ShelfMutationRejected::class.java) {
            runBlocking { failingDelete(status) }
        }
        assertEquals(reason, failure.reason)
        assertTrue(failure.fields.isEmpty())
    }

    private suspend fun failingDelete(status: HttpStatusCode) {
        authenticatedClient { jsonResponse("{}", status) }.shelves.delete("shelf-1")
    }

    private fun authenticatedClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AuthenticatedSecondPassClient {
        val root = KtorSecondPassClient(HttpClient(MockEngine(handler)) { expectSuccess = false })
        return root.authenticated(
            "https://library.example/api/v1/",
            BearerCredential.restore("spl_secret")
        )
    }

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun HttpRequestData.requirePayload(): JsonObject = Json.parseToJsonElement(
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
    ).let { it as JsonObject }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private companion object {
        const val BAD_BOOK = """{"book":["Already on shelf."]}"""
        const val BAD_POSITION = """{"position":["Direct positioning is unavailable."]}"""
        const val BAD_MOVE = """{"move":["Invalid move."]}"""
        const val BAD_NAME = """{"name":["Invalid."]}"""
    }
}
