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
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBooksClientTest {
    @Test
    fun `book asset download uses authoritative reference bearer and streams bytes`() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client = authenticatedClient { request ->
                requests += request
                if (request.url.encodedPath.endsWith("/download/")) {
                    respond(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
                } else {
                    jsonResponse(BOOK_DETAIL)
                }
            }
            val detail = client.library.books.getBook("book-1")
            val destination = ByteArrayOutputStream()

            client.library.books.downloadBook(requireNotNull(detail.file).download, destination)

            assertEquals(
                "/api/v1/library/books/book-1/download/",
                requests.last().url.encodedPath
            )
            assertEquals("Bearer spl_secret", requests.last().headers[HttpHeaders.Authorization])
            assertTrue(destination.toByteArray().contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
        }

    @Test
    fun `book asset download rejects a reference on another origin before transport`() =
        runBlocking {
            var requestCount = 0
            val client = authenticatedClient {
                requestCount += 1
                jsonResponse(
                    BOOK_DETAIL.replace(
                        "https://library.example/api/v1/library/books/book-1/download/",
                        "https://assets.example/book-1.epub"
                    )
                )
            }
            val detail = client.library.books.getBook("book-1")

            assertThrows(SplClientException.ProtocolInvalid::class.java) {
                runBlocking {
                    client.library.books.downloadBook(
                        requireNotNull(detail.file).download,
                        ByteArrayOutputStream()
                    )
                }
            }
            assertEquals(1, requestCount)
        }

    @Test
    fun `book asset download preserves authentication and request failure taxonomy`() =
        runBlocking {
            suspend fun downloadFailure(status: HttpStatusCode): Throwable {
                val client = authenticatedClient { request ->
                    if (request.url.encodedPath.endsWith("/download/")) {
                        jsonResponse("{\"detail\":\"failure\"}", status)
                    } else {
                        jsonResponse(BOOK_DETAIL)
                    }
                }
                val detail = client.library.books.getBook("book-1")
                return runCatching {
                    client.library.books.downloadBook(
                        requireNotNull(detail.file).download,
                        ByteArrayOutputStream()
                    )
                }.exceptionOrNull() ?: error("Expected download failure.")
            }

            assertTrue(
                downloadFailure(HttpStatusCode.Unauthorized) is
                    SplClientException.AuthenticationRejected
            )
            assertTrue(
                downloadFailure(HttpStatusCode.InternalServerError) is
                    SplClientException.AuthenticatedRequestFailed
            )
        }

    @Test
    fun `book asset transport failure maps to server unreachable`() = runBlocking {
        val client = authenticatedClient { request ->
            if (request.url.encodedPath.endsWith("/download/")) {
                throw IOException("network unavailable")
            }
            jsonResponse(BOOK_DETAIL)
        }
        val detail = client.library.books.getBook("book-1")

        assertThrows(SplClientException.ServerUnreachable::class.java) {
            runBlocking {
                client.library.books.downloadBook(
                    requireNotNull(detail.file).download,
                    ByteArrayOutputStream()
                )
            }
        }
        Unit
    }

    @Test
    fun `book detail maps ordered metadata nullable file and authenticated reference`() =
        runBlocking {
            var request: HttpRequestData? = null
            val client = authenticatedClient { captured ->
                request = captured
                jsonResponse(BOOK_DETAIL)
            }

            val detail = client.library.books.getBook("book / 1")

            assertEquals("/api/v1/library/books/book%20%2F%201/", request?.url?.encodedPath)
            assertEquals("Bearer spl_secret", request?.headers?.get(HttpHeaders.Authorization))
            assertEquals(listOf("Second Author", "First Author"), detail.authors.map { it.name })
            assertEquals("1.20", detail.series?.seriesIndex?.value)
            assertEquals(listOf("award", "science-fiction"), detail.catalogTags.map { it.slug })
            assertEquals(listOf("Private", "Public"), detail.groups.map { it.name })
            assertEquals(listOf("isbn_13", "other"), detail.identifiers.map { it.scheme })
            assertEquals("<p>Description</p>", detail.description)
            assertEquals(1234567L, detail.file?.fileSize)
            assertNull(detail.file?.checksum)
            assertEquals(
                "https://library.example/api/v1/library/books/book-1/download/",
                detail.file?.download?.url
            )
        }

    @Test
    fun `book detail preserves nullable cover and file`() = runBlocking {
        val client = authenticatedClient {
            jsonResponse(
                BOOK_DETAIL.replace(
                    "\"cover_url\":\"https://assets.example/cover.webp\"",
                    "\"cover_url\":null"
                ).replace(FILE_JSON, "null")
            )
        }

        val detail = client.library.books.getBook("book-1")

        assertNull(detail.cover)
        assertNull(detail.file)
    }

    @Test
    fun `compact book and page mapping preserve server values and order`() = runBlocking {
        val client = authenticatedClient { jsonResponse(FULL_BOOK_PAGE) }

        val page = client.library.books.list(options = BookListOptions(page = 2, pageSize = 40))

        assertEquals(41, page.totalCount)
        assertEquals(2, page.page)
        assertEquals(40, page.pageSize)
        assertTrue(page.hasNext)
        assertTrue(page.hasPrevious)
        val book = page.results.single()
        assertEquals("book-1", book.id)
        assertEquals("Book, The", book.sortTitle)
        assertEquals(listOf("Second Author", "First Author"), book.authors.map { it.name })
        assertEquals("1.20", book.series?.seriesIndex?.value)
        assertEquals(listOf("award", "science-fiction"), book.catalogTags.map { it.slug })
        assertEquals(2026, book.publishedYear)
        assertEquals(8, book.publishedMonth)
        assertEquals(17, book.publishedDay)
        assertEquals(PublicationDatePrecision.DAY, book.publicationDatePrecision)
        assertEquals("https://assets.example/cover.webp", book.cover?.url)
        assertEquals("epub", book.fileFormat)
    }

    @Test
    fun `nullable compact values remain null and unspecified precision maps explicitly`() =
        runBlocking {
            val client = authenticatedClient {
                jsonResponse(NULLABLE_BOOK_PAGE.replace(PRECISION_PLACEHOLDER, ""))
            }

            val book = client.library.books.list().results.single()

            assertNull(book.series)
            assertNull(book.language)
            assertNull(book.publisher)
            assertNull(book.publishedYear)
            assertNull(book.publishedMonth)
            assertNull(book.publishedDay)
            assertNull(book.cover)
            assertEquals(PublicationDatePrecision.UNSPECIFIED, book.publicationDatePrecision)
        }

    @Test
    fun `all publication precision values map without inference`() = runBlocking {
        val wireValues = ArrayDeque(listOf("", "year", "month", "day"))
        val client = authenticatedClient {
            val value = wireValues.removeFirst()
            jsonResponse(NULLABLE_BOOK_PAGE.replace(PRECISION_PLACEHOLDER, value))
        }

        val values = List(4) {
            client.library.books.list().results.single().publicationDatePrecision
        }

        assertEquals(
            listOf(
                PublicationDatePrecision.UNSPECIFIED,
                PublicationDatePrecision.YEAR,
                PublicationDatePrecision.MONTH,
                PublicationDatePrecision.DAY
            ),
            values
        )
    }

    @Test
    fun `book browsing sends bounded filters paging ordering and bearer`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(EMPTY_PAGE)
        }

        client.library.books.list(
            options =
                BookListOptions(
                    q = "The Book",
                    authorId = "author-1",
                    seriesId = "series-1",
                    tagSlug = "science-fiction",
                    ordering = BookOrdering.SERIES_INDEX_DESCENDING,
                    page = 3,
                    pageSize = 100
                )
        )

        assertEquals("/api/v1/library/books/", request?.url?.encodedPath)
        assertEquals("The Book", request?.url?.parameters?.get("q"))
        assertEquals("author-1", request?.url?.parameters?.get("author"))
        assertEquals("series-1", request?.url?.parameters?.get("series"))
        assertEquals("science-fiction", request?.url?.parameters?.get("tag"))
        assertEquals("-series_index", request?.url?.parameters?.get("ordering"))
        assertEquals("3", request?.url?.parameters?.get("page"))
        assertEquals("100", request?.url?.parameters?.get("page_size"))
        assertEquals("Bearer spl_secret", request?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `all book ordering values remain typed and exact`() = runBlocking {
        val values = mutableListOf<String?>()
        val client = authenticatedClient { request ->
            values += request.url.parameters["ordering"]
            jsonResponse(EMPTY_PAGE)
        }

        BookOrdering.entries.forEach {
            client.library.books.list(options = BookListOptions(ordering = it))
        }

        assertEquals(
            listOf(
                "title",
                "-title",
                "author",
                "-author",
                "series",
                "-series",
                "series_index",
                "-series_index",
                "publisher",
                "-publisher"
            ),
            values
        )
    }

    @Test
    fun `broad search uses slashless route and accepts blank query`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(EMPTY_PAGE)
        }

        val page =
            client.library.books.search(
                options =
                    LibrarySearchOptions(
                        q = "",
                        ordering = LibrarySearchOrdering.SERIES_DESCENDING,
                        page = 4,
                        pageSize = 200
                    )
            )

        assertEquals("/api/v1/library/search", request?.url?.encodedPath)
        assertEquals("", request?.url?.parameters?.get("q"))
        assertEquals("-series", request?.url?.parameters?.get("ordering"))
        assertEquals("4", request?.url?.parameters?.get("page"))
        assertEquals("200", request?.url?.parameters?.get("page_size"))
        assertEquals(0, page.totalCount)
        assertTrue(page.results.isEmpty())
        assertFalse(page.hasNext)
        assertFalse(page.hasPrevious)
    }

    @Test
    fun `all broad search ordering values remain typed and exact`() = runBlocking {
        val values = mutableListOf<String?>()
        val client = authenticatedClient { request ->
            values += request.url.parameters["ordering"]
            jsonResponse(EMPTY_PAGE)
        }

        LibrarySearchOrdering.entries.forEach {
            client.library.books.search(options = LibrarySearchOptions(ordering = it))
        }

        assertEquals(
            listOf("title", "-title", "author", "-author", "series", "-series"),
            values
        )
    }

    @Test
    fun `library paging and identifiers are validated before transport`() {
        assertThrows(IllegalArgumentException::class.java) { BookListOptions(page = 0) }
        assertThrows(IllegalArgumentException::class.java) { BookListOptions(pageSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { BookListOptions(pageSize = 201) }
        assertThrows(IllegalArgumentException::class.java) { BookListOptions(authorId = " ") }
        assertThrows(IllegalArgumentException::class.java) { BookListOptions(seriesId = "") }
        assertThrows(IllegalArgumentException::class.java) { BookListOptions(tagSlug = " ") }
        assertThrows(IllegalArgumentException::class.java) { LibrarySearchOptions(page = -1) }
    }

    @Test
    fun `missing required compact field is rejected as invalid protocol`() {
        val client = authenticatedClient {
            jsonResponse(
                """{"count":1,"results":[{
                    "id":"book-1","sort_title":"Book","subtitle":"","authors":[],
                    "series":null,"catalog_tags":[],"language":null,"publisher":null,
                    "published_year":null,"published_month":null,"published_day":null,
                    "published_date_precision":"","cover_url":null,"file_format":"epub"
                }]}"""
            )
        }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { client.library.books.list() }
        }
    }

    @Test
    fun `malformed exact series index and page envelope are rejected`() {
        val invalidSeries = authenticatedClient {
            jsonResponse(FULL_BOOK_PAGE.replace("1.20", "1.2"))
        }
        val invalidPage = authenticatedClient { jsonResponse("""{"count":-1,"results":[]}""") }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { invalidSeries.library.books.list() }
        }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { invalidPage.library.books.list() }
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

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private companion object {
        const val FILE_JSON =
            """{"format":"epub","file_size":1234567,"checksum":null,"download_url":"https://library.example/api/v1/library/books/book-1/download/"}"""
        const val BOOK_DETAIL =
            """{
                "id":"book-1","title":"The Book","sort_title":"Book, The",
                "subtitle":"A subtitle",
                "authors":[{"id":"author-2","name":"Second Author"},{"id":"author-1","name":"First Author"}],
                "series":{"id":"series-1","name":"Series","sort_name":"Series","series_index":"1.20"},
                "catalog_tags":[{"id":"tag-1","name":"Award","slug":"award"},{"id":"tag-2","name":"Science Fiction","slug":"science-fiction"}],
                "language":"en","publisher":"Publisher","published_year":2026,
                "published_month":8,"published_day":17,"published_date_precision":"day",
                "cover_url":"https://assets.example/cover.webp","description":"<p>Description</p>",
                "identifiers":[{"id":"identifier-1","scheme":"isbn_13","value":"123"},{"id":"identifier-2","scheme":"other","value":"abc"}],
                "file":$FILE_JSON,
                "groups":[{"id":"group-2","name":"Private","description":"Private room","is_public_group":false},{"id":"group-1","name":"Public","description":"Common room","is_public_group":true}]
            }"""
        const val EMPTY_PAGE = """{"count":0,"next":null,"previous":null,"results":[]}"""
        const val PRECISION_PLACEHOLDER = "__PRECISION__"
        const val NULLABLE_BOOK_PAGE =
            """{
                "count":1,"next":null,"previous":null,"results":[{
                    "id":"book-2","title":"Book","sort_title":"Book","subtitle":"",
                    "authors":[],"series":null,"catalog_tags":[],"language":null,
                    "publisher":null,"published_year":null,"published_month":null,
                    "published_day":null,"published_date_precision":"__PRECISION__","cover_url":null,
                    "file_format":"epub"
                }]
            }"""
        const val FULL_BOOK_PAGE =
            """{
                "count":41,
                "next":"https://library.example/api/v1/library/books/?page=3",
                "previous":"https://library.example/api/v1/library/books/?page=1",
                "results":[{
                    "id":"book-1","title":"The Book","sort_title":"Book, The",
                    "subtitle":"A subtitle",
                    "authors":[
                        {"id":"author-2","name":"Second Author"},
                        {"id":"author-1","name":"First Author"}
                    ],
                    "series":{
                        "id":"series-1","name":"Series","sort_name":"Series",
                        "series_index":"1.20"
                    },
                    "catalog_tags":[
                        {"id":"tag-1","name":"Award","slug":"award"},
                        {"id":"tag-2","name":"Science Fiction","slug":"science-fiction"}
                    ],
                    "language":"en","publisher":"Publisher","published_year":2026,
                    "published_month":8,"published_day":17,
                    "published_date_precision":"day",
                    "cover_url":"https://assets.example/cover.webp","file_format":"epub"
                }]
            }"""
    }
}
