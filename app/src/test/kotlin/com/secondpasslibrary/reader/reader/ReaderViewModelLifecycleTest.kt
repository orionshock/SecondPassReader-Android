package com.secondpasslibrary.reader.reader

import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryPage
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionCoordinator
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadata
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataWriter
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliation
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationFailure
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationResult
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectOperation
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectReport
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelLifecycleTest {
    @Test
    fun `repeated initialization retains one marginalia decoration coordinator`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engines = mutableListOf<MarginaliaTestRecordingEngine>()
        val viewModel = readerViewModel(engines)
        try {
            viewModel.initialize(
                marginaliaTestProfile(),
                "profile-1",
                "book-1",
                null,
                null,
                AppAvailability.Online
            )
            advanceUntilIdle()
            val lifetimeJobCount = viewModel.activeScopeChildren()

            assertEquals(1, engines.single().decorations.previousReplacements)

            viewModel.initialize(
                marginaliaTestProfile(),
                "profile-1",
                "book-1",
                null,
                null,
                AppAvailability.Online
            )
            advanceUntilIdle()

            assertEquals(lifetimeJobCount, viewModel.activeScopeChildren())
            assertEquals(1, engines.single().decorations.previousReplacements)

            viewModel.initialize(
                marginaliaTestProfile(),
                "profile-1",
                "book-2",
                null,
                null,
                AppAvailability.Online
            )
            advanceUntilIdle()

            assertEquals(lifetimeJobCount, viewModel.activeScopeChildren())
            assertEquals(1, engines[0].decorations.previousClears)
            assertEquals(1, engines[1].decorations.previousReplacements)
            assertEquals("current-book-2", viewModel.marginaliaState.value.annotations.sessionId)
            viewModel.acceptMarginalia(ReaderMarginaliaIntent.HideAllPreviousLayers)
            advanceUntilIdle()
            assertEquals(1, engines[1].decorations.previousClears)
            viewModel.acceptMarginalia(ReaderMarginaliaIntent.ShowAllPreviousLayers)
            advanceUntilIdle()
            assertEquals(2, engines[1].decorations.previousReplacements)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun readerViewModel(
        engines: MutableList<MarginaliaTestRecordingEngine>
    ): ReaderViewModel {
        val file = Files.createTempFile("reader-view-model-lifecycle", ".epub").toFile()
        val annotationsLoader = ReaderAnnotationsLoader { _, sessionId ->
            if (sessionId.startsWith("previous-")) {
                listOf(marginaliaTestHighlight(sessionId))
            } else {
                emptyList()
            }
        }
        return ReaderViewModel(
            assetResolver = ReaderBookAssetResolver { request, _ ->
                ResolvedReaderBook(request.bookId, file, reused = true)
            },
            launchPolicy = ReaderLaunchAdmission { _, _, _, _ -> ReaderLaunchDecision.ONLINE },
            engineOpener = ReaderEngineOpener {
                MarginaliaTestRecordingEngine().also(engines::add)
            },
            sessionCoordinator = ReaderSessionCoordinator { _, request ->
                ReaderSessionContext(
                    sessionId = "current-${request.bookId}",
                    status = ReaderSessionStatus.ACTIVE,
                    savedProgressCfi = null
                )
            },
            appearanceStore = MarginaliaTestFakeAppearanceStore,
            annotationsLoader = annotationsLoader,
            marginaliaLayerHistoryLoader = ReaderMarginaliaLayerHistoryLoader { _, bookId, page ->
                ReaderMarginaliaLayerHistoryPage(
                    layers = listOf(marginaliaTestPreviousLayer("previous-$bookId")),
                    page = page,
                    hasMore = false
                )
            },
            marginaliaLayerPreferenceStore = MarginaliaTestFakeLayerPreferenceStore,
            marginaliaLayerVisibilityStore = MarginaliaTestFakeLayerVisibilityStore,
            sessionMetadataWriter = ReaderSessionMetadataWriter { _, sessionId, name, notes ->
                ReaderSessionMetadata(sessionId, name, notes)
            },
            sessionReconciler = ReaderSessionReconciliation { _, _, _, _ ->
                ReaderSessionReconciliationResult.Failed(
                    ReaderSessionReconciliationFailure.UNAVAILABLE
                )
            },
            reconnectOrchestrator = ReaderReconnectOperation { _, _ ->
                ReaderReconnectReport(false, false)
            },
            localReaderStateStore = MarginaliaTestFakeLocalReaderStateStore
        )
    }

    private fun ReaderViewModel.activeScopeChildren(): Int =
        checkNotNull(viewModelScope.coroutineContext[Job]).children.count()
}
