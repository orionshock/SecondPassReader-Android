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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGroupsClientTest {
    @Test
    fun `group summaries preserve server order and public identity`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(GROUP_PAGE)
        }

        val page =
            client.library.groups.listGroups(
                LibraryGroupListOptions(page = 2, pageSize = 200)
            )

        assertEquals("/api/v1/library/groups/", request?.url?.encodedPath)
        assertEquals("name", request?.url?.parameters?.get("ordering"))
        assertEquals("2", request?.url?.parameters?.get("page"))
        assertEquals("200", request?.url?.parameters?.get("page_size"))
        assertEquals(listOf("Common Room", "Private Group"), page.results.map { it.name })
        assertTrue(page.results.first().isPublicGroup)
        assertFalse(page.results.last().isPublicGroup)
        assertTrue(page.hasPrevious)
    }

    @Test
    fun `group Books browse uses shared options and encoded scoped path`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(EMPTY_PAGE)
        }

        client.library.books.list(
            LibraryScope.Group("group/one"),
            BookListOptions(
                q = "title only",
                authorId = "author-id",
                seriesId = "series-id",
                ordering = BookOrdering.AUTHOR_DESCENDING,
                page = 3,
                pageSize = 50
            )
        )

        assertEquals("/api/v1/library/groups/group%2Fone/books/", request?.url?.encodedPath)
        assertEquals("title only", request?.url?.parameters?.get("q"))
        assertEquals("author-id", request?.url?.parameters?.get("author"))
        assertEquals("series-id", request?.url?.parameters?.get("series"))
        assertEquals("-author", request?.url?.parameters?.get("ordering"))
        assertEquals("Bearer spl_secret", request?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `group broad search uses scoped search endpoint and shared options`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(EMPTY_PAGE)
        }

        client.library.books.search(
            LibraryScope.Group("group/one"),
            LibrarySearchOptions(q = "author or title", ordering = LibrarySearchOrdering.AUTHOR)
        )

        assertEquals("/api/v1/library/groups/group%2Fone/search", request?.url?.encodedPath)
        assertEquals("author or title", request?.url?.parameters?.get("q"))
        assertEquals("author", request?.url?.parameters?.get("ordering"))
    }

    @Test
    fun `group identifiers paging and required fields are validated`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                LibraryScope.Group(" ")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LibraryGroupListOptions(pageSize = 201)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BookListOptions(authorId = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BookListOptions(seriesId = " ")
        }
        val malformed = authenticatedClient {
            jsonResponse("""{"count":1,"results":[{"id":"group","name":"Group"}]}""")
        }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { malformed.library.groups.listGroups() }
        }
    }

    private fun authenticatedClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AuthenticatedSecondPassClient {
        val root =
            KtorSecondPassClient(
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
        const val GROUP_PAGE =
            """{
                "count":2,"next":null,"previous":"https://library.example/previous",
                "results":[
                    {"id":"public","name":"Common Room","is_public_group":true},
                    {"id":"private","name":"Private Group","is_public_group":false}
                ]
            }"""
    }
}
