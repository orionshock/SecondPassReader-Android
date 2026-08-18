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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAxesClientTest {
    @Test
    fun `authors map ordered nullable preview states and list query`() = runBlocking {
        var request: HttpRequestData? = null
        val page = authenticatedClient {
            request = it
            jsonResponse(AUTHOR_PAGE)
        }.library.authors.list(
            options = AuthorListOptions(
                q = "Le Guin / science fiction",
                ordering = AuthorOrdering.BOOK_COUNT_DESCENDING,
                page = 2,
                pageSize = 40,
                previewLimit = 6
            )
        )

        assertEquals("/api/v1/library/authors/", request?.url?.encodedPath)
        assertEquals("Le Guin / science fiction", request?.url?.parameters?.get("q"))
        assertFalse(request?.url.toString().contains(' '))
        assertEquals("-book_count", request?.url?.parameters?.get("ordering"))
        assertEquals("true", request?.url?.parameters?.get("include_preview_books"))
        assertEquals("6", request?.url?.parameters?.get("preview_limit"))
        assertEquals(3, page.totalCount)
        assertEquals(2, page.page)
        assertEquals(40, page.pageSize)
        assertTrue(page.hasNext)
        assertTrue(page.hasPrevious)
        assertEquals(listOf("Omitted", "Empty", "Populated"), page.results.map { it.name })
        assertNull(page.results[0].previewBooks)
        assertEquals(emptyList<LibraryPreviewBook>(), page.results[1].previewBooks)
        assertEquals("Biography", page.results[2].biography)
        assertEquals(12, page.results[2].bookCount)
        assertEquals(
            "https://covers.example/book.webp",
            page.results[2].previewBooks?.single()?.cover?.url
        )
    }

    @Test
    fun `series map summary counts previews and pagination`() = runBlocking {
        var request: HttpRequestData? = null
        val page = authenticatedClient {
            request = it
            jsonResponse(SERIES_PAGE)
        }.library.series.list(options = SeriesListOptions(q = "cycle", previewLimit = 3))

        assertEquals("/api/v1/library/series/", request?.url?.encodedPath)
        assertEquals("name", request?.url?.parameters?.get("ordering"))
        assertEquals("cycle", request?.url?.parameters?.get("q"))
        assertEquals("3", request?.url?.parameters?.get("preview_limit"))
        assertEquals("A summary", page.results.single().summary)
        assertEquals(4, page.results.single().bookCount)
        assertNull(page.results.single().previewBooks?.single()?.cover)
        assertFalse(page.hasNext)
        assertFalse(page.hasPrevious)
    }

    @Test
    fun `detail operations encode identifiers and preserve preview absence`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient {
            requests += it
            if (it.url.encodedPath.contains("authors")) {
                jsonResponse(AUTHOR_DETAIL)
            } else {
                jsonResponse(SERIES_DETAIL)
            }
        }

        val author = client.library.authors.getAuthor("author/one")
        val series =
            client.library.series.getSeries(
                "series/one",
                LibraryEntityDetailOptions(previewLimit = 4)
            )

        assertEquals("/api/v1/library/authors/author%2Fone/", requests[0].url.encodedPath)
        assertFalse(requests[0].url.parameters.contains("include_preview_books"))
        assertNull(author.previewBooks)
        assertEquals("/api/v1/library/series/series%2Fone/", requests[1].url.encodedPath)
        assertEquals("true", requests[1].url.parameters["include_preview_books"])
        assertEquals("4", requests[1].url.parameters["preview_limit"])
        assertEquals(emptyList<LibraryPreviewBook>(), series.previewBooks)
    }

    @Test
    fun `group scoped axes select encoded endpoints and keep bearer internal`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = authenticatedClient {
            requests += it
            if (it.url.encodedPath.endsWith("authors/")) {
                jsonResponse(EMPTY_PAGE)
            } else {
                jsonResponse(SERIES_PAGE)
            }
        }

        client.library.authors.list(
            LibraryScope.Group("group/one"),
            AuthorListOptions(q = "author")
        )
        client.library.series.list(
            LibraryScope.Group("group/one"),
            SeriesListOptions(ordering = SeriesOrdering.NAME_DESCENDING)
        )

        assertEquals("/api/v1/library/groups/group%2Fone/authors/", requests[0].url.encodedPath)
        assertEquals("author", requests[0].url.parameters["q"])
        assertEquals("/api/v1/library/groups/group%2Fone/series/", requests[1].url.encodedPath)
        assertEquals("-name", requests[1].url.parameters["ordering"])
        requests.forEach {
            assertEquals("Bearer spl_secret", it.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `axis ordering and bounds are closed and validated`() {
        assertEquals(
            listOf("name", "-name", "book_count", "-book_count"),
            AuthorOrdering.entries.map { it.queryValue }
        )
        assertEquals(
            listOf("name", "-name", "book_count", "-book_count"),
            SeriesOrdering.entries.map { it.queryValue }
        )
        assertThrows(IllegalArgumentException::class.java) { AuthorListOptions(page = 0) }
        assertThrows(IllegalArgumentException::class.java) { SeriesListOptions(pageSize = 201) }
        assertThrows(IllegalArgumentException::class.java) { AuthorListOptions(previewLimit = -1) }
        assertThrows(IllegalArgumentException::class.java) { SeriesListOptions(previewLimit = 25) }
        assertThrows(IllegalArgumentException::class.java) {
            LibraryEntityDetailOptions(previewLimit = 25)
        }
    }

    @Test
    fun `malformed required author series and preview fields are rejected`() {
        val malformedPayloads = listOf(
            """{"count":1,"results":[{"id":"a","name":"A","sort_name":"A","book_count":1}]}""",
            """{"count":1,"results":[{"id":"s","name":"S","sort_name":"S","summary":"","book_count":-1}]}""",
            """{"id":"a","name":"A","sort_name":"A","biography":"","book_count":1,"preview_books":[{"id":"b"}]}"""
        )

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(malformedPayloads[0]) }
                    .library.authors.list()
            }
        }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(malformedPayloads[1]) }
                    .library.series.list()
            }
        }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(malformedPayloads[2]) }
                    .library.authors.getAuthor("a")
            }
        }
    }

    @Test
    fun `identifiers and zero preview limit are validated before transport`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(AUTHOR_DETAIL) }
                    .library.authors.getAuthor(" ")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                authenticatedClient { jsonResponse(SERIES_DETAIL) }
                    .library.series.getSeries("")
            }
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
        const val AUTHOR_DETAIL =
            """{"id":"a","name":"Author","sort_name":"Author","biography":"Bio","book_count":2}"""
        const val SERIES_DETAIL =
            """{"id":"s","name":"Series","sort_name":"Series","summary":"Summary","book_count":2,"preview_books":[]}"""
        const val AUTHOR_PAGE =
            """{
                "count":3,"next":"https://library.example/next","previous":"https://library.example/previous",
                "results":[
                    {"id":"a1","name":"Omitted","sort_name":"Omitted","biography":"","book_count":1},
                    {"id":"a2","name":"Empty","sort_name":"Empty","biography":"","book_count":0,"preview_books":[]},
                    {"id":"a3","name":"Populated","sort_name":"Populated","biography":"Biography","book_count":12,
                     "preview_books":[{"id":"b1","title":"Book","cover_url":"https://covers.example/book.webp"}]}
                ]
            }"""
        const val SERIES_PAGE =
            """{
                "count":1,"next":null,"previous":null,
                "results":[{"id":"s1","name":"Cycle","sort_name":"Cycle","summary":"A summary","book_count":4,
                "preview_books":[{"id":"b1","title":"Book","cover_url":null}]}]
            }"""
    }
}
