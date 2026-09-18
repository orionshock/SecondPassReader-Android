package com.secondpasslibrary.reader.marginalia.history

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.emptySessionBootstrap
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionsEmptyHistoryTest {
    @Test
    fun `unlinked visible Book becomes empty history with bootstrap context`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ ->
                throw SplClientException.BookReadingSessionHistoryNotFound()
            }
            activeSessionCall = { emptySessionBootstrap("authoritative-book") }
        }
        val controller = controller(capability, this)

        controller.enter(MarginaliaHistoryContext.Book("requested-book"))
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals("authoritative-book", state.book?.id)
        assertEquals(0, state.totalCount)
        assertEquals(1, state.currentPage)
        assertFalse(state.hasNext)
        assertNull(state.error)
    }

    @Test
    fun `empty scoped history keeps filters and search usable without paging`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            bookCall = { _, _ ->
                throw SplClientException.BookReadingSessionHistoryNotFound()
            }
        }
        val controller = controller(capability, this)
        controller.enter(MarginaliaHistoryContext.Book("book-1"))
        advanceUntilIdle()

        controller.changeStatus(ReadingSessionStatusFilter.ACTIVE)
        advanceUntilIdle()
        controller.changeStatus(ReadingSessionStatusFilter.CLOSED)
        advanceUntilIdle()
        controller.commitSearch("notes")
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(4, capability.bookRequests.size)
        assertEquals(ReadingSessionStatusFilter.CLOSED, controller.state.value.statusFilter)
        assertEquals("notes", controller.state.value.committedQuery)
        assertEquals(0, controller.state.value.totalCount)
        assertFalse(controller.state.value.hasNext)
    }

    private fun controller(
        capability: RecordingMarginaliaCapability,
        scope: CoroutineScope
    ): ReadingSessionsController =
        ReadingSessionsController(marginaliaProvider(capability), scope).also {
            it.prepare(marginaliaProfile())
        }
}
