package com.secondpasslibrary.reader.reader.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderReadOnlyHighlightDetail
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderHighlightEditDraft
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderPendingHighlight
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_CURRENT_SESSION_EDIT_TAG
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_SESSION_NAME_FIELD_TAG
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_SESSION_NOTES_FIELD_TAG
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_SESSION_SAVE_TAG
import com.secondpasslibrary.reader.reader.navigation.ReaderNavigationIntent
import com.secondpasslibrary.reader.reader.presentation.ReaderMarginaliaPresentationState
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadata
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReaderMarginaliaUiIntegrationTest : ReaderUiIntegrationTestSupport() {
    @Test
    fun annotationDrawerPresentsContentAndNavigatesExactCfiWithoutExiting() {
        val navigator = RecordingReaderCfiNavigator()
        val navigationIntents = mutableListOf<ReaderNavigationIntent>()
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
                ReaderTestScreen(
                    state = readerReadyState(navigator = navigator),
                    onBack = { exits += 1 },
                    onRetry = {},
                    onNavigationIntent = { navigationIntents += it },
                    marginalia = ReaderMarginaliaPresentationState(
                        annotations = ReaderAnnotationsState(
                            sessionId = "session-1",
                            annotations = listOf(annotation),
                            loaded = true
                        )
                    )
                )
            }
        }

        compose.onNodeWithContentDescription("Open Marginalia").performClick()
        compose.onNodeWithText("A selected passage").assertIsDisplayed()
        compose.onNodeWithContentDescription("More highlight actions").performClick()
        assertEquals(emptyList<EpubCfi>(), navigator.destinations)
        compose.onNodeWithText("Go to").performClick()
        compose.waitUntil { navigationIntents.isNotEmpty() }

        assertEquals(
            listOf(ReaderNavigationIntent.GoToAnnotation(annotation)),
            navigationIntents
        )
        assertTrue(navigator.destinations.isEmpty())
        assertEquals(0, exits)
        assertEquals(0, compose.onAllNodesWithText("A selected passage").fetchSemanticsNodes().size)
    }

    @Test
    fun marginaliaDrawerRetriesFailedLoad() {
        val state = androidx.compose.runtime.mutableStateOf(
            ReaderAnnotationsState(sessionId = "session-1", loading = true)
        )
        var retries = 0
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    state = readerReadyState(),
                    onBack = {},
                    onRetry = {},
                    onMarginaliaIntent = {
                        if (it == ReaderMarginaliaIntent.RetryCurrentAnnotations) retries += 1
                    },
                    marginalia = ReaderMarginaliaPresentationState(
                        annotations = state.value
                    )
                )
            }
        }

        compose.onNodeWithContentDescription("Open Marginalia").performClick()
        compose.onNodeWithContentDescription("Close Marginalia").assertIsDisplayed()
        compose.runOnUiThread {
            state.value = ReaderAnnotationsState(
                sessionId = "session-1",
                failure = ReaderAnnotationsFailure.UNAVAILABLE
            )
        }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun activeSelectionOffersExactColorsWhileClosedSessionRemainsReadOnly() {
        var createdColor: ReaderAnnotationColor? = null
        var createdNote: String? = null
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
                ReaderTestScreen(
                    state = readerReadyState(status = status.value),
                    onBack = {},
                    onRetry = {},
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
                                createState.value = ReaderAnnotationMutationState()
                            }

                            is ReaderAnnotationMutationIntent.SubmitQuickCreate -> {
                                createdColor = intent.color
                                createdNote = ""
                                createState.value = createState.value.copy(
                                    pendingCreate = createState.value.pendingCreate?.copy(
                                        color = intent.color,
                                        note = ""
                                    )
                                )
                            }

                            ReaderAnnotationMutationIntent.OpenCreateNote -> {
                                createState.value = createState.value.copy(
                                    createNoteEditorVisible = true
                                )
                            }

                            ReaderAnnotationMutationIntent.CancelCreateNote -> {
                                createState.value = createState.value.copy(
                                    createNoteEditorVisible = false
                                )
                            }

                            else -> Unit
                        }
                    },
                    marginalia = ReaderMarginaliaPresentationState(
                        selection = selection,
                        annotationMutations = createState.value
                    )
                )
            }
        }

        ReaderAnnotationColor.entries.forEach { color ->
            compose.onNodeWithContentDescription(
                "${color.name.lowercase().replaceFirstChar(Char::uppercase)} highlight"
            ).assertIsDisplayed()
        }
        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Blue highlight").performClick()
        compose.runOnIdle {
            assertEquals(ReaderAnnotationColor.BLUE, createdColor)
            assertEquals("", createdNote)
        }
        assertEquals(0, compose.onAllNodesWithText("Highlight").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Add note").performClick()
        compose.onNodeWithText("Selected passage").assertIsDisplayed()
        compose.onNodeWithText("Note (optional)").performTextInput("Keep this thought")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Add note").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add note").performClick()
        compose.onNodeWithText("Keep this thought").assertIsDisplayed()
        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle { assertEquals("Keep this thought", createdNote) }

        compose.runOnUiThread {
            createState.value = ReaderAnnotationMutationState(
                pendingCreate = ReaderPendingHighlight("closed-client-id", selection)
            )
            status.value = ReaderSessionStatus.CLOSED
        }
        compose.waitForIdle()
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Yellow highlight").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Add note").fetchSemanticsNodes().size
        )
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
                ReaderTestScreen(
                    state = readerReadyState(),
                    onBack = { exits += 1 },
                    onRetry = {},
                    onDismissSelection = { dismissals += 1 },
                    marginalia = ReaderMarginaliaPresentationState(
                        selection = selection,
                        annotationMutations = ReaderAnnotationMutationState(
                            pendingCreate = ReaderPendingHighlight("client-id", selection)
                        )
                    )
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
                ReaderTestScreen(
                    state = readerReadyState(status = status.value),
                    onBack = { exits += 1 },
                    onRetry = {},
                    onCreateBookmark = { bookmarkCreates += 1 },
                    onAnnotationMutation = { intent ->
                        observed += intent
                        mutations.value = reduceMutationUiState(mutations.value, intent)
                    },
                    marginalia = ReaderMarginaliaPresentationState(
                        annotations = ReaderAnnotationsState(
                            sessionId = "session-1",
                            annotations = listOf(highlight, bookmark),
                            loaded = true
                        ),
                        annotationMutations = mutations.value
                    )
                )
            }
        }

        compose.onNodeWithContentDescription("Open Marginalia").performClick()
        compose.onNodeWithContentDescription("Bookmark current location").performClick()
        compose.runOnIdle { assertEquals(1, bookmarkCreates) }
        compose.onNodeWithContentDescription("More highlight actions").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Edit highlight").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(ReaderAnnotationMutationIntent.DismissTransient, observed.last())
            assertEquals(0, exits)
        }

        compose.onNodeWithContentDescription("More highlight actions").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Note (optional)").performTextReplacement("Revised note")
        compose.onNodeWithContentDescription("Purple highlight").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(ReaderAnnotationMutationIntent.SaveEdit, observed.last())

        compose.runOnUiThread {
            mutations.value = ReaderAnnotationMutationState()
        }
        compose.onNodeWithContentDescription("More highlight actions").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this highlight?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(ReaderAnnotationMutationIntent.ConfirmDelete, observed.last())

        compose.runOnUiThread {
            mutations.value = ReaderAnnotationMutationState()
        }
        compose.onNodeWithContentDescription("More bookmark actions").performClick()
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
        compose.onNodeWithContentDescription("More highlight actions").performClick()
        compose.onNodeWithText("Go to").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Edit").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Delete").fetchSemanticsNodes().size)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("More bookmark actions").performClick()
        compose.onNodeWithText("Go to").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Delete").fetchSemanticsNodes().size)
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Bookmark current location")
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun `active current Session metadata editor preserves explicit name and note intents`() {
        val metadata = androidx.compose.runtime.mutableStateOf(
            ReaderSessionMetadataState(
                metadata = ReaderSessionMetadata("session-1", "Morning read", "Original note")
            )
        )
        val intents = mutableListOf<ReaderMarginaliaIntent>()
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    state = readerReadyState(status = ReaderSessionStatus.ACTIVE),
                    onBack = {},
                    onRetry = {},
                    onMarginaliaIntent = { intent ->
                        intents += intent
                        metadata.value = when (intent) {
                            ReaderMarginaliaIntent.EditCurrentSessionMetadata ->
                                metadata.value.copy(
                                    editorOpen = true,
                                    draftName = metadata.value.metadata?.name.orEmpty(),
                                    draftNotes = metadata.value.metadata?.notes.orEmpty()
                                )

                            is ReaderMarginaliaIntent.ChangeCurrentSessionName ->
                                metadata.value.copy(draftName = intent.name)

                            is ReaderMarginaliaIntent.ChangeCurrentSessionNotes ->
                                metadata.value.copy(draftNotes = intent.notes)

                            else -> metadata.value
                        }
                    },
                    marginalia = ReaderMarginaliaPresentationState(
                        annotations = ReaderAnnotationsState(
                            sessionId = "session-1",
                            loaded = true
                        ),
                        sessionMetadata = metadata.value
                    )
                )
            }
        }

        compose.onNodeWithContentDescription("Open Marginalia").performClick()
        compose.onAllNodesWithText("Morning read")[1].assertIsDisplayed()
        compose.onNodeWithTag(READER_CURRENT_SESSION_EDIT_TAG).performClick()
        compose.onNodeWithTag(READER_SESSION_NAME_FIELD_TAG)
            .performTextReplacement("Evening read")
        compose.onNodeWithTag(READER_SESSION_NOTES_FIELD_TAG)
            .performTextReplacement("  exact\n note  ")
        compose.onNodeWithTag(READER_SESSION_SAVE_TAG).performClick()

        assertEquals(
            ReaderMarginaliaIntent.ChangeCurrentSessionName("Evening read"),
            intents.filterIsInstance<ReaderMarginaliaIntent.ChangeCurrentSessionName>().last()
        )
        assertEquals(
            ReaderMarginaliaIntent.ChangeCurrentSessionNotes("  exact\n note  "),
            intents.filterIsInstance<ReaderMarginaliaIntent.ChangeCurrentSessionNotes>().last()
        )
        assertEquals(ReaderMarginaliaIntent.SaveCurrentSessionMetadata, intents.last())
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

    @Test
    fun `historical viewport highlight detail is read only and dismissible`() {
        val highlight = ReaderAnnotation.Highlight(
            id = "previous-highlight",
            clientId = "shared-client",
            cfi = TEST_ANNOTATION_CFI,
            locationLabel = "Chapter 03 · 42%",
            updatedAt = "2026-08-25T00:00:00Z",
            quote = "Historical quote",
            prefix = null,
            suffix = null,
            note = "Historical note",
            color = ReaderAnnotationColor.PURPLE
        )
        var dismissed = false
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    state = readerReadyState(),
                    onBack = {},
                    onRetry = {},
                    onDismissHighlightDetail = { dismissed = true },
                    marginalia = ReaderMarginaliaPresentationState(
                        highlightDetail = ReaderReadOnlyHighlightDetail(
                            sessionId = "previous-session",
                            annotation = highlight,
                            sessionName = "First read",
                            startedAt = "2026-08-01T00:00:00Z",
                            historical = true
                        )
                    )
                )
            }
        }

        compose.onNodeWithText("First read").assertIsDisplayed()
        compose.onNodeWithText("Historical · Read only").assertIsDisplayed()
        compose.onNodeWithText("Historical quote").assertIsDisplayed()
        compose.onNodeWithText("Historical note").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Edit").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Delete").fetchSemanticsNodes().size)
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
