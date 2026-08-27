package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderMarginaliaLayersControllerTest {
    @Test
    fun `current layer preserves exact active and closed Session identity`() = runTest {
        val controller = controller { _, _, page -> emptyPage(page) }

        controller.select(profile(), BOOK_ID, session("active", ReaderSessionStatus.ACTIVE))
        advanceUntilIdle()
        val active = checkNotNull(controller.state.value.currentLayer)
        assertEquals("active", active.sessionId)
        assertEquals(ReaderMarginaliaLayerRole.CURRENT, active.role)
        assertEquals(ReaderSessionStatus.ACTIVE, active.sessionStatus)
        assertEquals("Named read", active.sessionName)
        assertEquals(4, active.annotationCount)
        assertTrue(active.isWritable())

        controller.select(profile(), BOOK_ID, session("closed", ReaderSessionStatus.CLOSED))
        advanceUntilIdle()
        val closed = checkNotNull(controller.state.value.currentLayer)
        assertEquals("closed", closed.sessionId)
        assertEquals(ReaderSessionStatus.CLOSED, closed.sessionStatus)
        assertFalse(closed.isWritable())
    }

    @Test
    fun `previous discovery filters current and empty Sessions without changing server order`() =
        runTest {
            val controller = controller { _, _, page ->
                ReaderMarginaliaLayerHistoryPage(
                    layers = listOf(
                        previous("newest", count = 3, name = "Recent read"),
                        previous("current", count = 8),
                        previous("empty", count = 0),
                        previous("older", count = 1, status = ReaderSessionStatus.ACTIVE)
                    ),
                    page = page,
                    hasMore = false
                )
            }

            controller.select(profile(), BOOK_ID, session("current", ReaderSessionStatus.CLOSED))
            advanceUntilIdle()

            val layers = controller.state.value.previousLayers
            assertEquals(listOf("newest", "older"), layers.map { it.summary.sessionId })
            assertEquals("Recent read", layers.first().summary.sessionName)
            assertEquals(STARTED_AT, layers.first().summary.startedAt)
            assertEquals(CLOSED_AT, layers.first().summary.closedAt)
            assertEquals(ACTIVITY_AT, layers.first().summary.lastActivityAt)
            assertEquals(3, layers.first().summary.annotationCount)
            assertEquals(ReaderMarginaliaLayerRole.PREVIOUS, layers.first().summary.role)
            assertEquals(ReaderMarginaliaLayerLoadState.NOT_LOADED, layers.first().loadState)
            assertEquals(ReaderMarginaliaLayerVisibility.HIDDEN, layers.first().visibility)
            assertFalse(layers.last().summary.isWritable())
        }

    @Test
    fun `pagination appends uniquely and append failure retains discovered layers`() = runTest {
        var failSecondPage = false
        val requestedPages = mutableListOf<Int>()
        val controller = controller { _, _, page ->
            requestedPages += page
            when (page) {
                1 -> ReaderMarginaliaLayerHistoryPage(
                    listOf(previous("one"), previous("duplicate")),
                    page = 1,
                    hasMore = true
                )

                else -> {
                    if (failSecondPage) error("offline")
                    ReaderMarginaliaLayerHistoryPage(
                        listOf(previous("duplicate"), previous("two")),
                        page = 2,
                        hasMore = false
                    )
                }
            }
        }

        controller.select(profile(), BOOK_ID, session("current"))
        advanceUntilIdle()
        assertTrue(controller.state.value.hasMore)

        failSecondPage = true
        controller.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("one", "duplicate"), layerIds(controller))
        assertEquals(ReaderMarginaliaLayersFailure.UNAVAILABLE, controller.state.value.failure)
        assertTrue(controller.state.value.hasMore)

        failSecondPage = false
        controller.retry()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), requestedPages)
        assertEquals(listOf("one", "duplicate", "two"), layerIds(controller))
        assertFalse(controller.state.value.hasMore)
    }

    @Test
    fun `initial failure leaves current Session usable and retryable`() = runTest {
        var fail = true
        val controller = controller { _, _, page ->
            if (fail) error("unavailable")
            emptyPage(page)
        }

        controller.select(profile(), BOOK_ID, session("current"))
        advanceUntilIdle()

        assertEquals("current", controller.state.value.currentLayer?.sessionId)
        assertEquals(ReaderMarginaliaLayersFailure.UNAVAILABLE, controller.state.value.failure)
        assertTrue(controller.state.value.previousLayers.isEmpty())
        assertEquals(0, controller.state.value.currentPage)

        fail = false
        controller.retry()
        advanceUntilIdle()
        assertNull(controller.state.value.failure)
        assertEquals(1, controller.state.value.currentPage)
    }

    private fun TestScope.controller(loader: ReaderMarginaliaLayerHistoryLoader) =
        ReaderMarginaliaLayersController(
            loader,
            ReaderAnnotationsLoader { _, _ ->
                error("Previous annotations must not load during layer discovery.")
            },
            this
        )

    private fun layerIds(controller: ReaderMarginaliaLayersController) =
        controller.state.value.previousLayers.map { it.summary.sessionId }

    private fun session(
        id: String,
        status: ReaderSessionStatus = ReaderSessionStatus.ACTIVE
    ): ReaderSessionContext = ReaderSessionContext(
        sessionId = id,
        status = status,
        savedProgressCfi = null,
        sessionName = "Named read",
        startedAt = STARTED_AT,
        closedAt = if (status == ReaderSessionStatus.CLOSED) CLOSED_AT else null,
        lastActivityAt = ACTIVITY_AT,
        annotationCount = 4
    )

    private fun previous(
        id: String,
        count: Int = 1,
        name: String? = null,
        status: ReaderSessionStatus = ReaderSessionStatus.CLOSED
    ) = ReaderMarginaliaLayerSummary(
        sessionId = id,
        role = ReaderMarginaliaLayerRole.PREVIOUS,
        sessionStatus = status,
        sessionName = name,
        startedAt = STARTED_AT,
        closedAt = CLOSED_AT,
        lastActivityAt = ACTIVITY_AT,
        annotationCount = count
    )

    private fun emptyPage(page: Int) =
        ReaderMarginaliaLayerHistoryPage(emptyList(), page, hasMore = false)

    private fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-26",
        clientSessionId = "client-session",
        clientName = "Reader",
        clientType = "reader"
    )

    private companion object {
        const val BOOK_ID = "book-1"
        const val STARTED_AT = "2026-08-01T12:00:00Z"
        const val CLOSED_AT = "2026-08-10T12:00:00Z"
        const val ACTIVITY_AT = "2026-08-10T11:00:00Z"
    }
}
