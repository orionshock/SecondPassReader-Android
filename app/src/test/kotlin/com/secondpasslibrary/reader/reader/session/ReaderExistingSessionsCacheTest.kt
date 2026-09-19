package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaBooksClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
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
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderExistingSessionsCacheTest {
    @Test
    fun `zero existing Sessions does not create one`() = runTest {
        val api = FakeMarginalia(emptyList())
        val local = RecordingLocalStore()
        cache(api, local).cache(profile(), "account-1", "book-1")
        assertEquals(1, api.listCalls)
        assertEquals(0, api.openCalls)
        assertTrue(local.retained.isEmpty())
    }

    @Test
    fun `active and historical Sessions are retained with progress and annotations`() = runTest {
        val summaries = listOf(
            summary("active", ReadingSessionStatus.ACTIVE),
            summary("closed", ReadingSessionStatus.CLOSED)
        )
        val api = FakeMarginalia(summaries)
        val local = RecordingLocalStore()
        cache(api, local).cache(profile(), "account-1", "book-1")
        assertEquals(0, api.openCalls)
        assertEquals(listOf("active", "closed"), local.retained.map { it.sessionId })
        assertEquals(ReaderSessionStatus.CLOSED, local.retained.last().status)
        assertEquals("epubcfi(/6/2!/4/2:3)", local.retained.last().savedProgressCfi)
        assertEquals(listOf("active", "closed"), local.annotationSessions)
    }

    private fun cache(api: FakeMarginalia, local: RecordingLocalStore) =
        SplReaderExistingSessionsCache(
            clients = object : AuthenticatedClientProvider {
                override suspend fun forProfile(profile: ConnectionProfile) =
                    object : AuthenticatedSecondPassClient {
                        override val marginalia: AuthenticatedMarginaliaClient = api
                        override val library: AuthenticatedLibraryClient get() = unexpected()
                        override val shelves: AuthenticatedShelvesClient get() = unexpected()
                    }
            },
            local = local,
            annotations = ReaderAnnotationsLoader { _, _ -> emptyList() }
        )

    private class FakeMarginalia(private val summaries: List<ReadingSessionSummary>) :
        AuthenticatedMarginaliaClient {
        var listCalls = 0
        var openCalls = 0
        private val book = ReadingSessionBook("book-1", "Book", null, canOpen = true)
        override val books = object : AuthenticatedMarginaliaBooksClient {
            override suspend fun listSessions(
                bookId: String,
                options: BookReadingSessionListOptions
            ): BookReadingSessionHistory {
                listCalls++
                return BookReadingSessionHistory(
                    book,
                    MarginaliaPage(summaries.size, summaries, false, false, 1, 20)
                )
            }

            override suspend fun openSession(
                bookId: String,
                metadata: ReadingSessionMetadataInput
            ): ReadingSessionBootstrap {
                openCalls++
                return unexpected()
            }

            override suspend fun getActiveSession(bookId: String): ReadingSessionBootstrap =
                unexpected()
            override suspend fun list(
                options: MarginaliaBookListOptions
            ): MarginaliaPage<MarginaliaBookSummary> = unexpected()
            override suspend fun get(bookId: String): MarginaliaBookSummary = unexpected()
            override suspend fun startOver(
                bookId: String,
                idempotencyKey: MarginaliaIdempotencyKey,
                finalization: ReadingSessionFinalization
            ): ReadingSessionBootstrap = unexpected()
        }
        override val sessions = object : AuthenticatedReadingSessionsClient {
            override suspend fun get(sessionId: String): ReadingSessionDetailResult =
                ReadingSessionDetailResult(
                    book,
                    ReadingSessionDetail(
                        summaries.single { it.id == sessionId },
                        null
                    )
                )

            override suspend fun listAnnotations(sessionId: String): List<MarginaliaAnnotation> =
                emptyList()
            override suspend fun list(
                options: ReadingSessionListOptions
            ): MarginaliaPage<ReadingSessionListItem> = unexpected()
            override suspend fun recent(options: RecentReadingOptions): List<RecentReadingItem> =
                unexpected()
            override suspend fun updateMetadata(
                sessionId: String,
                metadata: ReadingSessionMetadataInput
            ): ReadingSessionDetailResult = unexpected()
            override suspend fun close(
                sessionId: String,
                finalization: ReadingSessionFinalization
            ): ReadingSessionDetailResult = unexpected()
            override suspend fun getProgress(sessionId: String): ReadingProgress =
                ReadingProgress("epubcfi(/6/2!/4/2:3)", null, "now")
            override suspend fun replaceProgress(
                sessionId: String,
                progress: ReadingProgressInput
            ): ReadingProgress = unexpected()
            override suspend fun synchronizeAnnotations(
                sessionId: String,
                operations: List<MarginaliaAnnotationOperation>
            ): List<MarginaliaAnnotation> = unexpected()
        }
    }

    private class RecordingLocalStore : LocalReaderStateStore {
        val retained = mutableListOf<ReaderSessionContext>()
        val annotationSessions = mutableListOf<String>()
        override suspend fun retainServerSession(
            account: LocalReaderAccountKey,
            bookId: String,
            session: ReaderSessionContext
        ): ReaderSessionContext {
            retained += session
            return session
        }

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            acknowledgedMutation: ReaderAnnotationMutationRequest?
        ) {
            annotationSessions += localSessionId
        }

        override suspend fun selectOfflineSession(
            account: LocalReaderAccountKey,
            bookId: String
        ): ReaderSessionContext = unexpected()
        override suspend fun writeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String,
            provenance: LocalReaderWriteProvenance,
            locationLabel: String?
        ) = unexpected()
        override suspend fun acknowledgeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String
        ) = unexpected()
        override suspend fun readAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String
        ): List<ReaderAnnotation> = unexpected()
        override suspend fun applyAnnotationMutation(
            account: LocalReaderAccountKey,
            localSessionId: String,
            request: ReaderAnnotationMutationRequest
        ): List<ReaderAnnotation> = unexpected()
        override suspend fun purgeAccount(account: LocalReaderAccountKey) = unexpected()
    }

    private fun summary(id: String, status: ReadingSessionStatus) = ReadingSessionSummary(
        id, "", "", status, "start", if (status == ReadingSessionStatus.CLOSED) "closed" else null,
        "updated", "activity", 0
    )

    private fun profile() = ConnectionProfile(
        "https://library.example", "https://library.example", "https://library.example/api/v1/",
        "Library", "", "test", "2026-09-19", "client", "Tablet", "android"
    )

    private companion object {
        fun unexpected(): Nothing = error("Unexpected creation or unrelated Reader work.")
    }
}
