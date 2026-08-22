package com.secondpasslibrary.reader.marginalia.history

import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.emptySessionBootstrap
import com.secondpasslibrary.reader.marginalia.sessionDetail
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookScopedReadingSessionHistoryLoaderTest {
    private val loader = BookScopedReadingSessionHistoryLoader()

    @Test
    fun `linked Book history returns the normal history without fallback`() = runTest {
        val capability = RecordingMarginaliaCapability()

        val result = loader.loadInitial(capability.books, "linked-book", options())

        assertTrue(result is BookScopedReadingSessionHistoryLoadResult.LinkedHistory)
        val history = (result as BookScopedReadingSessionHistoryLoadResult.LinkedHistory).history
        assertEquals("linked-book", history.book.id)
        assertEquals(listOf("linked-book"), capability.bookRequests.map { it.first })
        assertTrue(capability.activeSessionRequests.isEmpty())
    }

    @Test
    fun `unlinked visible Book returns authoritative context without creating a Session`() =
        runTest {
            val capability = RecordingMarginaliaCapability().apply {
                bookCall = { _, _ ->
                    throw SplClientException.BookReadingSessionHistoryNotFound()
                }
                activeSessionCall = { emptySessionBootstrap("authoritative-book") }
            }

            val result = loader.loadInitial(capability.books, "requested-book", options())

            assertTrue(
                result is BookScopedReadingSessionHistoryLoadResult.VisibleBookWithoutHistory
            )
            val empty =
                result as BookScopedReadingSessionHistoryLoadResult.VisibleBookWithoutHistory
            assertEquals("authoritative-book", empty.book.id)
            assertEquals(listOf("requested-book"), capability.activeSessionRequests)
            assertEquals(0, capability.openSessionRequests)
            assertEquals(0, capability.startOverRequests)
        }

    @Test
    fun `inaccessible Book preserves active-session lookup failure`() = runTest {
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
            loader.loadInitial(capability.books, "missing-book", options())
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.ReadingSessionLifecycleRejected)
        assertEquals(listOf("missing-book"), capability.activeSessionRequests)
    }

    @Test
    fun `non-history-not-found failure does not perform fallback lookup`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ -> throw SplClientException.ServerUnreachable() }
        }

        val failure = runCatching {
            loader.loadInitial(capability.books, "book-1", options())
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
            loader.loadInitial(capability.books, "book-1", options())
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.AuthenticationRejected)
        assertTrue(capability.activeSessionRequests.isEmpty())
    }

    @Test
    fun `loader rejects append pages without making a request`() = runTest {
        val capability = RecordingMarginaliaCapability()

        val failure = runCatching {
            loader.loadInitial(capability.books, "book-1", options(page = 2))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(capability.bookRequests.isEmpty())
        assertTrue(capability.activeSessionRequests.isEmpty())
    }

    @Test
    fun `history-not-found with an active Session remains protocol invalid`() = runTest {
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

        val failure = runCatching {
            loader.loadInitial(capability.books, "book-1", options())
        }.exceptionOrNull()

        assertTrue(failure is SplClientException.ProtocolInvalid)
    }

    private fun options(page: Int = 1) = BookReadingSessionListOptions(
        page = page,
        pageSize = READING_SESSIONS_PAGE_SIZE
    )
}
