package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.AuthenticatedMarginaliaBooksClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.ReadingSessionDetail
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.ReadingSessionSummary
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
import com.secondpasslibrary.reader.FakeAuthenticatedMarginaliaClient
import com.secondpasslibrary.reader.FakeAuthenticatedShelvesClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile

internal class RecordingMarginaliaCapability : AuthenticatedMarginaliaClient {
    val globalRequests = mutableListOf<ReadingSessionListOptions>()
    val bookRequests = mutableListOf<Pair<String, BookReadingSessionListOptions>>()
    val detailRequests = mutableListOf<String>()
    val annotationRequests = mutableListOf<String>()

    var globalCall:
        suspend (ReadingSessionListOptions) -> MarginaliaPage<ReadingSessionListItem> = {
            marginaliaPage(it.page, emptyList())
        }
    var bookCall: suspend (String, BookReadingSessionListOptions) -> BookReadingSessionHistory =
        { bookId, options ->
            BookReadingSessionHistory(
                sessionBook(bookId),
                marginaliaPage(options.page, emptyList())
            )
        }
    var detailCall: suspend (String) -> ReadingSessionDetailResult = { sessionDetail(it) }
    var annotationsCall: suspend (String) -> List<MarginaliaAnnotation> = { emptyList() }

    override val books = object : AuthenticatedMarginaliaBooksClient by
    FakeAuthenticatedMarginaliaClient.books {
        override suspend fun listSessions(
            bookId: String,
            options: BookReadingSessionListOptions
        ): BookReadingSessionHistory {
            bookRequests += bookId to options
            return bookCall(bookId, options)
        }
    }

    override val sessions = object : AuthenticatedReadingSessionsClient by
    FakeAuthenticatedMarginaliaClient.sessions {
        override suspend fun list(
            options: ReadingSessionListOptions
        ): MarginaliaPage<ReadingSessionListItem> {
            globalRequests += options
            return globalCall(options)
        }

        override suspend fun get(sessionId: String): ReadingSessionDetailResult {
            detailRequests += sessionId
            return detailCall(sessionId)
        }

        override suspend fun listAnnotations(sessionId: String): List<MarginaliaAnnotation> {
            annotationRequests += sessionId
            return annotationsCall(sessionId)
        }
    }
}

internal class MarginaliaTestClient(override val marginalia: AuthenticatedMarginaliaClient) :
    AuthenticatedSecondPassClient {
    override val library = FakeAuthenticatedLibraryClient()
    override val shelves = FakeAuthenticatedShelvesClient
}

internal class MarginaliaTestClientProvider(client: AuthenticatedSecondPassClient) :
    AuthenticatedClientProvider {
    private val activeClient = client

    override suspend fun forProfile(profile: ConnectionProfile) = activeClient
}

internal fun marginaliaProvider(capability: RecordingMarginaliaCapability) =
    MarginaliaTestClientProvider(MarginaliaTestClient(capability))

internal fun marginaliaProfile() = ConnectionProfile(
    serverOrigin = "https://library.example",
    serverBaseUrl = "https://library.example/",
    apiBaseUrl = "https://library.example/api/v1/",
    serverName = "Library",
    serverDescription = "",
    serverVersion = "1",
    serverReleaseDate = "",
    clientSessionId = "client-session",
    clientName = "Tablet",
    clientType = "second-pass-android-client"
)

internal fun sessionSummary(
    id: String,
    status: ReadingSessionStatus = ReadingSessionStatus.ACTIVE
) = ReadingSessionSummary(
    id = id,
    name = "",
    notes = "Notes $id",
    status = status,
    startedAt = "2026-08-01T00:00:00Z",
    closedAt = if (status == ReadingSessionStatus.CLOSED) "2026-08-02T00:00:00Z" else null,
    updatedAt = "2026-08-02T00:00:00Z",
    lastActivityAt = "2026-08-02T00:00:00Z",
    annotationCount = 2
)

internal fun sessionBook(id: String) = ReadingSessionBook(id, "Book $id", null, true)

internal fun sessionItem(id: String, status: ReadingSessionStatus = ReadingSessionStatus.ACTIVE) =
    ReadingSessionListItem(sessionSummary(id, status), sessionBook("book-$id"))

internal fun sessionDetail(id: String) = ReadingSessionDetailResult(
    book = sessionBook("book-$id"),
    session = ReadingSessionDetail(sessionSummary(id), null)
)

internal fun <T> marginaliaPage(
    page: Int,
    results: List<T>,
    total: Int = results.size,
    hasNext: Boolean = false
) = MarginaliaPage(total, results, hasNext, page > 1, page, READING_SESSIONS_PAGE_SIZE)
