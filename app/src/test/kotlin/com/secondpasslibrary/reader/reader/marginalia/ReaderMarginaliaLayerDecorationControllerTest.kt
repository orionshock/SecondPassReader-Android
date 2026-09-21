package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationActivation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationFailure
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderMarginaliaLayerDecorationControllerTest {
    @Test
    fun `loaded layer visibility is explicit and unloaded layers cannot become visible`() =
        runTest {
            val controller = ReaderMarginaliaLayersController(
                historyLoader = ReaderMarginaliaLayerHistoryLoader { _, _, page ->
                    ReaderMarginaliaLayerHistoryPage(
                        listOf(previousSummary("previous")),
                        page,
                        hasMore = false
                    )
                },
                annotationsLoader = ReaderAnnotationsLoader { _, _ -> listOf(highlight("loaded")) },
                scope = this
            )
            controller.select(profile(), "book", currentSession())
            advanceUntilIdle()

            assertEquals(
                ReaderMarginaliaLayerVisibilityResult.NOT_LOADED,
                controller.setLayerVisible("previous", visible = true)
            )
            assertEquals(
                ReaderMarginaliaLayerVisibility.HIDDEN,
                controller.state.value.previousLayers.single().visibility
            )
            assertEquals(
                ReaderMarginaliaLayerVisibilityResult.NOT_FOUND,
                controller.setLayerVisible("current", visible = true)
            )

            controller.loadLayer("previous")
            advanceUntilIdle()
            assertEquals(
                ReaderMarginaliaLayerVisibilityResult.UPDATED,
                controller.setLayerVisible("previous", visible = true)
            )
            assertEquals(
                ReaderMarginaliaLayerVisibility.VISIBLE,
                controller.state.value.previousLayers.single().visibility
            )
            assertEquals(
                ReaderMarginaliaLayerVisibilityResult.UPDATED,
                controller.setLayerVisible("previous", visible = false)
            )
        }

    @Test
    fun `independent visible Session groups exclude bookmarks and preserve same client ID`() =
        runTest {
            val target = RecordingDecorations()
            val controller = ReaderMarginaliaLayerDecorationController()
            val layers = listOf(
                visibleLayer("session-a", highlight("a", "same-client"), bookmark("bookmark-a")),
                visibleLayer("session-b", highlight("b", "same-client"))
            )

            controller.replace("current", target, layers)

            assertEquals(
                setOf("session-a", "session-b"),
                target.groups.keys.map {
                    it.sessionId
                }.toSet()
            )
            assertEquals(
                listOf("a"),
                target.groups.getValue(group("session-a")).map {
                    it.annotationId
                }
            )
            assertEquals(
                listOf("b"),
                target.groups.getValue(group("session-b")).map {
                    it.annotationId
                }
            )
            assertFalse(target.groups.values.flatten().any { it.kind.name == "BOOKMARK" })
        }

    @Test
    fun `hiding one layer clears only its group and leaves current decorations untouched`() =
        runTest {
            val target = RecordingDecorations()
            val controller = ReaderMarginaliaLayerDecorationController()
            controller.replace(
                "current",
                target,
                listOf(
                    visibleLayer("session-a", highlight("a")),
                    visibleLayer("session-b", highlight("b"))
                )
            )
            controller.replace(
                "current",
                target,
                listOf(visibleLayer("session-b", highlight("b")))
            )

            assertEquals(listOf(group("session-a")), target.clearedGroups)
            assertTrue(group("session-b") in target.groups)
            assertEquals(0, target.currentClearCount)
        }

    @Test
    fun `Reader Session replacement clears old previous groups before installing new owner`() =
        runTest {
            val first = RecordingDecorations()
            val second = RecordingDecorations()
            val controller = ReaderMarginaliaLayerDecorationController()
            controller.replace("current-a", first, listOf(visibleLayer("old", highlight("old"))))
            controller.replace("current-b", second, listOf(visibleLayer("new", highlight("new"))))

            assertEquals(listOf(group("old")), first.clearedGroups)
            assertTrue(group("new") in second.groups)
        }

    private class RecordingDecorations : ReaderAnnotationDecorations {
        override val failures = MutableStateFlow(
            emptyMap<String, ReaderAnnotationDecorationFailure>()
        )
        override val activations = emptyFlow<ReaderAnnotationDecorationActivation>()
        val groups =
            mutableMapOf<
                ReaderAnnotationDecorationGroupId.Previous,
                List<ReaderAnnotationDecoration>
                >()
        val clearedGroups = mutableListOf<ReaderAnnotationDecorationGroupId.Previous>()
        var currentClearCount = 0

        override suspend fun replace(
            groupId: ReaderAnnotationDecorationGroupId,
            decorations: List<ReaderAnnotationDecoration>
        ) {
            when (groupId) {
                ReaderAnnotationDecorationGroupId.Current -> Unit
                is ReaderAnnotationDecorationGroupId.Previous -> groups[groupId] = decorations
            }
        }

        override suspend fun clear(groupId: ReaderAnnotationDecorationGroupId) {
            when (groupId) {
                ReaderAnnotationDecorationGroupId.Current -> currentClearCount += 1

                is ReaderAnnotationDecorationGroupId.Previous -> {
                    clearedGroups += groupId
                    groups.remove(groupId)
                }
            }
        }
    }
}

private fun visibleLayer(sessionId: String, vararg annotations: ReaderAnnotation) =
    layer(sessionId, ReaderMarginaliaLayerLoadState.LOADED).copy(
        visibility = ReaderMarginaliaLayerVisibility.VISIBLE,
        annotations = annotations.toList()
    )

private fun layer(sessionId: String, state: ReaderMarginaliaLayerLoadState) =
    ReaderPreviousMarginaliaLayer(
        summary = ReaderMarginaliaLayerSummary(
            sessionId = sessionId,
            role = ReaderMarginaliaLayerRole.PREVIOUS,
            sessionStatus = ReaderSessionStatus.CLOSED,
            sessionName = null,
            startedAt = "start",
            closedAt = "closed",
            lastActivityAt = "activity",
            annotationCount = 1
        ),
        loadState = state
    )

private fun highlight(id: String, clientId: String = "client-$id") = ReaderAnnotation.Highlight(
    id = id,
    clientId = clientId,
    cfi = "epubcfi(/6/4!/4/2,/1:0,/1:4)",
    locationLabel = "Chapter 1",
    updatedAt = "updated",
    quote = "Text",
    prefix = null,
    suffix = null,
    note = null,
    color = ReaderAnnotationColor.BLUE
)

private fun bookmark(id: String) = ReaderAnnotation.Bookmark(
    id = id,
    clientId = "client-$id",
    cfi = "epubcfi(/6/4!/4/2:0)",
    locationLabel = "Chapter 1",
    updatedAt = "updated"
)

private fun group(sessionId: String) = ReaderAnnotationDecorationGroupId.Previous(sessionId)

private fun previousSummary(sessionId: String) =
    layer(sessionId, ReaderMarginaliaLayerLoadState.NOT_LOADED).summary

private fun currentSession() =
    ReaderSessionContext("current", ReaderSessionStatus.ACTIVE, savedProgressCfi = null)

private fun profile() = ConnectionProfile(
    serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
    serverOrigin = "https://library.example",
    libraryBaseUrl = "https://library.example",
    serverName = "Library",
    serverDescription = "",
    serverVersion = "1",
    serverReleaseDate = "2026-08-26",
    clientSessionId = "client-session",
    clientName = "Reader",
    clientType = "reader"
)
