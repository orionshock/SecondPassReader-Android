package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTagsClientTest {
    @Test
    fun `global tags preserve order counts and query options`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(TAG_PAGE)
        }

        val page = client.library.tags.list(
            options = CatalogTagListOptions(
                q = "science fiction",
                ordering = CatalogTagOrdering.BOOK_COUNT_DESCENDING,
                page = 2,
                pageSize = 50
            )
        )

        assertEquals("/api/v1/library/tags/", request?.url?.encodedPath)
        assertEquals("science fiction", request?.url?.parameters?.get("q"))
        assertEquals("-book_count", request?.url?.parameters?.get("ordering"))
        assertEquals(listOf("Fiction", "History"), page.results.map { it.name })
        assertEquals(listOf(12, 4), page.results.map { it.bookCount })
        assertTrue(page.hasPrevious)
    }

    @Test
    fun `group tags and global detail keep endpoint topology internal`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient { request ->
            requests += request
            if (request.url.encodedPath.endsWith("/tag%2Fone/")) {
                jsonResponse(TAG_DETAIL)
            } else {
                jsonResponse(EMPTY_PAGE)
            }
        }

        client.library.tags.list(LibraryScope.Group("group/one"))
        val detail = client.library.tags.get("tag/one")

        assertEquals("/api/v1/library/groups/group%2Fone/tags/", requests[0].url.encodedPath)
        assertEquals("/api/v1/library/tags/tag%2Fone/", requests[1].url.encodedPath)
        assertEquals("fiction", detail.slug)
    }

    @Test
    fun `all tag orderings map to the documented wire values`() = runBlocking {
        val observed = mutableListOf<String?>()
        val client = authenticatedClient { request ->
            observed += request.url.parameters["ordering"]
            jsonResponse(EMPTY_PAGE)
        }

        CatalogTagOrdering.entries.forEach { ordering ->
            client.library.tags.list(options = CatalogTagListOptions(ordering = ordering))
        }

        assertEquals(listOf("name", "-name", "book_count", "-book_count"), observed)
    }

    @Test
    fun `malformed tag counts and identifiers are rejected`() {
        val malformed = authenticatedClient { jsonResponse(TAG_DETAIL.replace("12", "-1")) }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { malformed.library.tags.get("tag") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { malformed.library.tags.get(" ") }
        }
    }

    private fun authenticatedClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AuthenticatedSecondPassClient {
        val root = KtorSecondPassClient(
            HttpClient(MockEngine { request -> handler(request) }) { expectSuccess = false }
        )
        return root.authenticated(
            "https://library.example/api/v1/",
            BearerCredential.restore("spl_secret")
        )
    }

    private fun MockRequestHandleScope.jsonResponse(body: String): HttpResponseData =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        const val EMPTY_PAGE = """{"count":0,"next":null,"previous":null,"results":[]}"""
        const val TAG_DETAIL =
            """{"id":"tag","name":"Fiction","slug":"fiction","book_count":12}"""
        const val TAG_PAGE =
            """{
                "count":2,"next":null,"previous":"https://library.example/previous",
                "results":[
                    {"id":"one","name":"Fiction","slug":"fiction","book_count":12},
                    {"id":"two","name":"History","slug":"history","book_count":4}
                ]
            }"""
    }
}
