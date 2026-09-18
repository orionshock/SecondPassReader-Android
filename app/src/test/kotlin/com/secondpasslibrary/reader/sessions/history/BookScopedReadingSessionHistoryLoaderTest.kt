package com.secondpasslibrary.reader.sessions.history

import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.emptySessionBootstrap
import com.secondpasslibrary.reader.marginalia.history.READING_SESSIONS_PAGE_SIZE
import com.secondpasslibrary.reader.marginalia.sessionDetail
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookScopedReadingSessionHistoryLoaderTest {
    private val loader = BookScopedReadingSessionHistoryLoader()

    @Test
    fun `linked Book history returns directly without active Session lookup`() = runTest {
        val capability = RecordingMarginaliaCapability()

        val result = loader.loadFirstPage(capability.books, "linked-book", options())

        assertTrue(result is BookScopedReadingSessionHistoryResult.History)
        val history = (result as BookScopedReadingSessionHistoryResult.History).value
        assertEquals("linked-book", history.book.id)
        assertEquals(listOf("linked-book"), capability.bookRequests.map { it.first })
        assertTrue(capability.activeSessionRequests.isEmpty())
    }

    @Test
    fun `missing history without active Session is valid empty Book history`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ ->
                throw SplClientException.BookReadingSessionHistoryNotFound()
            }
            activeSessionCall = { emptySessionBootstrap("authoritative-book") }
        }

        val result = loader.loadFirstPage(capability.books, "requested-book", options())

        assertTrue(result is BookScopedReadingSessionHistoryResult.VisibleBookWithoutHistory)
        val empty = result as BookScopedReadingSessionHistoryResult.VisibleBookWithoutHistory
        assertEquals("authoritative-book", empty.book.id)
        assertEquals(listOf("requested-book"), capability.activeSessionRequests)
        assertEquals(0, capability.openSessionRequests)
        assertEquals(0, capability.startOverRequests)
    }

    @Test
    fun `missing history with active Session is protocol invalid`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ ->
                throw SplClientException.BookReadingSessionHistoryNotFound()
            }
            activeSessionCall = {
                emptySessionBootstrap("book-1").copy(
                    activeSession = sessionDetail("active").session
                )
            }
        }

        val result = loader.loadFirstPage(capability.books, "book-1", options())

        assertEquals(
            BookScopedReadingSessionHistoryResult.ProtocolInvalidActiveSessionMissingHistory,
            result
        )
    }

    @Test
    fun `active Session lookup failure preserves client classification`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ ->
                throw SplClientException.BookReadingSessionHistoryNotFound()
            }
            activeSessionCall = {
                throw SplClientException.ReadingSessionLifecycleRejected(
                    ReadingSessionLifecycleRejection.RESOURCE_NOT_FOUND
                )
            }
        }

        val failure = runCatching {
            loader.loadFirstPage(capability.books, "missing-book", options())
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.ReadingSessionLifecycleRejected)
        assertEquals(listOf("missing-book"), capability.activeSessionRequests)
    }

    @Test
    fun `ordinary history failure does not perform active Session lookup`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ -> throw SplClientException.ServerUnreachable() }
        }

        val failure = runCatching {
            loader.loadFirstPage(capability.books, "book-1", options())
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.ServerUnreachable)
        assertTrue(capability.activeSessionRequests.isEmpty())
    }

    @Test
    fun `authentication rejection remains distinguishable without fallback lookup`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ -> throw SplClientException.AuthenticationRejected() }
        }

        val failure = runCatching {
            loader.loadFirstPage(capability.books, "book-1", options())
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.AuthenticationRejected)
        assertTrue(capability.activeSessionRequests.isEmpty())
    }

    @Test
    fun `later page is outside first-page authority handling`() = runTest {
        val capability = RecordingMarginaliaCapability()

        val failure = runCatching {
            loader.loadFirstPage(capability.books, "book-1", options(page = 2))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(capability.bookRequests.isEmpty())
        assertTrue(capability.activeSessionRequests.isEmpty())
    }

    private fun options(page: Int = 1) = BookReadingSessionListOptions(
        page = page,
        pageSize = READING_SESSIONS_PAGE_SIZE
    )
}
