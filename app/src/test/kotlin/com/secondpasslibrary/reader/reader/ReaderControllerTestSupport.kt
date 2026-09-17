package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderEpubIntegrityException
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpenException
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovement
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import com.secondpasslibrary.reader.reader.lifecycle.ReaderPositionRetention
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionCoordinator
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.EmptyReaderTableOfContents
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class ReaderControllerTestSupport {
    protected class FakeEngine(initialReadiness: EpubCfiReadiness = EpubCfiReadiness.Available) :
        ReaderEngine {
        var closed = false
        override val viewport = ReaderViewport { }
        val movements = MutableSharedFlow<ReaderViewportMovement>(extraBufferCapacity = 8)
        override val viewportMovements = ReaderViewportMovements { movements }
        val navigator = FakeCfiNavigator(initialReadiness)
        override val cfiNavigator: EpubCfiNavigator = navigator
        override val tableOfContents = EmptyReaderTableOfContents
        override val appearance = TestAppearanceController()
        val startupRetentionPositions = mutableListOf<EpubCfi?>()
        override val positionRetention = object : ReaderPositionRetention {
            override fun completeStartupRestore(restoredPosition: EpubCfi?) {
                startupRetentionPositions += restoredPosition
            }

            override fun captureBeforeNavigatorLoss() = Unit

            override suspend fun awaitPendingCapture(): EpubCfi? = null

            override fun retainPosition(position: EpubCfi) = Unit
        }

        fun move(sequence: Long) {
            check(movements.tryEmit(ReaderViewportMovement(sequence)))
        }

        override fun close() {
            closed = true
        }
    }

    protected class TestAppearanceController : ReaderAppearanceController {
        private val state = MutableStateFlow(ReaderAppearance())
        override val appearance = state

        override suspend fun update(appearance: ReaderAppearance) {
            state.value = appearance
        }
    }

    protected class FakeAppearanceStore(
        private val stored: ReaderAppearance,
        private val writeGate: CompletableDeferred<Unit>? = null
    ) : ReaderAppearanceStore {
        var written: ReaderAppearance? = null

        override suspend fun read() = stored

        override suspend fun write(appearance: ReaderAppearance) {
            writeGate?.await()
            written = appearance
        }
    }

    protected class FakeCfiNavigator(initialReadiness: EpubCfiReadiness) : EpubCfiNavigator {
        override val readiness = MutableStateFlow(initialReadiness)
        val destinations = mutableListOf<EpubCfi>()
        var goToOutcome: EpubCfiOutcome<Unit> = EpubCfiOutcome.Success(Unit)
        var currentPositionOutcome: EpubCfiOutcome<EpubCfi> =
            EpubCfiOutcome.Failure(EpubCfiFailure.VISIBLE_POSITION_UNAVAILABLE)
        var goToGate: CompletableDeferred<Unit>? = null
        var positionRequests = 0

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
            destinations += cfi
            goToGate?.await()
            return goToOutcome
        }

        override suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> {
            positionRequests += 1
            return currentPositionOutcome
        }

        override suspend fun currentPositionWithContext(): EpubCfiOutcome<EpubCfiPosition> =
            when (val captured = currentPosition()) {
                is EpubCfiOutcome.Failure -> captured

                is EpubCfiOutcome.Success -> EpubCfiOutcome.Success(
                    EpubCfiPosition(captured.value, 3, 0.42, "Chapter Three")
                )
            }

        override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> =
            error("Selection capture is not used by ReaderController tests.")

        override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
            error("CFI resolution is not used by ReaderController tests.")
    }

    protected fun coordinator(progressCfi: String? = null) = ReaderSessionCoordinator {
            _,
            request
        ->
        ReaderSessionContext(
            sessionId = request.existingSessionId ?: "session-1",
            status = ReaderSessionStatus.ACTIVE,
            savedProgressCfi = progressCfi
        )
    }

    protected fun fakeLocalStore(onProgress: suspend (String) -> Unit = {}) =
        object : LocalReaderStateStore {
            override suspend fun selectOfflineSession(
                account: LocalReaderAccountKey,
                bookId: String
            ) = ReaderSessionContext(
                "local-session",
                ReaderSessionStatus.ACTIVE,
                null,
                serverSessionId = null,
                identityKind = ReaderSessionIdentityKind.PROVISIONAL
            )

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
            ) = onProgress(cfi)

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

    protected companion object {
        const val PROGRESS_CFI = "epubcfi(/6/2!/4/2:3)"
        const val NEXT_CFI = "epubcfi(/6/4!/4/2:7)"
    }

    protected fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-23",
        clientSessionId = "session-1",
        clientName = "Reader",
        clientType = "reader"
    )
}
