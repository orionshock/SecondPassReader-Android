package com.secondpasslibrary.reader.reader.presentation

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.MarginaliaTestFakeAppearanceController
import com.secondpasslibrary.reader.reader.MarginaliaTestFakeAppearanceStore
import com.secondpasslibrary.reader.reader.MarginaliaTestFakeCfiNavigator
import com.secondpasslibrary.reader.reader.MarginaliaTestFakeLayerPreferenceStore
import com.secondpasslibrary.reader.reader.MarginaliaTestFakeLayerVisibilityStore
import com.secondpasslibrary.reader.reader.MarginaliaTestFakeLocalReaderStateStore
import com.secondpasslibrary.reader.reader.MarginaliaTestRecordingEngine
import com.secondpasslibrary.reader.reader.ReaderProgressRestore
import com.secondpasslibrary.reader.reader.ReaderSessionAuthority
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderBookmarkHudIntent
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarks
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarksResolver
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationActivation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionEvents
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryPage
import com.secondpasslibrary.reader.reader.marginaliaTestHighlight
import com.secondpasslibrary.reader.reader.marginaliaTestPreviousLayer
import com.secondpasslibrary.reader.reader.marginaliaTestProfile
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadata
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataWriter
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderMarginaliaPresentationControllerTest {
    @Test
    fun `selection opens annotation draft and dismiss clears renderer selection`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.open()
        runCurrent()
        fixture.selectionChanges.emit(Unit)
        runCurrent()
        assertEquals("selected", fixture.owner.state.value.selection?.selectedText)
        assertEquals(
            fixture.owner.state.value.selection,
            fixture.owner.state.value.annotationMutations.pendingCreate?.selection
        )
        fixture.owner.dismissSelection()
        runCurrent()
        assertEquals(null, fixture.owner.state.value.selection)
        assertEquals(null, fixture.owner.state.value.annotationMutations.pendingCreate)
        assertEquals(1, fixture.selectionClears)
        fixture.owner.close()
    }

    @Test
    fun `snapshot routes layers bookmarks mutations and metadata with one decoration owner`() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.open()
            runCurrent()
            val owner = fixture.owner
            val initial = owner.state.value
            assertEquals("current", initial.annotations.sessionId)
            assertEquals(listOf(fixture.bookmark), initial.pageBookmarks.bookmarks)
            assertEquals(1, initial.marginaliaLayers.previousLayers.size)
            assertEquals(1, fixture.engine.decorations.previousReplacements)

            repeat(2) { owner.selectEntry(marginaliaTestProfile(), "profile", "book", null) }
            owner.acceptMarginalia(ReaderMarginaliaIntent.HideAllPreviousLayers)
            runCurrent()
            assertEquals(1, fixture.engine.decorations.previousClears)
            owner.acceptMarginalia(ReaderMarginaliaIntent.ShowAllPreviousLayers)
            runCurrent()
            assertEquals(2, fixture.engine.decorations.previousReplacements)

            owner.acceptBookmark(ReaderBookmarkHudIntent.Remove(fixture.bookmark))
            runCurrent()
            assertEquals(fixture.bookmark, owner.state.value.annotationMutations.deleting)
            owner.mutateAnnotation(ReaderAnnotationMutationIntent.ConfirmDelete)
            runCurrent()
            assertEquals(1, fixture.store.mutations.size)
            assertTrue(owner.state.value.pageBookmarks.bookmarks.isEmpty())
            assertEquals(1, fixture.syncRequests)

            owner.acceptMarginalia(ReaderMarginaliaIntent.EditCurrentSessionMetadata)
            owner.acceptMarginalia(ReaderMarginaliaIntent.ChangeCurrentSessionName("Renamed"))
            owner.acceptMarginalia(ReaderMarginaliaIntent.SaveCurrentSessionMetadata)
            runCurrent()
            assertEquals("Renamed", owner.state.value.sessionMetadata.metadata?.name)
            assertEquals("Renamed", owner.state.value.marginaliaLayers.currentLayer?.sessionName)

            owner.close()
            assertTrue(fixture.engine.decorations.groups.isEmpty())
            val clears = fixture.engine.decorations.previousClears
            owner.close()
            fixture.reader.value = ReaderState.Resolving
            owner.acceptMarginalia(ReaderMarginaliaIntent.ShowAllPreviousLayers)
            runCurrent()
            assertEquals(clears, fixture.engine.decorations.previousClears)
            assertEquals(2, fixture.engine.decorations.previousReplacements)
        }

    @Test
    fun `highlight activation routes current edits and historical read only detail`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.open()
        runCurrent()
        val current = marginaliaTestHighlight("current")
        fixture.engine.decorations.activations.emit(
            ReaderAnnotationDecorationActivation(
                "current",
                ReaderAnnotationDecorationGroupId.Current,
                current.id
            )
        )
        runCurrent()
        assertEquals(current, fixture.owner.state.value.annotationMutations.editing?.annotation)
        fixture.engine.decorations.activations.emit(
            ReaderAnnotationDecorationActivation(
                "previous",
                ReaderAnnotationDecorationGroupId.Previous("previous"),
                marginaliaTestHighlight("previous").id
            )
        )
        runCurrent()
        assertTrue(fixture.owner.state.value.highlightDetail != null)
        fixture.owner.dismissHighlightDetail()
        runCurrent()
        assertEquals(null, fixture.owner.state.value.highlightDetail)
        fixture.owner.close()
    }

    @Test
    fun `annotation history and metadata authentication share one upstream flow`() = runTest {
        val fixture = Fixture(backgroundScope, rejectAuthentication = true)
        val events = mutableListOf<Unit>()
        val subscription = backgroundScope.launch {
            fixture.owner.authenticationRequiredEvents.collect { events += it }
        }
        fixture.open()
        runCurrent()
        assertEquals(2, events.size)
        fixture.owner.acceptMarginalia(ReaderMarginaliaIntent.EditCurrentSessionMetadata)
        fixture.owner.acceptMarginalia(ReaderMarginaliaIntent.ChangeCurrentSessionName("Changed"))
        fixture.owner.acceptMarginalia(ReaderMarginaliaIntent.SaveCurrentSessionMetadata)
        runCurrent()
        assertEquals(3, events.size)
        fixture.owner.close()
        runCurrent()
        assertTrue(subscription.isCompleted)
        fixture.owner.acceptMarginalia(ReaderMarginaliaIntent.RetryCurrentAnnotations)
        runCurrent()
        assertEquals(3, events.size)
    }

    private class Fixture(scope: CoroutineScope, rejectAuthentication: Boolean = false) {
        val reader = MutableStateFlow<ReaderState>(ReaderState.Resolving)
        val engine = MarginaliaTestRecordingEngine()
        val bookmark = ReaderAnnotation.Bookmark(
            "bookmark",
            "client",
            "epubcfi(/6/2!/4/2:3)",
            null,
            "now"
        )
        val store = ProjectionStore()
        var syncRequests = 0
        val selectionChanges = MutableSharedFlow<Unit>()
        var selectionClears = 0
        private val selections = object : ReaderSelectionEvents {
            override fun changes() = selectionChanges
            override suspend fun clear() {
                selectionClears++
            }
        }
        private val navigator = object : EpubCfiNavigator by MarginaliaTestFakeCfiNavigator {
            override suspend fun currentSelection() = EpubCfiOutcome.Success(
                EpubCfiSelection(EpubCfi("epubcfi(/6/2!/4/2:3)"), "selected", null, null, 1, 0.1)
            )
        }
        private val resolver = object : ReaderVisiblePageBookmarksResolver {
            override fun invalidations() = emptyFlow<Unit>()
            override suspend fun resolve(bookmarks: List<ReaderAnnotation.Bookmark>) =
                ReaderVisiblePageBookmarks(bookmarks)
        }
        val owner = ReaderMarginaliaPresentationController(
            reader, scope,
            ReaderAnnotationsLoader { _, id ->
                if (rejectAuthentication) throw SplClientException.AuthenticationRejected()
                listOf(marginaliaTestHighlight(id)) +
                    if (id == "current") listOf(bookmark) else emptyList()
            },
            ReaderMarginaliaLayerHistoryLoader { _, _, page ->
                if (rejectAuthentication) throw SplClientException.AuthenticationRejected()
                ReaderMarginaliaLayerHistoryPage(
                    listOf(marginaliaTestPreviousLayer("previous")),
                    page,
                    false
                )
            },
            MarginaliaTestFakeLayerPreferenceStore,
            MarginaliaTestFakeLayerVisibilityStore,
            ReaderSessionMetadataWriter { _, id, name, notes ->
                if (rejectAuthentication) throw SplClientException.AuthenticationRejected()
                ReaderSessionMetadata(id, name, notes)
            },
            store,
            { syncRequests++ }
        )

        fun open() {
            owner.selectEntry(marginaliaTestProfile(), "profile", "book", null)
            reader.value = ReaderState.Ready(
                "Book",
                object : ReaderEngine by engine {
                    override val visiblePageBookmarks = resolver
                    override val selectionEvents = selections
                    override val cfiNavigator = navigator
                },
                ReaderSessionContext("current", ReaderSessionStatus.ACTIVE, null),
                ReaderProgressRestore.NOT_NEEDED,
                ReaderSessionAuthority.SERVER
            )
        }
    }

    private class ProjectionStore :
        LocalReaderStateStore by MarginaliaTestFakeLocalReaderStateStore {
        private var annotations = emptyList<ReaderAnnotation>()
        val mutations = mutableListOf<ReaderAnnotationMutationRequest>()
        override suspend fun readAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = annotations

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            acknowledgedMutation: ReaderAnnotationMutationRequest?
        ) {
            this.annotations = annotations
        }

        override suspend fun applyAnnotationMutation(
            account: LocalReaderAccountKey,
            localSessionId: String,
            request: ReaderAnnotationMutationRequest
        ): List<ReaderAnnotation> {
            mutations += request
            annotations = annotations.filterNot { it is ReaderAnnotation.Bookmark }
            return annotations
        }
    }
}
