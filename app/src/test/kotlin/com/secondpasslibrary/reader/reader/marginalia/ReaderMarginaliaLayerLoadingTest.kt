package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderMarginaliaLayerLoadingTest {
    @Test
    fun `one exact previous layer loads in server order and remains hidden`() = runTest {
        val requested = mutableListOf<String>()
        val annotations = listOf(bookmark("bookmark", "shared"), highlight("highlight", "other"))
        val controller = controller(
            ReaderAnnotationsLoader { _, sessionId ->
                requested += sessionId
                annotations
            }
        )
        discover(controller, "previous")

        controller.loadLayer("previous")
        advanceUntilIdle()

        val layer = layer(controller, "previous")
        assertEquals(listOf("previous"), requested)
        assertEquals(ReaderMarginaliaLayerLoadState.LOADED, layer.loadState)
        assertEquals(ReaderMarginaliaLayerVisibility.HIDDEN, layer.visibility)
        assertEquals(listOf("bookmark", "highlight"), layer.annotations.map { it.id })
        assertEquals(
            "Selected quote",
            (layer.annotations.last() as ReaderAnnotation.Highlight).quote
        )
    }

    @Test
    fun `current unknown loading and loaded requests do not fetch`() = runTest {
        var requests = 0
        val pending = CompletableDeferred<List<ReaderAnnotation>>()
        val controller = controller(
            ReaderAnnotationsLoader { _, _ ->
                requests += 1
                pending.await()
            }
        )
        discover(controller, "previous")

        controller.loadLayer("current")
        controller.loadLayer("unknown")
        controller.loadLayer("previous")
        controller.loadLayer("previous")
        runCurrent()
        assertEquals(1, requests)
        assertEquals(
            ReaderMarginaliaLayerLoadState.LOADING,
            layer(controller, "previous").loadState
        )

        pending.complete(listOf(bookmark("bookmark", "client")))
        advanceUntilIdle()
        controller.loadLayer("previous")
        advanceUntilIdle()
        assertEquals(1, requests)
    }

    @Test
    fun `failed layer retains summary and explicit retry succeeds`() = runTest {
        var attempts = 0
        val controller = controller(
            ReaderAnnotationsLoader { _, _ ->
                attempts += 1
                if (attempts == 1) throw SplClientException.ServerUnreachable()
                listOf(bookmark("bookmark", "client"))
            }
        )
        discover(controller, "previous")

        controller.loadLayer("previous")
        advanceUntilIdle()
        val failed = layer(controller, "previous")
        assertEquals(ReaderMarginaliaLayerLoadState.FAILED, failed.loadState)
        assertEquals(ReaderMarginaliaLayerAnnotationsFailure.UNAVAILABLE, failed.loadFailure)
        assertEquals("previous", failed.summary.sessionId)
        assertTrue(failed.annotations.isEmpty())

        controller.loadLayer("previous")
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertEquals(ReaderMarginaliaLayerLoadState.LOADED, layer(controller, "previous").loadState)
    }

    @Test
    fun `layer failures are isolated and same client ID remains Session scoped`() = runTest {
        val controller = controller(
            ReaderAnnotationsLoader { _, sessionId ->
                if (sessionId == "layer-a") throw IllegalStateException("missing")
                listOf(bookmark("bookmark-$sessionId", "same-client"))
            }
        )
        discover(controller, "layer-a", "layer-b", "layer-c")

        controller.loadLayer("layer-a")
        controller.loadLayer("layer-b")
        controller.loadLayer("layer-c")
        advanceUntilIdle()

        assertEquals(ReaderMarginaliaLayerLoadState.FAILED, layer(controller, "layer-a").loadState)
        val loadedLayers = listOf(layer(controller, "layer-b"), layer(controller, "layer-c"))
        assertTrue(loadedLayers.all { it.loadState == ReaderMarginaliaLayerLoadState.LOADED })
        assertTrue(loadedLayers.all { it.annotations.single().clientId == "same-client" })
        assertEquals(listOf("layer-b", "layer-c"), loadedLayers.map { it.summary.sessionId })
    }

    @Test
    fun `late old Reader result cannot publish into replacement context`() = runTest {
        val oldResult = CompletableDeferred<List<ReaderAnnotation>>()
        val controller = controller(
            ReaderAnnotationsLoader { _, sessionId ->
                if (sessionId == "old-layer") {
                    withContext(NonCancellable) { oldResult.await() }
                } else {
                    listOf(bookmark("new-bookmark", "client"))
                }
            }
        )
        discover(controller, "old-layer")
        controller.loadLayer("old-layer")
        runCurrent()

        discoveredByBook["book-2"] = emptyList()
        controller.select(profile(), "book-2", current("new-current"))
        runCurrent()
        oldResult.complete(listOf(bookmark("stale", "same-client")))
        advanceUntilIdle()

        assertEquals("new-current", controller.state.value.currentLayer?.sessionId)
        assertFalse(
            controller.state.value.previousLayers.any {
                it.summary.sessionId == "old-layer"
            }
        )
    }

    private fun TestScope.controller(annotationsLoader: ReaderAnnotationsLoader) =
        ReaderMarginaliaLayersController(
            historyLoader = ReaderMarginaliaLayerHistoryLoader { _, bookId, page ->
                ReaderMarginaliaLayerHistoryPage(
                    discoveredByBook.getValue(bookId),
                    page,
                    hasMore = false
                )
            },
            annotationsLoader = annotationsLoader,
            scope = this
        )

    private suspend fun TestScope.discover(
        controller: ReaderMarginaliaLayersController,
        vararg sessionIds: String
    ) {
        discoveredByBook[BOOK_ID] = sessionIds.map(::previous)
        controller.select(profile(), BOOK_ID, current("current"))
        advanceUntilIdle()
    }

    private fun layer(controller: ReaderMarginaliaLayersController, sessionId: String) =
        controller.state.value.previousLayers.single { it.summary.sessionId == sessionId }

    private fun current(sessionId: String) =
        ReaderSessionContext(sessionId, ReaderSessionStatus.ACTIVE, savedProgressCfi = null)

    private fun previous(sessionId: String) = ReaderMarginaliaLayerSummary(
        sessionId = sessionId,
        role = ReaderMarginaliaLayerRole.PREVIOUS,
        sessionStatus = ReaderSessionStatus.CLOSED,
        sessionName = null,
        startedAt = "start",
        closedAt = "closed",
        lastActivityAt = "activity",
        annotationCount = 1
    )

    private fun bookmark(id: String, clientId: String) = ReaderAnnotation.Bookmark(
        id = id,
        clientId = clientId,
        cfi = CFI,
        locationLabel = "Chapter 01 · 10%",
        updatedAt = "updated"
    )

    private fun highlight(id: String, clientId: String) = ReaderAnnotation.Highlight(
        id = id,
        clientId = clientId,
        cfi = CFI,
        locationLabel = "Chapter 01 · 10%",
        updatedAt = "updated",
        quote = "Selected quote",
        prefix = "Before",
        suffix = "After",
        note = "Note",
        color = ReaderAnnotationColor.BLUE
    )

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

    private val discoveredByBook = mutableMapOf<String, List<ReaderMarginaliaLayerSummary>>()

    private companion object {
        const val BOOK_ID = "book-1"
        const val CFI = "epubcfi(/6/2!/4/2:3)"
    }
}
