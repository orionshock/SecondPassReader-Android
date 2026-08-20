package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.ReadingSessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarginaliaControllerTest {
    @Test
    fun `Session selection and back preserve prior Book history state`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = MarginaliaController(marginaliaProvider(capability), this)
        controller.initialize(marginaliaProfile())
        advanceUntilIdle()
        controller.showBookHistory("book-1")
        advanceUntilIdle()
        controller.sessions.changeStatus(ReadingSessionStatusFilter.CLOSED)
        advanceUntilIdle()
        controller.sessions.commitSearch("notes")
        advanceUntilIdle()
        val requestsBeforeDetail = capability.bookRequests.size

        controller.selectSession("session-1")
        advanceUntilIdle()
        assertTrue(controller.state.value.destination is MarginaliaDestination.SessionDetail)
        controller.backFromDetail()

        assertEquals(
            MarginaliaDestination.History(MarginaliaHistoryContext.Book("book-1")),
            controller.state.value.destination
        )
        assertEquals(
            ReadingSessionStatusFilter.CLOSED,
            controller.sessions.state.value.statusFilter
        )
        assertEquals("notes", controller.sessions.state.value.committedQuery)
        assertEquals(requestsBeforeDetail, capability.bookRequests.size)
    }

    @Test
    fun `parent exposes typed Book Detail and Reader navigation`() = runTest {
        val controller = MarginaliaController(
            marginaliaProvider(RecordingMarginaliaCapability()),
            this
        )

        controller.openBookDetail("book-1")
        assertEquals(
            MarginaliaExternalNavigationIntent.BookDetail("book-1"),
            controller.navigation.first()
        )
        controller.openReader("book-1", "session-1")
        assertEquals(
            MarginaliaExternalNavigationIntent.Reader("book-1", "session-1"),
            controller.navigation.first()
        )
    }

    @Test
    fun `authoritative metadata update reconciles visible history row`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            globalCall = { marginaliaPage(1, listOf(sessionItem("session-1"))) }
        }
        val controller = MarginaliaController(marginaliaProvider(capability), this)
        controller.initialize(marginaliaProfile())
        advanceUntilIdle()
        controller.selectSession("session-1")
        advanceUntilIdle()
        controller.detail.beginMetadataEdit()
        controller.detail.metadataEditor.updateName("Renamed")
        controller.detail.saveMetadata()
        advanceUntilIdle()

        assertEquals("Renamed", controller.detail.state.value.detail?.session?.summary?.name)
        assertEquals("Renamed", controller.sessions.state.value.sessions.single().session.name)
    }

    @Test
    fun `close reconciles All Active and Closed history filters`() = runTest {
        val filters = listOf(
            ReadingSessionStatusFilter.ALL to 1,
            ReadingSessionStatusFilter.ACTIVE to 0,
            ReadingSessionStatusFilter.CLOSED to 1
        )
        filters.forEach { (filter, expectedCount) ->
            val capability = RecordingMarginaliaCapability().apply {
                globalCall = { marginaliaPage(1, listOf(sessionItem("session-$filter"))) }
            }
            val controller = MarginaliaController(marginaliaProvider(capability), this)
            controller.initialize(marginaliaProfile())
            advanceUntilIdle()
            controller.sessions.changeStatus(filter)
            advanceUntilIdle()
            controller.selectSession("session-$filter")
            advanceUntilIdle()
            controller.detail.beginClose()
            controller.detail.confirmClose()
            advanceUntilIdle()

            assertEquals(expectedCount, controller.sessions.state.value.sessions.size)
            controller.sessions.state.value.sessions.firstOrNull()?.let {
                assertEquals(ReadingSessionStatus.CLOSED, it.session.status)
            }
            assertEquals(
                ReadingSessionStatus.CLOSED,
                controller.detail.state.value.detail?.session?.summary?.status
            )
            controller.close()
        }
    }
}
