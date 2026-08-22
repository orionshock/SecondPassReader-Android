package com.secondpasslibrary.reader.marginalia.history

import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.marginaliaPage
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import com.secondpasslibrary.reader.marginalia.sessionBook
import com.secondpasslibrary.reader.marginalia.sessionItem
import com.secondpasslibrary.reader.marginalia.sessionSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionsControllerTest {
    @Test
    fun `global history loads with explicit defaults and preserves domain state`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            globalCall = {
                marginaliaPage(
                    1,
                    listOf(
                        sessionItem("active"),
                        sessionItem("closed", ReadingSessionStatus.CLOSED)
                    )
                )
            }
        }
        val controller = ReadingSessionsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.enter(MarginaliaHistoryContext.Global)
        advanceUntilIdle()

        val request = capability.globalRequests.single()
        assertNull(request.status)
        assertNull(request.q)
        assertNull(request.hasAnnotations)
        assertEquals(READING_SESSIONS_PAGE_SIZE, request.pageSize)
        assertEquals(
            listOf(ReadingSessionStatus.ACTIVE, ReadingSessionStatus.CLOSED),
            controller.state.value.sessions.map { it.session.status }
        )
    }

    @Test
    fun `status and committed search reset global history`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = ReadingSessionsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.enter(MarginaliaHistoryContext.Global)
        advanceUntilIdle()

        controller.changeStatus(ReadingSessionStatusFilter.ACTIVE)
        advanceUntilIdle()
        controller.changeStatus(ReadingSessionStatusFilter.CLOSED)
        advanceUntilIdle()
        controller.commitSearch("  Le Guin  ")
        advanceUntilIdle()

        assertEquals(
            listOf(null, ReadingSessionStatus.ACTIVE, ReadingSessionStatus.CLOSED),
            capability.globalRequests.take(3).map { it.status }
        )
        assertEquals("Le Guin", capability.globalRequests.last().q)
        assertEquals(1, capability.globalRequests.last().page)
    }

    @Test
    fun `next page appends once and failure retains server ordered content`() = runTest {
        var secondPageAttempts = 0
        val gate = CompletableDeferred<Unit>()
        val capability = RecordingMarginaliaCapability().apply {
            globalCall = { request ->
                if (request.page == 1) {
                    marginaliaPage(1, listOf(sessionItem("b")), total = 2, hasNext = true)
                } else {
                    secondPageAttempts += 1
                    if (secondPageAttempts == 1) {
                        gate.await()
                        throw SplClientException.ServerUnreachable()
                    }
                    marginaliaPage(2, listOf(sessionItem("a")), total = 2)
                }
            }
        }
        val controller = ReadingSessionsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.enter(MarginaliaHistoryContext.Global)
        advanceUntilIdle()

        controller.loadNextPage()
        controller.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2), capability.globalRequests.map { it.page })
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("b"), controller.state.value.sessions.map { it.session.id })
        assertEquals(MarginaliaLoadPhase.NEXT_PAGE, controller.state.value.error?.phase)

        controller.retry()
        advanceUntilIdle()
        assertEquals(listOf("b", "a"), controller.state.value.sessions.map { it.session.id })
    }

    @Test
    fun `stale response cannot replace a newer query`() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        val capability = RecordingMarginaliaCapability().apply {
            globalCall = { request ->
                if (request.q == null) {
                    withContext(NonCancellable) { oldGate.await() }
                    marginaliaPage(1, listOf(sessionItem("old")))
                } else {
                    marginaliaPage(1, listOf(sessionItem("new")))
                }
            }
        }
        val controller = ReadingSessionsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.enter(MarginaliaHistoryContext.Global)
        runCurrent()
        controller.commitSearch("new")
        runCurrent()
        oldGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("new"), controller.state.value.sessions.map { it.session.id })
    }

    @Test
    fun `Book history retains parent context and uses Book request shape`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { bookId, options ->
                BookReadingSessionHistory(
                    sessionBook(bookId),
                    marginaliaPage(options.page, listOf(sessionSummary("session")))
                )
            }
        }
        val controller = ReadingSessionsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.enter(MarginaliaHistoryContext.Book("book-1"))
        advanceUntilIdle()

        val request = capability.bookRequests.single()
        assertEquals("book-1", request.first)
        assertEquals(READING_SESSIONS_PAGE_SIZE, request.second.pageSize)
        assertEquals("book-1", controller.state.value.book?.id)
        assertEquals("book-1", controller.state.value.sessions.single().book.id)
        assertFalse(controller.state.value.nextPageLoading)
    }

    @Test
    fun `initial authentication rejection stays distinct and is propagated`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            globalCall = { throw SplClientException.AuthenticationRejected() }
        }
        val controller = ReadingSessionsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.enter(MarginaliaHistoryContext.Global)
        advanceUntilIdle()

        assertEquals(
            MarginaliaFailure.AUTHENTICATION_REJECTED,
            controller.state.value.error?.failure
        )
        assertEquals(
            MarginaliaConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
    }
}
