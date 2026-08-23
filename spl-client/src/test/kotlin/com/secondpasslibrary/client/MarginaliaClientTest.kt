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

class MarginaliaClientTest {
    @Test
    fun `Book list maps complete summaries exact Series index and pagination`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(MARGINALIA_BOOK_PAGE)
        }

        val page = client.marginalia.books.list(
            MarginaliaBookListOptions(page = 2, pageSize = 50, q = "dresden")
        )

        assertEquals(3, page.totalCount)
        assertTrue(page.hasNext)
        assertTrue(page.hasPrevious)
        assertEquals(2, page.page)
        assertEquals(50, page.pageSize)
        val book = page.results.single()
        assertEquals(listOf("Second Author", "First Author"), book.authors.map { it.name })
        assertEquals("1.25", book.series?.seriesIndex?.value)
        assertEquals("https://assets.example/book.webp", book.cover?.url)
        assertFalse(book.canOpen)
        assertEquals(4, book.sessionCount)
        assertEquals(1, book.activeSessionCount)
        assertNull(book.lastActivityAt)
        assertEquals("/api/v1/marginalia/books/", request?.url?.encodedPath)
        assertEquals("2", request?.url?.parameters?.get("page"))
        assertEquals("50", request?.url?.parameters?.get("page_size"))
        assertEquals("dresden", request?.url?.parameters?.get("q"))
        assertEquals("Bearer spl_secret", request?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `Book detail encodes identity and preserves nullable Series`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(MARGINALIA_BOOK.replace(SERIES_VALUE, "null"))
        }

        val book = client.marginalia.books.get("book / 1")

        assertNull(book.series)
        assertEquals("/api/v1/marginalia/books/book%20%2F%201/", request?.url?.encodedPath)
    }

    @Test
    fun `global Sessions map filters page and historical Book context`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(GLOBAL_SESSION_PAGE)
        }

        val page =
            client.marginalia.sessions.list(
                ReadingSessionListOptions(
                    status = ReadingSessionStatus.CLOSED,
                    q = "notes and title",
                    hasAnnotations = true,
                    page = 3,
                    pageSize = 25
                )
            )

        val item = page.results.single()
        assertEquals("", item.session.name)
        assertEquals(ReadingSessionStatus.CLOSED, item.session.status)
        assertEquals("2026-08-03T00:00:00Z", item.session.closedAt)
        assertEquals(2, item.session.annotationCount)
        assertFalse(item.book.canOpen)
        assertNull(item.book.cover)
        assertEquals("closed", request?.url?.parameters?.get("status"))
        assertEquals("notes and title", request?.url?.parameters?.get("q"))
        assertEquals("true", request?.url?.parameters?.get("has_annotations"))
        assertEquals("3", request?.url?.parameters?.get("page"))
        assertEquals("25", request?.url?.parameters?.get("page_size"))
    }

    @Test
    fun `Book history maps parent context and excludes global-only annotation filter`() =
        runBlocking {
            var request: HttpRequestData? = null
            val client = authenticatedClient { captured ->
                request = captured
                jsonResponse(BOOK_SESSION_PAGE)
            }

            val history =
                client.marginalia.books.listSessions(
                    "book 1",
                    BookReadingSessionListOptions(
                        status = ReadingSessionStatus.ACTIVE,
                        q = "morning",
                        pageSize = 10
                    )
                )

            assertEquals("book-1", history.book.id)
            assertTrue(history.book.canOpen)
            assertEquals(ReadingSessionStatus.ACTIVE, history.sessions.results.single().status)
            assertNull(history.sessions.results.single().closedAt)
            assertEquals("/api/v1/marginalia/books/book%201/sessions/", request?.url?.encodedPath)
            assertEquals("active", request?.url?.parameters?.get("status"))
            assertEquals("morning", request?.url?.parameters?.get("q"))
            assertFalse(request?.url?.parameters?.contains("has_annotations") == true)
        }

    @Test
    fun `Book history exposes its narrow not-found boundary`() {
        val client = authenticatedClient {
            jsonResponse(
                """{"detail":"No Book matches the given query."}""",
                HttpStatusCode.NotFound
            )
        }

        assertThrows(SplClientException.BookReadingSessionHistoryNotFound::class.java) {
            runBlocking {
                client.marginalia.books.listSessions(
                    "visible-book-without-history"
                )
            }
        }
    }

    @Test
    fun `Session detail maps nullable opaque progress label`() = runBlocking {
        var request: HttpRequestData? = null
        val client = authenticatedClient { captured ->
            request = captured
            jsonResponse(SESSION_DETAIL)
        }

        val detail = client.marginalia.sessions.get("session / 1")

        assertEquals("session-1", detail.session.summary.id)
        assertEquals("Notes", detail.session.summary.notes)
        assertEquals("epubcfi(/6/2)", detail.session.progress?.cfi)
        assertNull(detail.session.progress?.locationLabel)
        assertEquals("book-1", detail.book.id)
        assertEquals("/api/v1/marginalia/sessions/session%20%2F%201/", request?.url?.encodedPath)
    }

    @Test
    fun `malformed required Session and Book fields are rejected`() {
        val malformedSession =
            authenticatedClient {
                jsonResponse(GLOBAL_SESSION_PAGE.replace("\"notes\":\"Notes\",", ""))
            }
        val malformedBook =
            authenticatedClient {
                jsonResponse(MARGINALIA_BOOK_PAGE.replace("\"session_count\":4,", ""))
            }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { malformedSession.marginalia.sessions.list() }
        }
        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { malformedBook.marginalia.books.list() }
        }
    }

    @Test
    fun `page bounds fail before transport`() {
        assertThrows(IllegalArgumentException::class.java) {
            MarginaliaBookListOptions(page = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingSessionListOptions(pageSize = 201)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BookReadingSessionListOptions(pageSize = 0)
        }
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

    private companion object {
        const val SERIES_VALUE =
            "{\"id\":\"series-1\",\"name\":\"Series\",\"series_index\":\"1.25\"}"
        const val BOUNDED_BOOK =
            """{"id":"book-1","title":"Book","cover_url":null,"can_open":true}"""
        const val SESSION =
            """{"id":"session-1","name":"Morning","notes":"Notes","status":"active","started_at":"2026-08-01T00:00:00Z","closed_at":null,"updated_at":"2026-08-02T00:00:00Z","last_activity_at":"2026-08-02T00:00:00Z","annotation_count":2}"""
        const val MARGINALIA_BOOK =
            """{"id":"book-1","title":"Book","authors":[{"id":"author-2","name":"Second Author"},{"id":"author-1","name":"First Author"}],"series":$SERIES_VALUE,"cover_url":"https://assets.example/book.webp","can_open":false,"session_count":4,"active_session_count":1,"last_activity_at":null}"""
        const val MARGINALIA_BOOK_PAGE =
            """{"count":3,"next":"next","previous":"previous","results":[$MARGINALIA_BOOK]}"""
        const val GLOBAL_SESSION_PAGE =
            """{"count":1,"next":null,"previous":"previous","results":[{"id":"session-closed","name":"","notes":"Notes","status":"closed","started_at":"2026-08-01T00:00:00Z","closed_at":"2026-08-03T00:00:00Z","updated_at":"2026-08-03T00:00:00Z","last_activity_at":"2026-08-03T00:00:00Z","annotation_count":2,"book":{"id":"book-old","title":"Historical Book","cover_url":null,"can_open":false}}]}"""
        const val BOOK_SESSION_PAGE =
            """{"context":{"book":$BOUNDED_BOOK},"count":1,"next":null,"previous":null,"results":[$SESSION]}"""
        const val SESSION_DETAIL =
            """{"context":{"book":$BOUNDED_BOOK},"session":{"id":"session-1","name":"Morning","notes":"Notes","status":"active","started_at":"2026-08-01T00:00:00Z","closed_at":null,"updated_at":"2026-08-02T00:00:00Z","last_activity_at":"2026-08-02T00:00:00Z","annotation_count":2,"progress":{"cfi":"epubcfi(/6/2)","location_label":null,"updated_at":"2026-08-02T00:00:00Z"}}}"""
    }
}
