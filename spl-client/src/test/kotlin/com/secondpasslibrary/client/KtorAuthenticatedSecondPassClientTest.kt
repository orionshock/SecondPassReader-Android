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

class KtorAuthenticatedSecondPassClientTest {
    @Test
    fun `recent reading sends bounded query and preserves mapped server order`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(
                """{
                    "results": [
                        {
                            "id": "session-closed",
                            "name": "First from server",
                            "status": "closed",
                            "last_activity_at": "2026-08-16T12:00:00Z",
                            "book": {
                                "id": "book-2",
                                "title": "Second Book",
                                "cover_url": "https://assets.example/covers/book-2.webp",
                                "can_open": true
                            },
                            "progress": {
                                "cfi": "epubcfi(/6/4)",
                                "location_label": "Chapter 2",
                                "updated_at": "2026-08-16T11:00:00Z"
                            }
                        },
                        {
                            "id": "session-active",
                            "name": "",
                            "status": "active",
                            "last_activity_at": "2026-08-15T12:00:00Z",
                            "book": {
                                "id": "book-1",
                                "title": "First Book",
                                "cover_url": null,
                                "can_open": false
                            },
                            "progress": null
                        }
                    ]
                }"""
            )
        }

        val result = client.recentReading(RecentReadingOptions(limit = 10, includeClosed = true))

        assertEquals(listOf("session-closed", "session-active"), result.map { it.sessionId })
        assertEquals(ReadingSessionStatus.CLOSED, result[0].status)
        assertEquals(ReadingSessionStatus.ACTIVE, result[1].status)
        assertEquals("", result[1].sessionName)
        assertEquals("Chapter 2", result[0].progress?.locationLabel)
        assertNull(result[1].progress)
        assertEquals(
            "https://assets.example/covers/book-2.webp",
            result[0].book.cover?.url
        )
        assertNull(result[1].book.cover)
        assertEquals("10", request?.url?.parameters?.get("limit"))
        assertEquals("true", request?.url?.parameters?.get("include_closed"))
        assertEquals("Bearer spl_secret", request?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `recent reading rejects limits outside one through fifty before transport`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecentReadingOptions(limit = 0, includeClosed = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecentReadingOptions(limit = 51, includeClosed = false)
        }
    }

    @Test
    fun `shelf list sends home query and distinguishes omitted previews`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(SHELF_PAGE)
        }

        val page =
            client.shelves.list(
                ShelfListOptions(
                    page = 2,
                    pageSize = 6,
                    ordering = ShelfOrdering.ITEM_COUNT_DESCENDING,
                    previewLimit = 3
                )
            )

        assertEquals(2, page.totalCount)
        assertTrue(page.hasNextPage)
        assertFalse(page.hasPreviousPage)
        assertEquals(4, page.shelves[0].itemCount)
        assertTrue(page.shelves[0].owner is ShelfOwner.User)
        assertNull(page.shelves[0].previewBooks)
        assertTrue(page.shelves[1].owner is ShelfOwner.Group)
        assertEquals(
            "https://cdn.example/book.png",
            page.shelves[1].previewBooks?.single()?.cover?.url
        )
        assertEquals("2", request?.url?.parameters?.get("page"))
        assertEquals("6", request?.url?.parameters?.get("page_size"))
        assertEquals("-item_count", request?.url?.parameters?.get("ordering"))
        assertEquals("true", request?.url?.parameters?.get("include_preview_books"))
        assertEquals("3", request?.url?.parameters?.get("preview_limit"))
        assertEquals("Bearer spl_secret", request?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `supported shelf orderings map to their bounded server values`() = runBlocking {
        val values = mutableListOf<String?>()
        val client = authenticatedClient { request ->
            values += request.url.parameters["ordering"]
            jsonResponse("""{"count":0,"next":null,"previous":null,"results":[]}""")
        }

        ShelfOrdering.entries.forEach { ordering ->
            client.shelves.list(ShelfListOptions(ordering = ordering))
        }

        assertEquals(listOf("name", "-name", "item_count", "-item_count"), values)
    }

    @Test
    fun `shelf list rejects a missing item count`() {
        val client = authenticatedClient {
            jsonResponse(
                """{
                    "count": 1,
                    "results": [{
                        "id": "shelf-1",
                        "name": "Shelf",
                        "owner_type": "user",
                        "owner_user": {"profile_id": "profile-1"},
                        "visibility": "private",
                        "can_edit": true
                    }]
                }"""
            )
        }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { client.shelves.list() }
        }
    }

    private fun authenticatedClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AuthenticatedSecondPassClient {
        val root =
            KtorSecondPassClient(
                HttpClient(MockEngine { request -> handler(request) }) {
                    expectSuccess = false
                }
            )
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

    private companion object {
        const val SHELF_PAGE =
            """{
                "count": 2,
                "next": "https://library.example/api/v1/shelves/?page=2",
                "previous": null,
                "results": [
                    {
                        "id": "shelf-user",
                        "name": "Reading now",
                        "description": null,
                        "owner_type": "user",
                        "owner_user": {
                            "profile_id": "profile-1",
                            "username": "reader"
                        },
                        "visibility": "private",
                        "created_at": "2026-08-01T00:00:00Z",
                        "updated_at": "2026-08-02T00:00:00Z",
                        "item_count": 4,
                        "can_edit": true
                    },
                    {
                        "id": "shelf-group",
                        "name": "Shared picks",
                        "description": "Group shelf",
                        "owner_type": "group",
                        "owner_group": {
                            "id": "group-1",
                            "name": "Readers",
                            "is_public_group": false
                        },
                        "visibility": "listed",
                        "created_at": "2026-08-01T00:00:00Z",
                        "updated_at": "2026-08-02T00:00:00Z",
                        "item_count": 1,
                        "can_edit": false,
                        "preview_books": [
                            {
                                "id": "book-1",
                                "title": "Book",
                                "cover_url": "https://cdn.example/book.png"
                            }
                        ]
                    }
                ]
            }"""
    }
}
