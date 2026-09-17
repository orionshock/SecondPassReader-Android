package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionStatusFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarginaliaControllerTest {
    @Test
    fun `Sessions and Books modes retain independent child state`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            globalCall = { marginaliaPage(it.page, listOf(sessionItem("session-1"))) }
            marginaliaBooksCall = { marginaliaPage(it.page, listOf(marginaliaBook("book-1"))) }
        }
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )
        controller.initialize(marginaliaProfile())
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.CommitSessionsSearch("session query"))
        advanceUntilIdle()

        controller.accept(MarginaliaIntent.SelectBrowseMode(MarginaliaBrowseMode.BOOKS))
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.CommitBooksSearch("book query"))
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.SelectBrowseMode(MarginaliaBrowseMode.SESSIONS))

        assertEquals(MarginaliaBrowseMode.SESSIONS, controller.state.value.browseMode)
        assertEquals("session query", controller.state.value.sessions.committedQuery)
        assertEquals("book query", controller.state.value.books.committedQuery)
        assertEquals(0, capability.openSessionRequests)
    }

    @Test
    fun `selecting a Marginalia Book enters scoped history and returns to Books`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )
        controller.initialize(marginaliaProfile())
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.SelectBrowseMode(MarginaliaBrowseMode.BOOKS))
        advanceUntilIdle()

        controller.accept(MarginaliaIntent.SelectBook("book-1"))
        advanceUntilIdle()
        assertEquals(
            MarginaliaDestination.History(
                MarginaliaHistoryContext.Book("book-1"),
                MarginaliaReturnDestination.Books
            ),
            controller.state.value.destination
        )
        controller.accept(MarginaliaIntent.SelectSession("session-1"))
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.BackFromDetail)
        assertEquals(
            MarginaliaDestination.History(
                MarginaliaHistoryContext.Book("book-1"),
                MarginaliaReturnDestination.Books
            ),
            controller.state.value.destination
        )
        controller.accept(MarginaliaIntent.BackFromBookHistory)

        assertEquals(MarginaliaBrowseMode.BOOKS, controller.state.value.browseMode)
        assertEquals(
            MarginaliaDestination.History(MarginaliaHistoryContext.Global),
            controller.state.value.destination
        )
    }

    @Test
    fun `Book entry loads scoped history without creating a Reading Session`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )

        controller.initialize(
            marginaliaProfile(),
            MarginaliaHistoryContext.Book("book-1")
        )
        advanceUntilIdle()

        assertEquals(
            MarginaliaDestination.History(MarginaliaHistoryContext.Book("book-1")),
            controller.state.value.destination
        )
        assertEquals("book-1", capability.bookRequests.single().first)
        assertEquals(0, capability.openSessionRequests)
        assertTrue(controller.state.value.sessions.sessions.isEmpty())
    }

    @Test
    fun `a different Book route starts with fresh history filters`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )
        controller.initialize(
            marginaliaProfile(),
            MarginaliaHistoryContext.Book("book-1")
        )
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.ChangeStatus(ReadingSessionStatusFilter.ACTIVE))
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.CommitSessionsSearch("Kindle"))
        advanceUntilIdle()

        controller.initialize(
            marginaliaProfile(),
            MarginaliaHistoryContext.Book("book-2")
        )
        advanceUntilIdle()

        assertEquals(ReadingSessionStatusFilter.ALL, controller.state.value.sessions.statusFilter)
        assertEquals("", controller.state.value.sessions.committedQuery)
        assertEquals("book-2", capability.bookRequests.last().first)
        assertEquals(null, capability.bookRequests.last().second.status)
        assertEquals(null, capability.bookRequests.last().second.q)
    }

    @Test
    fun `Session selection and back preserve prior Book history state`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )
        controller.initialize(marginaliaProfile())
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.ShowBookHistory("book-1"))
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.ChangeStatus(ReadingSessionStatusFilter.CLOSED))
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.CommitSessionsSearch("notes"))
        advanceUntilIdle()
        val requestsBeforeDetail = capability.bookRequests.size

        controller.accept(MarginaliaIntent.SelectSession("session-1"))
        advanceUntilIdle()
        assertTrue(controller.state.value.destination is MarginaliaDestination.SessionDetail)
        controller.accept(MarginaliaIntent.BackFromDetail)

        assertEquals(
            MarginaliaDestination.History(MarginaliaHistoryContext.Book("book-1")),
            controller.state.value.destination
        )
        assertEquals(
            ReadingSessionStatusFilter.CLOSED,
            controller.state.value.sessions.statusFilter
        )
        assertEquals("notes", controller.state.value.sessions.committedQuery)
        assertEquals(requestsBeforeDetail, capability.bookRequests.size)
    }

    @Test
    fun `direct edit entry opens existing metadata workflow after detail loads`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )

        controller.initialize(
            marginaliaProfile(),
            detailEntry =
                ReadingSessionDetailEntry(
                    "session-1",
                    ReadingSessionDetailEntryAction.EDIT
                )
        )
        advanceUntilIdle()

        assertEquals(listOf("session-1"), capability.detailRequests)
        assertTrue(controller.state.value.metadataEdit.open)
        assertTrue(controller.state.value.destination is MarginaliaDestination.SessionDetail)
    }

    @Test
    fun `direct close entry opens existing finalization workflow after detail loads`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )

        controller.initialize(
            marginaliaProfile(),
            detailEntry =
                ReadingSessionDetailEntry(
                    "session-1",
                    ReadingSessionDetailEntryAction.CLOSE
                )
        )
        advanceUntilIdle()

        assertEquals(listOf("session-1"), capability.detailRequests)
        assertTrue(controller.state.value.close.open)
        assertTrue(controller.state.value.metadataEdit.open.not())
    }

    @Test
    fun `parent exposes typed Book Detail and Reader navigation`() = runTest {
        val controller = MarginaliaController(
            marginaliaProvider(RecordingMarginaliaCapability()),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )

        controller.accept(MarginaliaIntent.OpenBookDetail("book-1"))
        assertEquals(
            MarginaliaExternalNavigationIntent.BookDetail("book-1"),
            controller.navigation.first()
        )
        controller.accept(MarginaliaIntent.OpenReader("book-1", "session-1"))
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
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )
        controller.initialize(marginaliaProfile())
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.SelectSession("session-1"))
        advanceUntilIdle()
        controller.accept(MarginaliaIntent.BeginEdit)
        controller.accept(MarginaliaIntent.EditName("Renamed"))
        controller.accept(MarginaliaIntent.SaveEdit)
        advanceUntilIdle()

        assertEquals("Renamed", controller.state.value.detail.detail?.session?.summary?.name)
        assertEquals("Renamed", controller.state.value.sessions.sessions.single().session.name)
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
            val controller = MarginaliaController(
                marginaliaProvider(capability),
                CoroutineScope(
                    backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
                )
            )
            controller.initialize(marginaliaProfile())
            advanceUntilIdle()
            controller.accept(MarginaliaIntent.ChangeStatus(filter))
            advanceUntilIdle()
            controller.accept(MarginaliaIntent.SelectSession("session-$filter"))
            advanceUntilIdle()
            controller.accept(MarginaliaIntent.BeginClose)
            controller.accept(MarginaliaIntent.ConfirmClose)
            advanceUntilIdle()

            assertEquals(expectedCount, controller.state.value.sessions.sessions.size)
            controller.state.value.sessions.sessions.firstOrNull()?.let {
                assertEquals(ReadingSessionStatus.CLOSED, it.session.status)
            }
            assertEquals(
                ReadingSessionStatus.CLOSED,
                controller.state.value.detail.detail?.session?.summary?.status
            )
            controller.close()
        }
    }

    @Test
    fun `close tears down child loading`() = runTest {
        var cancelled = false
        val capability = RecordingMarginaliaCapability().apply {
            globalCall = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val controller = MarginaliaController(
            marginaliaProvider(capability),
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        )
        controller.initialize(marginaliaProfile())
        advanceUntilIdle()

        controller.close()
        controller.close()
        advanceUntilIdle()

        assertTrue(cancelled)
    }
}
