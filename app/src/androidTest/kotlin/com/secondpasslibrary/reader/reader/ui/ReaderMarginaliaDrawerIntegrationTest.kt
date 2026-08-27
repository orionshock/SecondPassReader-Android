package com.secondpasslibrary.reader.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerLoadState
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerRole
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerSummary
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerVisibility
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderMarginaliaDrawerIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tabletDrawerSeparatesLayerControlFromSelectedAnnotationCollection() {
        val current = annotation("current-annotation", "Current passage")
        val first = previousLayer("previous-a", "Second read", "Earlier passage")
        val second = previousLayer("previous-b", "First read", "Oldest passage")
        val layerState = mutableStateOf(
            ReaderMarginaliaLayersState(
                currentLayer = currentLayer(),
                previousLayers = listOf(first, second),
                hasMore = true
            )
        )
        val loadRequests = mutableListOf<String>()
        val visibilityRequests = mutableListOf<Pair<String, Boolean>>()
        var loadMore = 0
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    state = readerReadyState(),
                    onBack = {},
                    onRetry = {},
                    annotations = ReaderAnnotationsState(
                        sessionId = "session-1",
                        annotations = listOf(current),
                        loaded = true
                    ),
                    marginaliaLayers = layerState.value,
                    onLoadMarginaliaLayer = { loadRequests += it },
                    onSetMarginaliaLayerVisible = { id, visible ->
                        visibilityRequests += id to visible
                    },
                    onLoadMoreMarginaliaLayers = { loadMore += 1 }
                )
            }
        }

        compose.onNodeWithContentDescription("Reading annotations").performClick()
        compose.onNodeWithContentDescription("Marginalia layer Current Session")
            .assertIsSelected()
        compose.onNodeWithText("Current passage").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Earlier passage").fetchSemanticsNodes().size)

        compose.onNodeWithContentDescription("Marginalia layer Second read").performClick()
        compose.onNodeWithText("Load this historical Session to browse its annotations.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Load Second read").performClick()
        compose.runOnIdle { assertEquals(listOf("previous-a"), loadRequests) }

        compose.runOnUiThread {
            layerState.value = layerState.value.copy(
                previousLayers = listOf(
                    first.copy(
                        loadState = ReaderMarginaliaLayerLoadState.LOADED,
                        annotations = listOf(annotation("a", "Earlier passage"))
                    ),
                    second.copy(
                        loadState = ReaderMarginaliaLayerLoadState.LOADED,
                        visibility = ReaderMarginaliaLayerVisibility.VISIBLE,
                        annotations = listOf(annotation("b", "Oldest passage"))
                    )
                )
            )
        }
        compose.onNodeWithText("Earlier passage").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show Second read").performClick()
        compose.runOnIdle { assertEquals(listOf("previous-a" to true), visibilityRequests) }

        compose.onNodeWithContentDescription("Marginalia layer First read").performClick()
        compose.onNodeWithText("Oldest passage").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Earlier passage").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Hide First read").performClick()
        compose.onNodeWithContentDescription("Marginalia layer First read").assertIsSelected()
        compose.runOnIdle {
            assertEquals("previous-b" to false, visibilityRequests.last())
        }

        compose.onNodeWithText("Load more sessions").performClick()
        compose.runOnIdle { assertEquals(1, loadMore) }
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Highlight actions").fetchSemanticsNodes().size
        )
    }
}

private fun previousLayer(sessionId: String, name: String, quote: String) =
    ReaderPreviousMarginaliaLayer(
        summary = ReaderMarginaliaLayerSummary(
            sessionId = sessionId,
            role = ReaderMarginaliaLayerRole.PREVIOUS,
            sessionStatus = ReaderSessionStatus.CLOSED,
            sessionName = name,
            startedAt = "2026-01-01T00:00:00Z",
            closedAt = "2026-02-01T00:00:00Z",
            lastActivityAt = "2026-02-01T00:00:00Z",
            annotationCount = 1
        ),
        annotations = listOf(annotation("$sessionId-annotation", quote))
    )

private fun currentLayer() = ReaderMarginaliaLayerSummary(
    sessionId = "session-1",
    role = ReaderMarginaliaLayerRole.CURRENT,
    sessionStatus = ReaderSessionStatus.ACTIVE,
    sessionName = null,
    startedAt = null,
    closedAt = null,
    lastActivityAt = null,
    annotationCount = 1
)

private fun annotation(id: String, quote: String) = ReaderAnnotation.Highlight(
    id = id,
    clientId = "client-$id",
    cfi = TEST_ANNOTATION_CFI,
    locationLabel = "Chapter 03 · 42%",
    updatedAt = "2026-08-26T00:00:00Z",
    quote = quote,
    prefix = "Before",
    suffix = "After",
    note = null,
    color = ReaderAnnotationColor.YELLOW
)
