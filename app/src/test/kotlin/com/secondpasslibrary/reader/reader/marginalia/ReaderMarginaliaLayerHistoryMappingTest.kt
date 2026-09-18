package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.ReadingSessionStatus as SplReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.marginaliaPage
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import com.secondpasslibrary.reader.marginalia.sessionBook
import com.secondpasslibrary.reader.marginalia.sessionSummary
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.sessions.history.BookScopedReadingSessionHistoryLoader
import com.secondpasslibrary.reader.sessions.history.BookScopedReadingSessionHistoryResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMarginaliaLayerHistoryMappingTest {
    @Test
    fun `shared history maps into Reader prior layers without presentation coupling`() {
        val result = BookScopedReadingSessionHistoryResult.History(
            BookReadingSessionHistory(
                sessionBook("book-1"),
                marginaliaPage(
                    page = 2,
                    results = listOf(
                        sessionSummary("active"),
                        sessionSummary("closed", SplReadingSessionStatus.CLOSED)
                    ),
                    hasNext = true
                )
            )
        )

        val page = result.toReaderMarginaliaLayerHistoryPage()

        assertEquals(listOf("active", "closed"), page.layers.map { it.sessionId })
        assertEquals(
            listOf(ReaderSessionStatus.ACTIVE, ReaderSessionStatus.CLOSED),
            page.layers.map { it.sessionStatus }
        )
        assertEquals(2, page.page)
        assertTrue(page.hasMore)
    }

    @Test
    fun `visible Book without history maps to an empty first Reader layer page`() {
        val result = BookScopedReadingSessionHistoryResult.VisibleBookWithoutHistory(
            sessionBook("book-1")
        )

        val page = result.toReaderMarginaliaLayerHistoryPage()

        assertTrue(page.layers.isEmpty())
        assertEquals(1, page.page)
        assertTrue(!page.hasMore)
    }

    @Test
    fun `protocol-invalid shared result preserves Reader failure classification`() {
        val failure = runCatching {
            BookScopedReadingSessionHistoryResult.ProtocolInvalidActiveSessionMissingHistory
                .toReaderMarginaliaLayerHistoryPage()
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.ProtocolInvalid)
    }

    @Test
    fun `later Reader page preserves direct history failure without authority lookup`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ ->
                throw SplClientException.BookReadingSessionHistoryNotFound()
            }
        }
        val loader = SplReaderMarginaliaLayerHistoryLoader(
            marginaliaProvider(capability),
            BookScopedReadingSessionHistoryLoader()
        )

        val failure = runCatching {
            loader.load(marginaliaProfile(), "book-1", page = 2)
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.BookReadingSessionHistoryNotFound)
        assertTrue(capability.activeSessionRequests.isEmpty())
    }
}
