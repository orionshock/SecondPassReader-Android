package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaBooksClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ClosedReadingSessionPage
import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.MarginaliaBookListOptions
import com.secondpasslibrary.client.MarginaliaBookSummary
import com.secondpasslibrary.client.MarginaliaIdempotencyKey
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingProgressInput
import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.ReadingSessionBootstrap
import com.secondpasslibrary.client.ReadingSessionDetail
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionFinalization
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionMetadataInput
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.ReadingSessionSummary
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionCoordinatorTest {
    @Test
    fun `explicit Session loads exact identity and progress without active lookup or open`() =
        runTest {
            val api = FakeSessionApi().apply {
                exact = detail("session-closed", "book-1", ReadingSessionStatus.CLOSED)
                progress = ReadingProgress("epubcfi(/6/2!/4/2:3)", null, "now")
            }

            val context = coordinator(api).resolve(
                profile(),
                ReaderSessionRequest("book-1", "session-closed")
            )

            assertEquals("session-closed", api.exactSessionId)
            assertEquals(0, api.activeCalls)
            assertEquals(0, api.openCalls)
            assertEquals(ReaderSessionStatus.CLOSED, context.status)
            assertEquals(PROGRESS_CFI, context.savedProgressCfi)
        }

    @Test
    fun `Book entry reuses existing active Session without opening another`() = runTest {
        val api = FakeSessionApi().apply {
            active = bootstrap("book-1", detail("session-active", "book-1"))
            progress = ReadingProgress(PROGRESS_CFI, null, "now")
        }

        val context = coordinator(api).resolve(profile(), ReaderSessionRequest("book-1"))

        assertEquals(1, api.activeCalls)
        assertEquals(0, api.openCalls)
        assertEquals("session-active", context.sessionId)
        assertEquals(PROGRESS_CFI, context.savedProgressCfi)
    }

    @Test
    fun `Book entry lazily opens one Session only when no active Session exists`() = runTest {
        val api = FakeSessionApi().apply {
            active = bootstrap("book-1", null)
            opened = bootstrap("book-1", detail("session-new", "book-1"), created = true)
        }

        val context = coordinator(api).resolve(profile(), ReaderSessionRequest("book-1"))

        assertEquals(1, api.activeCalls)
        assertEquals(1, api.openCalls)
        assertEquals("session-new", context.sessionId)
        assertNull(context.savedProgressCfi)
    }

    @Test
    fun `progress fetch failure preserves resolved Session context`() = runTest {
        val api = FakeSessionApi().apply {
            exact = detail("session-1", "book-1")
            progressFailure = IllegalStateException("offline")
        }

        val context = coordinator(api).resolve(
            profile(),
            ReaderSessionRequest("book-1", "session-1")
        )

        assertEquals("session-1", context.sessionId)
        assertNull(context.savedProgressCfi)
        assertEquals(ReaderProgressLoadFailure.UNAVAILABLE, context.progressFailure)
    }

    @Test
    fun `wrong Book on explicit Session fails without substituting active Session`() = runTest {
        val api = FakeSessionApi().apply {
            exact = detail("session-1", "book-other")
        }

        val failure = runCatching {
            coordinator(api).resolve(
                profile(),
                ReaderSessionRequest("book-1", "session-1")
            )
        }.exceptionOrNull()
        assertTrue(failure is ReaderSessionIdentityMismatchException)
        assertEquals(0, api.activeCalls)
        assertEquals(0, api.openCalls)
    }

    private fun coordinator(api: FakeSessionApi) = SplReaderSessionCoordinator(
        object : AuthenticatedClientProvider {
            override suspend fun forProfile(profile: ConnectionProfile) =
                object : AuthenticatedSecondPassClient {
                    override val marginalia: AuthenticatedMarginaliaClient = api
                    override val library: AuthenticatedLibraryClient
                        get() = error("Library must not be used by Reader Session bootstrap.")
                    override val shelves: AuthenticatedShelvesClient
                        get() = error("Shelves must not be used by Reader Session bootstrap.")
                }
        }
    )

    private class FakeSessionApi : AuthenticatedMarginaliaClient {
        var exact: ReadingSessionDetailResult? = null
        var active: ReadingSessionBootstrap? = null
        var opened: ReadingSessionBootstrap? = null
        var progress: ReadingProgress? = null
        var progressFailure: Exception? = null
        var exactSessionId: String? = null
        var activeCalls = 0
        var openCalls = 0
        override val books: AuthenticatedMarginaliaBooksClient =
            object : AuthenticatedMarginaliaBooksClient {
                override suspend fun getActiveSession(bookId: String): ReadingSessionBootstrap {
                    activeCalls += 1
                    return checkNotNull(active)
                }

                override suspend fun openSession(
                    bookId: String,
                    metadata: ReadingSessionMetadataInput
                ): ReadingSessionBootstrap {
                    openCalls += 1
                    return checkNotNull(opened)
                }

                override suspend fun list(
                    options: MarginaliaBookListOptions
                ): MarginaliaPage<MarginaliaBookSummary> = unexpected()

                override suspend fun get(bookId: String): MarginaliaBookSummary = unexpected()

                override suspend fun listSessions(
                    bookId: String,
                    options: BookReadingSessionListOptions
                ): BookReadingSessionHistory = unexpected()

                override suspend fun startOver(
                    bookId: String,
                    idempotencyKey: MarginaliaIdempotencyKey,
                    finalization: ReadingSessionFinalization
                ): ReadingSessionBootstrap = unexpected()
            }
        override val sessions: AuthenticatedReadingSessionsClient =
            object : AuthenticatedReadingSessionsClient {
                override suspend fun get(sessionId: String): ReadingSessionDetailResult {
                    exactSessionId = sessionId
                    return checkNotNull(exact)
                }

                override suspend fun getProgress(sessionId: String): ReadingProgress? {
                    progressFailure?.let { throw it }
                    return progress
                }

                override suspend fun list(
                    options: ReadingSessionListOptions
                ): MarginaliaPage<ReadingSessionListItem> = unexpected()

                override suspend fun recent(
                    options: RecentReadingOptions
                ): List<RecentReadingItem> = unexpected()

                override suspend fun updateMetadata(
                    sessionId: String,
                    metadata: ReadingSessionMetadataInput
                ): ReadingSessionDetailResult = unexpected()

                override suspend fun close(
                    sessionId: String,
                    finalization: ReadingSessionFinalization
                ): ReadingSessionDetailResult = unexpected()

                override suspend fun replaceProgress(
                    sessionId: String,
                    progress: ReadingProgressInput
                ): ReadingProgress = unexpected()

                override suspend fun listAnnotations(
                    sessionId: String
                ): List<MarginaliaAnnotation> = unexpected()

                override suspend fun synchronizeAnnotations(
                    sessionId: String,
                    operations: List<MarginaliaAnnotationOperation>
                ): List<MarginaliaAnnotation> = unexpected()
            }
    }

    private fun detail(
        sessionId: String,
        bookId: String,
        status: ReadingSessionStatus = ReadingSessionStatus.ACTIVE
    ) = ReadingSessionDetailResult(
        book = ReadingSessionBook(bookId, "Book", null, canOpen = true),
        session = ReadingSessionDetail(summary(sessionId, status), progress = null)
    )

    private fun bootstrap(
        bookId: String,
        detail: ReadingSessionDetailResult?,
        created: Boolean = false
    ) = ReadingSessionBootstrap(
        created = created,
        book = ReadingSessionBook(bookId, "Book", null, canOpen = true),
        activeSession = detail?.session,
        annotations = emptyList(),
        closedSessions = ClosedReadingSessionPage(0, emptyList(), false, false)
    )

    private fun summary(sessionId: String, status: ReadingSessionStatus) = ReadingSessionSummary(
        id = sessionId,
        name = "",
        notes = "",
        status = status,
        startedAt = "start",
        closedAt = if (status == ReadingSessionStatus.CLOSED) "closed" else null,
        updatedAt = "updated",
        lastActivityAt = "activity",
        annotationCount = 0
    )

    private fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-23",
        clientSessionId = "client-session",
        clientName = "Reader",
        clientType = "reader"
    )

    private companion object {
        const val PROGRESS_CFI = "epubcfi(/6/2!/4/2:3)"

        fun unexpected(): Nothing = error("Unexpected SDK call from Reader Session bootstrap.")
    }
}
