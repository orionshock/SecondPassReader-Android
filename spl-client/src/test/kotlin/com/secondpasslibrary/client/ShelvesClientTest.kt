package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelvesClientTest {
    @Test
    fun `list maps all scopes filters pagination ordering and bearer internally`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient { request ->
            requests += request
            jsonResponse(EMPTY_PAGE)
        }

        ShelfScope.entries.forEach { scope ->
            client.shelves.list(
                ShelfListOptions(
                    scope = scope,
                    ownerGroupId = "group 1",
                    bookId = "book-1",
                    ordering = ShelfOrdering.ITEM_COUNT_DESCENDING,
                    page = 2,
                    pageSize = 50
                )
            )
        }

        assertEquals(
            ShelfScope.entries.map {
                it.queryValue
            },
            requests.map { it.parameter("scope") }
        )
        requests.forEach { request ->
            assertEquals("group 1", request.parameter("owner_group"))
            assertEquals("book-1", request.parameter("book"))
            assertEquals("-item_count", request.parameter("ordering"))
            assertEquals("2", request.parameter("page"))
            assertEquals("50", request.parameter("page_size"))
            assertEquals("Bearer spl_secret", request.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `list maps ownership visibility matching and preview presence`() = runBlocking {
        val page = authenticatedClient { jsonResponse(SHELF_PAGE) }.shelves.list(
            ShelfListOptions(page = 3, pageSize = 10)
        )

        assertEquals(3, page.totalCount)
        assertEquals(3, page.page)
        assertEquals(10, page.pageSize)
        assertTrue(page.hasNextPage)
        assertFalse(page.hasPreviousPage)
        val personal = page.shelves[0]
        assertEquals(ShelfOwner.User("profile-1", "reader"), personal.owner)
        assertEquals(ShelfVisibility.PRIVATE, personal.visibility)
        assertEquals("item-7", personal.matchedItemId)
        assertTrue(personal.canEdit)
        assertEquals(ShelfUser("profile-1", "reader"), personal.createdBy)
        assertNull(personal.previewBooks)
        val publicGroup = page.shelves[1]
        assertEquals(ShelfOwner.Group("group-1", "Common Room", true), publicGroup.owner)
        assertEquals(ShelfVisibility.LISTED, publicGroup.visibility)
        assertEquals(emptyList<ShelfPreviewBook>(), publicGroup.previewBooks)
        val preview = page.shelves[2].previewBooks?.single()
        assertEquals("book-1", preview?.id)
        assertEquals("https://cdn.example/cover.webp", preview?.cover?.url)
    }

    @Test
    fun `preview limit zero disables previews and positive requests bounded previews`() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client = authenticatedClient { request ->
                requests += request
                jsonResponse(
                    if (request.url.encodedPath.endsWith("shelves/")) EMPTY_PAGE else SHELF_DETAIL
                )
            }

            client.shelves.list(ShelfListOptions(previewLimit = 0))
            client.shelves.list(ShelfListOptions(previewLimit = 24))
            client.shelves.get("shelf 1", ShelfDetailOptions(previewLimit = 3))

            assertNull(requests[0].parameter("include_preview_books"))
            assertEquals("true", requests[1].parameter("include_preview_books"))
            assertEquals("24", requests[1].parameter("preview_limit"))
            assertEquals("/api/v1/shelves/shelf%201/", requests[2].url.encodedPath)
            assertEquals("3", requests[2].parameter("preview_limit"))
        }

    @Test
    fun `normal items reuse compact books and retain non-contiguous stored positions`() =
        runBlocking {
            var request: HttpRequestData? = null
            val page = authenticatedClient { captured ->
                request = captured
                jsonResponse(SHELF_ITEMS_PAGE)
            }.shelves.listItems(
                "shelf 1",
                ShelfItemListOptions(ShelfItemOrdering.AUTHOR_DESCENDING, page = 2, pageSize = 25)
            )

            assertEquals(listOf(2, 9), page.results.map(ShelfItem::position))
            assertEquals(listOf("book-1", "book-2"), page.results.map { it.book.id })
            assertEquals("Shelf Book 1", page.results.first().book.title)
            assertEquals("adder", page.results.first().addedBy?.username)
            assertEquals("/api/v1/shelves/shelf%201/items/", request?.url?.encodedPath)
            assertEquals("-author", request?.parameter("ordering"))
            assertEquals("Bearer spl_secret", request?.headers?.get(HttpHeaders.Authorization))
        }

    @Test
    fun `editor projection separates available books from safe unavailable placeholders`() =
        runBlocking {
            var request: HttpRequestData? = null
            val page = authenticatedClient { captured ->
                request = captured
                jsonResponse(SHELF_EDITOR_PAGE)
            }.shelves.listEditorItems("shelf-1", ShelfEditorListOptions(page = 1, pageSize = 100))

            assertEquals(2, page.totalCount)
            assertEquals(1, page.visibleItemCount)
            assertEquals(1, page.unavailableItemCount)
            val available = page.results[0] as ShelfEditorItem.Available
            assertEquals("book-1", available.book.id)
            val unavailable = page.results[1] as ShelfEditorItem.Unavailable
            assertEquals("item-hidden", unavailable.id)
            assertEquals("shelf-1", unavailable.shelfId)
            assertEquals(8, unavailable.position)
            assertEquals("adder", unavailable.addedBy?.username)
            assertEquals("edit", request?.parameter("view"))
            assertEquals("position", request?.parameter("ordering"))
        }

    @Test
    fun `all normal item ordering values are closed and mapped`() = runBlocking {
        val values = mutableListOf<String?>()
        val client = authenticatedClient { request ->
            values += request.parameter("ordering")
            jsonResponse(EMPTY_PAGE)
        }

        ShelfItemOrdering.entries.forEach { ordering ->
            client.shelves.listItems("shelf", ShelfItemListOptions(ordering))
        }

        assertEquals(
            listOf("position", "-position", "title", "-title", "author", "-author"),
            values
        )
    }

    @Test
    fun `shelf inputs enforce page and preview bounds before transport`() {
        assertThrows(IllegalArgumentException::class.java) { ShelfListOptions(pageSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { ShelfListOptions(pageSize = 201) }
        assertThrows(IllegalArgumentException::class.java) { ShelfListOptions(previewLimit = -1) }
        assertThrows(IllegalArgumentException::class.java) { ShelfDetailOptions(previewLimit = 25) }
        assertThrows(IllegalArgumentException::class.java) { ShelfItemListOptions(page = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            ShelfEditorListOptions(pageSize = 201)
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { authenticatedClient { jsonResponse(EMPTY_PAGE) }.shelves.get(" ") }
        }
    }

    @Test
    fun `malformed required shelf and item fields are rejected`() {
        val malformedShelf = authenticatedClient {
            jsonResponse(
                SHELF_DETAIL.replace("\"owner_type\":\"user\"", "\"owner_type\":\"other\"")
            )
        }
        val malformedItem = authenticatedClient {
            jsonResponse(SHELF_ITEMS_PAGE.replaceFirst(COMPACT_BOOK, "null"))
        }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { malformedShelf.shelves.get("shelf-1") }
        }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { malformedItem.shelves.listItems("shelf-1") }
        }
    }

    @Test
    fun `editor availability discriminator is required and must match Book visibility`() {
        val missingFlag = authenticatedClient {
            jsonResponse(SHELF_EDITOR_PAGE.replaceFirst("\"unavailable\":false,", ""))
        }
        val leakedBook = authenticatedClient {
            jsonResponse(
                SHELF_EDITOR_PAGE.replaceFirst(
                    "\"unavailable\":false",
                    "\"unavailable\":true"
                )
            )
        }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { missingFlag.shelves.listEditorItems("shelf-1") }
        }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { leakedBook.shelves.listEditorItems("shelf-1") }
        }
    }

    private fun authenticatedClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AuthenticatedSecondPassClient {
        val root = KtorSecondPassClient(HttpClient(MockEngine(handler)) { expectSuccess = false })
        return root.authenticated(
            "https://library.example",
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

    private fun HttpRequestData.parameter(name: String): String? = url.parameters[name]
}
