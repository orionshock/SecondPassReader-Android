package com.secondpasslibrary.reader.reader

import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationActivation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationFailure
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryPage
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerRole
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerSummary
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPreferenceStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaVisibilityScope
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
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
import com.secondpasslibrary.reader.reader.toc.EmptyReaderTableOfContents
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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
        val engines = mutableListOf<RecordingEngine>()
        val viewModel = readerViewModel(engines)
        try {
            viewModel.initialize(
                profile(),
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
                profile(),
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
                profile(),
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
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun readerViewModel(engines: MutableList<RecordingEngine>): ReaderViewModel {
        val file = Files.createTempFile("reader-view-model-lifecycle", ".epub").toFile()
        val annotationsLoader = ReaderAnnotationsLoader { _, sessionId ->
            if (sessionId.startsWith("previous-")) listOf(highlight(sessionId)) else emptyList()
        }
        return ReaderViewModel(
            assetResolver = ReaderBookAssetResolver { request, _ ->
                ResolvedReaderBook(request.bookId, file, reused = true)
            },
            launchPolicy = ReaderLaunchAdmission { _, _, _, _ -> ReaderLaunchDecision.ONLINE },
            engineOpener = ReaderEngineOpener {
                RecordingEngine().also(engines::add)
            },
            sessionCoordinator = ReaderSessionCoordinator { _, request ->
                ReaderSessionContext(
                    sessionId = "current-${request.bookId}",
                    status = ReaderSessionStatus.ACTIVE,
                    savedProgressCfi = null
                )
            },
            appearanceStore = FakeAppearanceStore,
            annotationsLoader = annotationsLoader,
            marginaliaLayerHistoryLoader = ReaderMarginaliaLayerHistoryLoader { _, bookId, page ->
                ReaderMarginaliaLayerHistoryPage(
                    layers = listOf(previousLayer("previous-$bookId")),
                    page = page,
                    hasMore = false
                )
            },
            marginaliaLayerPreferenceStore = FakeLayerPreferenceStore,
            marginaliaLayerVisibilityStore = FakeLayerVisibilityStore,
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
            localReaderStateStore = FakeLocalReaderStateStore
        )
    }

    private fun ReaderViewModel.activeScopeChildren(): Int =
        checkNotNull(viewModelScope.coroutineContext[Job]).children.count()

    private class RecordingEngine : ReaderEngine {
        override val viewport = ReaderViewport { _: Modifier -> }
        override val cfiNavigator = FakeCfiNavigator
        override val viewportMovements = ReaderViewportMovements { emptyFlow() }
        override val tableOfContents = EmptyReaderTableOfContents
        override val appearance = FakeAppearanceController()
        override val annotationDecorations = RecordingDecorations()
        val decorations: RecordingDecorations
            get() = annotationDecorations

        override fun close() = Unit
    }

    private class RecordingDecorations : ReaderAnnotationDecorations {
        override val failures = MutableStateFlow(
            emptyMap<String, ReaderAnnotationDecorationFailure>()
        )
        override val activations: Flow<ReaderAnnotationDecorationActivation> = emptyFlow()
        var previousReplacements = 0
        var previousClears = 0

        override suspend fun replace(
            groupId: ReaderAnnotationDecorationGroupId,
            decorations: List<ReaderAnnotationDecoration>
        ) {
            if (groupId is ReaderAnnotationDecorationGroupId.Previous) previousReplacements += 1
        }

        override suspend fun clear(groupId: ReaderAnnotationDecorationGroupId) {
            if (groupId is ReaderAnnotationDecorationGroupId.Previous) previousClears += 1
        }
    }

    private class FakeAppearanceController : ReaderAppearanceController {
        override val appearance: StateFlow<ReaderAppearance> = MutableStateFlow(ReaderAppearance())

        override suspend fun update(appearance: ReaderAppearance) = Unit
    }

    private data object FakeAppearanceStore : ReaderAppearanceStore {
        override suspend fun read() = ReaderAppearance()

        override suspend fun write(appearance: ReaderAppearance) = Unit
    }

    private data object FakeLayerPreferenceStore : ReaderMarginaliaLayerPreferenceStore {
        override suspend fun readAutoShowPrevious() = true

        override suspend fun writeAutoShowPrevious(enabled: Boolean) = Unit
    }

    private data object FakeLayerVisibilityStore : ReaderMarginaliaLayerVisibilityStore {
        override suspend fun read(
            scope: ReaderMarginaliaVisibilityScope,
            sessionId: String,
            now: Instant
        ): Boolean? = null

        override suspend fun write(
            scope: ReaderMarginaliaVisibilityScope,
            sessionId: String,
            visible: Boolean,
            touchedAt: Instant
        ) = Unit

        override suspend fun clearAccountState() = Unit
    }

    private data object FakeLocalReaderStateStore : LocalReaderStateStore {
        override suspend fun selectOfflineSession(account: LocalReaderAccountKey, bookId: String) =
            error("Offline selection is not expected.")

        override suspend fun retainServerSession(
            account: LocalReaderAccountKey,
            bookId: String,
            session: ReaderSessionContext
        ) = session

        override suspend fun writeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String,
            provenance: LocalReaderWriteProvenance,
            locationLabel: String?
        ) = Unit

        override suspend fun acknowledgeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String
        ) = Unit

        override suspend fun readAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = emptyList<ReaderAnnotation>()

        override suspend fun applyAnnotationMutation(
            account: LocalReaderAccountKey,
            localSessionId: String,
            request: ReaderAnnotationMutationRequest
        ) = emptyList<ReaderAnnotation>()

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            acknowledgedMutation: ReaderAnnotationMutationRequest?
        ) = Unit

        override suspend fun purgeAccount(account: LocalReaderAccountKey) = Unit
    }

    private data object FakeCfiNavigator : EpubCfiNavigator {
        override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> = EpubCfiOutcome.Success(Unit)

        override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> =
            EpubCfiOutcome.Success(null)

        override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
            EpubCfiOutcome.Failure(EpubCfiFailure.DOM_TARGET_NOT_FOUND)
    }

    private fun previousLayer(sessionId: String) = ReaderMarginaliaLayerSummary(
        sessionId = sessionId,
        role = ReaderMarginaliaLayerRole.PREVIOUS,
        sessionStatus = ReaderSessionStatus.CLOSED,
        sessionName = null,
        startedAt = null,
        closedAt = null,
        lastActivityAt = null,
        annotationCount = 1
    )

    private fun highlight(sessionId: String) = ReaderAnnotation.Highlight(
        id = "highlight-$sessionId",
        clientId = "client-$sessionId",
        cfi = "epubcfi(/6/2!/4/2:3)",
        locationLabel = null,
        updatedAt = "2026-09-16T00:00:00Z",
        quote = "quote",
        prefix = null,
        suffix = null,
        note = null,
        color = ReaderAnnotationColor.YELLOW
    )

    private fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-09-16",
        clientSessionId = "client-session",
        clientName = "Reader",
        clientType = "reader"
    )
}
