package com.secondpasslibrary.reader.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
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
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatusScope
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadata
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.READER_TOC_BODY_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_EYEBROW_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_FOOTER_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_HEADER_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_TITLE_TAG
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.toc.ReaderTocEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUiIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readerChromeUsesSeparatedFloatingClustersAndProtectsPublicationTop() {
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(readerReadyState(), onBack = {}, onRetry = {})
            }
        }

        val positioner = compose.onNodeWithTag(READER_CHROME_POSITIONER_TAG)
            .getUnclippedBoundsInRoot()
        val left = compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG)
            .getUnclippedBoundsInRoot()
        val right = compose.onNodeWithTag(READER_CHROME_RIGHT_CLUSTER_TAG)
            .getUnclippedBoundsInRoot()
        val publication = compose.onNodeWithTag(TEST_PUBLICATION_CONTENT_TAG)
            .getUnclippedBoundsInRoot()

        assertTrue(left.right - left.left < positioner.right - positioner.left)
        assertTrue(right.right - right.left < positioner.right - positioner.left)
        assertTrue(left.right < right.left)
        assertTrue(publication.top >= left.bottom)
        assertTrue(publication.top >= right.bottom)
        compose.onAllNodesWithText("A deliberately long Reader title that remains one line")[0]
            .assertIsDisplayed()
    }

    @Test
    fun readerHudShowsAmbientStatusAutoHidesAndReturnsOnPublicationTap() {
        val hud = RecordingReaderHudEvents(
            ReaderReadingStatus(8, ReaderReadingStatusScope.SECTION)
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    readerReadyState(hudEvents = hud),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithTag(com.secondpasslibrary.reader.reader.ui.hud.READER_HUD_CLOCK_TAG)
            .assertIsDisplayed()
        compose.onNodeWithText("8 pages left in section").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open table of contents").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(3_500)
        compose.onNodeWithContentDescription("Open table of contents").assertIsNotDisplayed()
        compose.runOnIdle { hud.tap() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Open table of contents").assertIsDisplayed()
    }

    @Test
    fun readerMenuOwnsNestedTocNavigationDismissalAndCloseBook() {
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
        compose.onNodeWithContentDescription("Open table of contents").performClick()
        compose.onNodeWithText("Part One").assertIsDisplayed()
        compose.onNodeWithText("Part One").assertIsSelected()
        val eyebrow = compose.onNodeWithTag(READER_TOC_EYEBROW_TAG).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag(READER_TOC_TITLE_TAG).getUnclippedBoundsInRoot()
        assertTrue(eyebrow.bottom <= title.top)
        compose.onNodeWithContentDescription("Close table of contents").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close table of contents").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Part One").assertIsNotDisplayed()

        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performTouchInput {
            down(Offset(width - 12f, height / 2f))
            up()
        }
        compose.onNodeWithContentDescription("Open Chapter Two").performClick()
        compose.waitForIdle()
        assertEquals(listOf(TEST_CHAPTER_TWO), toc.destinations)

        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            up()
        }
        compose.onNodeWithText("Part One").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, exits)

        compose.onNodeWithContentDescription("Open table of contents").performClick()
        compose.onRoot().performTouchInput {
            down(Offset(width - 4f, height / 2f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Part One").assertIsNotDisplayed()
        assertEquals(0, exits)

        compose.onNodeWithContentDescription("Open table of contents").performClick()
        compose.onNodeWithText("Close book").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun longTocKeepsHeaderAndExitFooterFixedWhileBodyScrolls() {
        val entries = (1..60).map { chapter ->
            ReaderTocEntry(
                title = "Chapter $chapter",
                target = ReaderPublicationTarget("text/chapter-$chapter.xhtml")
            )
        }
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    readerReadyState(RecordingReaderToc(entries, resource = null)),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Open table of contents").performClick()
        val headerBefore = compose.onNodeWithTag(READER_TOC_HEADER_TAG).getUnclippedBoundsInRoot()
        val footerBefore = compose.onNodeWithTag(READER_TOC_FOOTER_TAG).getUnclippedBoundsInRoot()
        compose.onNodeWithTag(READER_TOC_BODY_TAG).performScrollToNode(hasText("Chapter 60"))
        compose.onNodeWithText("Chapter 60").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText("Close book").assertIsDisplayed()

        assertEquals(
            headerBefore,
            compose.onNodeWithTag(READER_TOC_HEADER_TAG).getUnclippedBoundsInRoot()
        )
        assertEquals(
            footerBefore,
            compose.onNodeWithTag(READER_TOC_FOOTER_TAG).getUnclippedBoundsInRoot()
        )
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
        compose.onNodeWithContentDescription("Open table of contents").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Highlight actions").performClick()
        assertEquals(emptyList<EpubCfi>(), navigator.destinations)
        compose.onNodeWithText("Go to").performClick()
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
                    }
                )
            }
        }

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
        compose.onNodeWithContentDescription("Highlight actions").performClick()
        compose.onNodeWithText("Go to").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Edit").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Delete").fetchSemanticsNodes().size)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Bookmark actions").performClick()
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
                ReaderScreen(
                    state = readerReadyState(status = ReaderSessionStatus.ACTIVE),
                    onBack = {},
                    onRetry = {},
                    annotations = ReaderAnnotationsState(sessionId = "session-1", loaded = true),
                    sessionMetadata = metadata.value,
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
                    }
                )
            }
        }

        compose.onNodeWithContentDescription("Reading annotations").performClick()
        compose.onAllNodesWithText("Morning read")[1].assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit current session").performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Evening read")
        compose.onNodeWithText("Session note").performTextReplacement("  exact\n note  ")
        compose.onNodeWithText("Save").performClick()

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
}
