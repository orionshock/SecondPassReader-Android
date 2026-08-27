package com.secondpasslibrary.reader.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsFailure
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderHighlightEditDraft
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderPendingHighlight
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUiIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readerMenuOwnsNestedTocNavigationBackAndReturn() {
        val toc = RecordingReaderToc()
        var exits = 0
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(readerReadyState(toc), onBack = { exits += 1 }, onRetry = {})
            }
        }

        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Open navigation drawer")
                .fetchSemanticsNodes().size
        )
        compose.onNodeWithContentDescription("Reader menu").performClick()
        compose.onNodeWithText("Part One").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Chapter Two").performClick()
        compose.waitForIdle()
        assertEquals(listOf(TEST_CHAPTER_TWO), toc.destinations)

        compose.onNodeWithContentDescription("Reader menu").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, exits)

        compose.onNodeWithContentDescription("Reader menu").performClick()
        compose.onNodeWithText("Return to Book").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun appearancePanelUpdatesAppOwnedAppearanceAndLeavesReaderOpen() {
        val appearance = RecordingReaderAppearance()
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    readerReadyState(RecordingReaderToc(), appearance),
                    onBack = {},
                    onRetry = {},
                    onAppearanceChanged = appearance::record
                )
            }
        }

        compose.onNodeWithContentDescription("Reading appearance").performClick()
        compose.onNodeWithText("Reading appearance").assertIsDisplayed()
        compose.onNodeWithText("Sepia").performClick()
        compose.waitUntil { appearance.appearance.value.theme == ReaderTheme.SEPIA }
        compose.onNodeWithContentDescription("Increase Font size").performClick()
        compose.waitUntil { appearance.appearance.value.fontScale > 1.0 }
        compose.onNodeWithContentDescription("Increase Line height").performClick()
        compose.waitUntil { appearance.appearance.value.lineHeight > 1.4 }
        compose.onNodeWithContentDescription("Publisher styles").performClick()
        compose.waitUntil { appearance.appearance.value.publisherStylesEnabled }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Theme").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Reader menu").assertIsDisplayed()
    }

    @Test
    fun annotationDrawerPresentsContentAndNavigatesExactCfiWithoutExiting() {
        val navigator = RecordingReaderCfiNavigator()
        val annotation = ReaderAnnotation.Highlight(
            id = "annotation-1",
            clientId = "client-annotation-1",
            cfi = TEST_ANNOTATION_CFI,
            locationLabel = "Chapter 3",
            updatedAt = "2026-08-24T13:00:00Z",
            quote = "A selected passage",
            prefix = "Before",
            suffix = "After",
            note = "A Reader note",
            color = ReaderAnnotationColor.BLUE
        )
        var exits = 0
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    state = readerReadyState(navigator = navigator),
                    onBack = { exits += 1 },
                    onRetry = {},
                    annotations = ReaderAnnotationsState(
                        sessionId = "session-1",
                        annotations = listOf(annotation),
                        loaded = true
                    )
                )
            }
        }

        compose.onNodeWithContentDescription("Reading annotations").performClick()
        compose.onAllNodesWithText("1 annotation")[0].assertIsDisplayed()
        compose.onNodeWithText("A selected passage").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open annotation").performClick()
        compose.waitUntil { navigator.destinations.isNotEmpty() }

        assertEquals(listOf(EpubCfi(TEST_ANNOTATION_CFI)), navigator.destinations)
        assertEquals(0, exits)
        assertEquals(0, compose.onAllNodesWithText("A selected passage").fetchSemanticsNodes().size)
    }

    @Test
    fun annotationDrawerShowsCompactLoadingFailureAndEmptyStates() {
        val state = androidx.compose.runtime.mutableStateOf(
            ReaderAnnotationsState(sessionId = "session-1", loading = true)
        )
        var retries = 0
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    state = readerReadyState(),
                    onBack = {},
                    onRetry = {},
                    annotations = state.value,
                    onMarginaliaIntent = {
                        if (it == ReaderMarginaliaIntent.RetryCurrentAnnotations) retries += 1
                    }
                )
            }
        }

        compose.onNodeWithContentDescription("Reading annotations").performClick()
        compose.onNodeWithContentDescription("Close annotations").assertIsDisplayed()
        compose.runOnUiThread {
            state.value = ReaderAnnotationsState(
                sessionId = "session-1",
                failure = ReaderAnnotationsFailure.UNAVAILABLE
            )
        }
        compose.onNodeWithText("Annotations could not be loaded.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        compose.runOnUiThread {
            state.value = ReaderAnnotationsState(sessionId = "session-1", loaded = true)
        }
        compose.onNodeWithText("No annotations in this reading session.").assertIsDisplayed()
    }

    @Test
    fun activeSelectionOffersExactColorsWhileClosedSessionRemainsReadOnly() {
        var createdColor: ReaderAnnotationColor? = null
        var createdNote: String? = null
        var dismissals = 0
        val selection = ReaderSelection(
            EpubCfi(TEST_ANNOTATION_CFI),
            "Selected passage",
            "Before",
            "After",
            "Chapter 03 · 42%"
        )
        val status = androidx.compose.runtime.mutableStateOf(ReaderSessionStatus.ACTIVE)
        val createState = androidx.compose.runtime.mutableStateOf(
            ReaderAnnotationMutationState(
                pendingCreate = ReaderPendingHighlight("client-id", selection)
            )
        )
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    state = readerReadyState(status = status.value),
                    onBack = {},
                    onRetry = {},
                    selection = selection,
                    annotationMutations = createState.value,
                    onAnnotationMutation = { intent ->
                        when (intent) {
                            is ReaderAnnotationMutationIntent.UpdateCreate -> {
                                val pending = createState.value.pendingCreate
                                createState.value = createState.value.copy(
                                    pendingCreate = pending?.copy(
                                        color = intent.color ?: pending.color,
                                        note = intent.note ?: pending.note
                                    )
                                )
                            }

                            ReaderAnnotationMutationIntent.SubmitCreate -> {
                                createdColor = createState.value.pendingCreate?.color
                                createdNote = createState.value.pendingCreate?.note
                            }

                            else -> Unit
                        }
                    },
                    onDismissSelection = { dismissals += 1 }
                )
            }
        }

        compose.onNodeWithContentDescription("Yellow highlight").assertIsSelected()
        compose.onNodeWithText("Note (optional)").performTextInput("Keep this thought")
        compose.onNodeWithContentDescription("Blue highlight").performClick()
        compose.onNodeWithText("Highlight").performClick()
        compose.runOnIdle {
            assertEquals(ReaderAnnotationColor.BLUE, createdColor)
            assertEquals("Keep this thought", createdNote)
        }
        compose.onNodeWithContentDescription("Reading annotations").performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }

        compose.runOnUiThread { status.value = ReaderSessionStatus.CLOSED }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Highlight").fetchSemanticsNodes().size)
    }

    @Test
    fun `Back dismisses highlight create before Reader exit`() {
        var dismissals = 0
        var exits = 0
        val selection = ReaderSelection(
            EpubCfi(TEST_ANNOTATION_CFI),
            "Selected passage",
            null,
            null,
            "Chapter 03 · 42%"
        )
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    state = readerReadyState(),
                    onBack = { exits += 1 },
                    onRetry = {},
                    selection = selection,
                    annotationMutations = ReaderAnnotationMutationState(
                        pendingCreate = ReaderPendingHighlight("client-id", selection)
                    ),
                    onDismissSelection = { dismissals += 1 }
                )
            }
        }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, exits)
        }
    }

    @Test
    fun `active annotations offer allowed mutations while closed Session stays read only`() {
        val highlight = ReaderAnnotation.Highlight(
            id = "server-highlight",
            clientId = "client-highlight",
            cfi = TEST_ANNOTATION_CFI,
            locationLabel = "Chapter 03 · 42%",
            updatedAt = "2026-08-25T00:00:00Z",
            quote = "Selected passage",
            prefix = "Before",
            suffix = "After",
            note = "Original note",
            color = ReaderAnnotationColor.YELLOW
        )
        val bookmark = ReaderAnnotation.Bookmark(
            "server-bookmark",
            "client-bookmark",
            TEST_ANNOTATION_CFI,
            "Chapter 03 · 42%",
            "2026-08-25T00:00:00Z"
        )
        val status = androidx.compose.runtime.mutableStateOf(ReaderSessionStatus.ACTIVE)
        val mutations = androidx.compose.runtime.mutableStateOf(ReaderAnnotationMutationState())
        val observed = mutableListOf<ReaderAnnotationMutationIntent>()
        var bookmarkCreates = 0
        var exits = 0
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    state = readerReadyState(status = status.value),
                    onBack = { exits += 1 },
                    onRetry = {},
                    annotations = ReaderAnnotationsState(
                        sessionId = "session-1",
                        annotations = listOf(highlight, bookmark),
                        loaded = true
                    ),
                    annotationMutations = mutations.value,
                    onCreateBookmark = { bookmarkCreates += 1 },
                    onAnnotationMutation = { intent ->
                        observed += intent
                        mutations.value = reduceMutationUiState(mutations.value, intent)
                    }
                )
            }
        }

        compose.onNodeWithContentDescription("Reading annotations").performClick()
        compose.onNodeWithContentDescription("Bookmark current location").performClick()
        compose.runOnIdle { assertEquals(1, bookmarkCreates) }
        compose.onNodeWithContentDescription("Highlight actions").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Edit highlight").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(ReaderAnnotationMutationIntent.DismissTransient, observed.last())
            assertEquals(0, exits)
        }

        compose.onNodeWithContentDescription("Highlight actions").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Note (optional)").performTextReplacement("Revised note")
        compose.onNodeWithContentDescription("Purple highlight").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(ReaderAnnotationMutationIntent.SaveEdit, observed.last())

        compose.runOnUiThread {
            mutations.value = ReaderAnnotationMutationState()
        }
        compose.onNodeWithContentDescription("Highlight actions").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this highlight?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(ReaderAnnotationMutationIntent.ConfirmDelete, observed.last())

        compose.runOnUiThread {
            mutations.value = ReaderAnnotationMutationState()
        }
        compose.onNodeWithContentDescription("Bookmark actions").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this bookmark?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(ReaderAnnotationMutationIntent.ConfirmDelete, observed.last())
        assertEquals(bookmark, mutations.value.deleting)

        compose.runOnUiThread {
            mutations.value = ReaderAnnotationMutationState()
            status.value = ReaderSessionStatus.CLOSED
        }
        compose.waitForIdle()
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Highlight actions").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Bookmark actions").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Bookmark current location")
                .fetchSemanticsNodes().size
        )
    }

    private fun reduceMutationUiState(
        state: ReaderAnnotationMutationState,
        intent: ReaderAnnotationMutationIntent
    ): ReaderAnnotationMutationState = when (intent) {
        is ReaderAnnotationMutationIntent.BeginEdit ->
            ReaderAnnotationMutationState(editing = ReaderHighlightEditDraft(intent.annotation))

        is ReaderAnnotationMutationIntent.UpdateEdit -> state.copy(
            editing = state.editing?.copy(
                color = intent.color ?: state.editing.color,
                note = intent.note ?: state.editing.note
            )
        )

        is ReaderAnnotationMutationIntent.RequestDelete ->
            ReaderAnnotationMutationState(deleting = intent.annotation)

        ReaderAnnotationMutationIntent.DismissTransient -> ReaderAnnotationMutationState()

        else -> state
    }
}
