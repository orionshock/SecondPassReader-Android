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
class ReaderControllerTest {
    @Test
    fun `offline admitted asset opens with provisional local Session without server bootstrap`() =
        runTest {
            val file = Files.createTempFile("reader-offline", ".epub").toFile()
            var sessionCalls = 0
            var localOnly = false
            val controller = ReaderController(
                assetResolver = ReaderBookAssetResolver { request, _ ->
                    localOnly = request.localOnly
                    ResolvedReaderBook(request.titleHint.orEmpty(), file, reused = true)
                },
                engineOpener = ReaderEngineOpener { FakeEngine() },
                sessionCoordinator = ReaderSessionCoordinator { _, _ ->
                    sessionCalls += 1
                    error("Offline Reader must not bootstrap a Session.")
                },
                scope = this,
                launchPolicy = ReaderLaunchAdmission { _, _, _, _ ->
                    ReaderLaunchDecision.LOCAL_AVAILABLE
                },
                localStateStore = fakeLocalStore()
            )

            controller.initialize(
                profile(),
                "profile-1",
                "book-1",
                null,
                "Cached title",
                AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
            )
            advanceUntilIdle()

            val ready = controller.state.value as ReaderState.Ready
            assertTrue(localOnly)
            assertEquals("Cached title", ready.title)
            assertEquals("local-session", ready.session?.sessionId)
            assertEquals(ReaderSessionIdentityKind.PROVISIONAL, ready.session?.identityKind)
            assertTrue(ready.localOnly)
            assertEquals(0, sessionCalls)
            controller.close()
            advanceUntilIdle()
        }

    @Test
    fun `settled offline movement persists exact progress independently of server sync`() =
        runTest {
            val file = Files.createTempFile("reader-offline-progress", ".epub").toFile()
            val engine = FakeEngine()
            val persisted = mutableListOf<String>()
            val controller = ReaderController(
                assetResolver = ReaderBookAssetResolver { _, _ ->
                    ResolvedReaderBook("Cached title", file, reused = true)
                },
                engineOpener = ReaderEngineOpener { engine },
                sessionCoordinator = ReaderSessionCoordinator { _, _ -> error("server call") },
                scope = this,
                launchPolicy = ReaderLaunchAdmission { _, _, _, _ ->
                    ReaderLaunchDecision.LOCAL_AVAILABLE
                },
                localStateStore = fakeLocalStore { persisted += it }
            )
            controller.initialize(
                profile(),
                "profile-1",
                "book-1",
                null,
                "Cached title",
                AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
            )
            advanceUntilIdle()
            engine.navigator.currentPositionOutcome = EpubCfiOutcome.Success(EpubCfi(NEXT_CFI))

            engine.move(1)
            advanceUntilIdle()

            assertEquals(listOf(NEXT_CFI), persisted)
            controller.close()
            advanceUntilIdle()
        }

    @Test
    fun `close waits for latest local progress commit before releasing engine`() = runTest {
        val file = Files.createTempFile("reader-close-progress", ".epub").toFile()
        val engine = FakeEngine()
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val closeCompleted = CompletableDeferred<Unit>()
        val persisted = mutableListOf<String>()
        val controller = ReaderController(
            assetResolver = ReaderBookAssetResolver { _, _ ->
                ResolvedReaderBook("Cached title", file, reused = true)
            },
            engineOpener = ReaderEngineOpener { engine },
            sessionCoordinator = ReaderSessionCoordinator { _, _ -> error("server call") },
            scope = this,
            progressPersistenceScope = backgroundScope,
            launchPolicy = ReaderLaunchAdmission { _, _, _, _ ->
                ReaderLaunchDecision.LOCAL_AVAILABLE
            },
            localStateStore = fakeLocalStore {
                writeStarted.complete(Unit)
                releaseWrite.await()
                persisted += it
            }
        )
        controller.initialize(
            profile(),
            "profile-1",
            "book-1",
            null,
            "Cached title",
            AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
        )
        advanceUntilIdle()
        engine.navigator.currentPositionOutcome = EpubCfiOutcome.Success(EpubCfi(NEXT_CFI))
        engine.move(1)
        writeStarted.await()

        controller.close { closeCompleted.complete(Unit) }
        runCurrent()

        assertTrue(!engine.closed)
        releaseWrite.complete(Unit)
        closeCompleted.await()
        assertTrue(engine.closed)
        assertEquals(listOf(NEXT_CFI), persisted)
    }

    @Test
    fun `reconciliation binding does not deliver pending progress`() = runTest {
        val file = Files.createTempFile("reader-bind-only", ".epub").toFile()
        val engine = FakeEngine()
        val controller = ReaderController(
            assetResolver = ReaderBookAssetResolver { _, _ ->
                ResolvedReaderBook("Cached title", file, reused = true)
            },
            engineOpener = ReaderEngineOpener { engine },
            sessionCoordinator = ReaderSessionCoordinator { _, _ -> error("server call") },
            scope = this,
            launchPolicy = ReaderLaunchAdmission { _, _, _, _ ->
                ReaderLaunchDecision.LOCAL_AVAILABLE
            },
            localStateStore = fakeLocalStore()
        )
        controller.initialize(
            profile(),
            "profile-1",
            "book-1",
            null,
            "Cached title",
            AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
        )
        advanceUntilIdle()
        engine.navigator.currentPositionOutcome = EpubCfiOutcome.Success(EpubCfi(NEXT_CFI))
        engine.move(1)
        advanceUntilIdle()

        controller.acceptReconciledSession(
            "local-session",
            ReaderSessionContext(
                sessionId = "local-session",
                status = ReaderSessionStatus.ACTIVE,
                savedProgressCfi = NEXT_CFI,
                serverSessionId = "server-session",
                identityKind = ReaderSessionIdentityKind.SERVER_CONFIRMED
            )
        )
        advanceUntilIdle()

        assertEquals(
            "server-session",
            (controller.state.value as ReaderState.Ready).session?.serverSessionId
        )
        controller.close()
        advanceUntilIdle()
    }

    @Test
    fun `download then publication open reaches ready in order`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val engine = FakeEngine()
        val resolver = ReaderBookAssetResolver { _, onDownloadStarted ->
            onDownloadStarted()
            ResolvedReaderBook("Academ's Fury", file, reused = false)
        }
        val controller = ReaderController(
            resolver,
            ReaderEngineOpener { engine },
            coordinator(),
            this
        )
        val states = mutableListOf<ReaderState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.state.collect(states::add)
        }

        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()

        assertEquals(
            listOf(
                ReaderState.Resolving::class,
                ReaderState.Downloading::class,
                ReaderState.Opening::class,
                ReaderState.Ready::class
            ),
            states.map { it::class }
        )
        assertEquals("Academ's Fury", (controller.state.value as ReaderState.Ready).title)
        assertEquals(
            ReaderProgressRestore.NOT_NEEDED,
            (controller.state.value as ReaderState.Ready).restore
        )
        assertTrue(engine.navigator.destinations.isEmpty())
        controller.close()
        assertTrue(engine.closed)
    }

    @Test
    fun `saved progress waits for navigator readiness and restores exact CFI`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val engine = FakeEngine(EpubCfiReadiness.AwaitingViewport)
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { engine },
            coordinator(PROGRESS_CFI),
            this
        )

        controller.initialize(profile(), "profile-1", "book-1", "session-existing")
        runCurrent()

        val waiting = controller.state.value as ReaderState.Ready
        assertEquals("session-existing", requireNotNull(waiting.session).sessionId)
        assertEquals(ReaderProgressRestore.WAITING, waiting.restore)
        assertTrue(engine.navigator.destinations.isEmpty())

        engine.navigator.readiness.value = EpubCfiReadiness.Available
        advanceUntilIdle()

        val restored = controller.state.value as ReaderState.Ready
        assertEquals(ReaderProgressRestore.RESTORED, restored.restore)
        assertEquals(listOf(EpubCfi(PROGRESS_CFI)), engine.navigator.destinations)
        assertEquals(listOf(EpubCfi(PROGRESS_CFI)), engine.startupRetentionPositions)
        controller.close()
    }

    @Test
    fun `saved-location movement is excluded before progress capture starts`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val engine = FakeEngine(EpubCfiReadiness.AwaitingViewport)
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { engine },
            coordinator(PROGRESS_CFI),
            this
        )

        controller.initialize(profile(), "profile-1", "book-1", "session-existing")
        runCurrent()
        engine.move(1)
        engine.navigator.readiness.value = EpubCfiReadiness.Available
        advanceUntilIdle()

        assertEquals(
            ReaderProgressRestore.RESTORED,
            (controller.state.value as ReaderState.Ready).restore
        )
        assertTrue(requireNotNull(controller.progress.value).captureEnabled)
        assertEquals(0, engine.navigator.positionRequests)
        assertEquals(null, controller.progress.value?.latestCandidate)

        engine.navigator.currentPositionOutcome = EpubCfiOutcome.Success(EpubCfi(NEXT_CFI))
        engine.move(2)
        advanceUntilIdle()

        assertEquals(EpubCfi(NEXT_CFI), controller.progress.value?.latestCandidate)
        controller.close()
    }

    @Test
    fun `settled movement is persisted locally before network acknowledgement`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val engine = FakeEngine()
        val persisted = mutableListOf<String>()
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { engine },
            coordinator(),
            this,
            localStateStore = fakeLocalStore(persisted::add)
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()
        engine.navigator.currentPositionOutcome = EpubCfiOutcome.Success(EpubCfi(NEXT_CFI))
        engine.move(1)
        advanceUntilIdle()

        assertEquals(listOf(NEXT_CFI), persisted)
        controller.close()
    }

    @Test
    fun `stalled startup restore cannot permanently disable progress capture`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val engine = FakeEngine().apply {
            navigator.goToGate = CompletableDeferred()
        }
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { engine },
            coordinator(PROGRESS_CFI),
            this
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        runCurrent()
        assertTrue(controller.progress.value?.captureEnabled == false)

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(
            ReaderProgressRestore.SKIPPED,
            (controller.state.value as ReaderState.Ready).restore
        )
        assertTrue(requireNotNull(controller.progress.value).captureEnabled)
        controller.close()
    }

    @Test
    fun `malformed or rejected saved progress leaves Reader usable`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val malformedEngine = FakeEngine()
        val rejectedEngine = FakeEngine().apply {
            navigator.goToOutcome = EpubCfiOutcome.Failure(EpubCfiFailure.DOM_TARGET_NOT_FOUND)
        }
        val malformed = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { malformedEngine },
            coordinator(" "),
            this
        )
        val rejected = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { rejectedEngine },
            coordinator(PROGRESS_CFI),
            this
        )

        malformed.initialize(profile(), "profile-1", "book-1", null)
        rejected.initialize(profile(), "profile-1", "book-2", null)
        advanceUntilIdle()

        assertEquals(
            ReaderProgressRestore.SKIPPED,
            (malformed.state.value as ReaderState.Ready).restore
        )
        assertEquals(
            ReaderProgressRestore.SKIPPED,
            (rejected.state.value as ReaderState.Ready).restore
        )
        malformed.close()
        rejected.close()
    }

    @Test
    fun `download failure is classified without opening a publication`() = runTest {
        var opened = false
        val controller = ReaderController(
            ReaderBookAssetResolver { _, onDownloadStarted ->
                onDownloadStarted()
                throw SplClientException.ServerUnreachable()
            },
            ReaderEngineOpener {
                opened = true
                FakeEngine()
            },
            coordinator(),
            this
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()

        assertEquals(ReaderState.Failure(ReaderFailure.DOWNLOAD), controller.state.value)
        assertTrue(!opened)
        controller.close()
    }

    @Test
    fun `integrity failure is classified without opening a publication`() = runTest {
        var opened = false
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> throw ReaderEpubIntegrityException() },
            ReaderEngineOpener {
                opened = true
                FakeEngine()
            },
            coordinator(),
            this
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()

        assertEquals(ReaderState.Failure(ReaderFailure.INTEGRITY), controller.state.value)
        assertTrue(!opened)
        controller.close()
    }

    @Test
    fun `publication and format failures remain distinct`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val resolved = ReaderBookAssetResolver { _, _ ->
            ResolvedReaderBook("Book", file, reused = true)
        }
        val openFailure = ReaderController(
            resolved,
            ReaderEngineOpener { throw ReaderEngineOpenException("broken") },
            coordinator(),
            this
        )
        val noEpub = ReaderController(
            ReaderBookAssetResolver { _, _ -> throw ReaderEpubUnavailableException() },
            ReaderEngineOpener { FakeEngine() },
            coordinator(),
            this
        )

        openFailure.initialize(profile(), "profile-1", "book-1", null)
        noEpub.initialize(profile(), "profile-1", "book-2", null)
        advanceUntilIdle()

        assertEquals(ReaderState.Failure(ReaderFailure.OPEN), openFailure.state.value)
        assertEquals(ReaderState.Failure(ReaderFailure.NO_EPUB), noEpub.state.value)
        openFailure.close()
        noEpub.close()
    }

    @Test
    fun `reinitialization closes a late engine without replacing the current request`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val firstEngine = FakeEngine()
        val secondEngine = FakeEngine()
        val resolver = ReaderBookAssetResolver { request, _ ->
            ResolvedReaderBook(request.bookId, file, reused = true)
        }
        lateinit var controller: ReaderController
        var openCount = 0
        controller = ReaderController(
            resolver,
            ReaderEngineOpener {
                if (openCount++ == 0) {
                    controller.initialize(profile(), "profile-1", "book-2", null)
                    firstEngine
                } else {
                    secondEngine
                }
            },
            coordinator(),
            this
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()

        assertTrue(firstEngine.closed)
        assertEquals("book-2", (controller.state.value as ReaderState.Ready).title)
        assertTrue(!secondEngine.closed)
        controller.close()
        assertTrue(secondEngine.closed)
    }

    @Test
    fun `authentication rejection is surfaced to connection ownership`() = runTest {
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> throw SplClientException.AuthenticationRejected() },
            ReaderEngineOpener { FakeEngine() },
            coordinator(),
            this
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        assertEquals(
            ReaderConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
        controller.close()
    }

    @Test
    fun `persisted appearance initializes a newly opened engine`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val saved = ReaderAppearance(
            theme = ReaderTheme.LIGHT,
            fontScale = 1.2,
            lineHeight = 1.6,
            publisherStylesEnabled = true
        )
        var openedWith: ReaderAppearance? = null
        val engine = FakeEngine()
        val opener = object : ReaderEngineOpener {
            override suspend fun open(file: java.io.File) = error("Initial appearance is required")

            override suspend fun open(
                file: java.io.File,
                initialAppearance: ReaderAppearance
            ): ReaderEngine {
                openedWith = initialAppearance
                engine.appearance.update(initialAppearance)
                return engine
            }
        }
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            opener,
            coordinator(),
            this,
            FakeAppearanceStore(saved)
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()

        assertEquals(saved, openedWith)
        assertEquals(
            saved,
            (controller.state.value as ReaderState.Ready).engine.appearance.appearance.value
        )
        controller.close()
    }

    @Test
    fun `live appearance applies before asynchronous persistence completes`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val writeGate = CompletableDeferred<Unit>()
        val store = FakeAppearanceStore(ReaderAppearance(), writeGate)
        val engine = FakeEngine()
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { engine },
            coordinator(),
            this,
            store
        )
        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()
        val updated = ReaderAppearance(theme = ReaderTheme.DARK, fontScale = 1.1)

        controller.updateAppearance(updated)
        runCurrent()

        assertEquals(updated, engine.appearance.appearance.value)
        assertEquals(null, store.written)
        writeGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(updated, store.written)
        controller.close()
    }

    private class FakeEngine(initialReadiness: EpubCfiReadiness = EpubCfiReadiness.Available) :
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

    private class TestAppearanceController : ReaderAppearanceController {
        private val state = MutableStateFlow(ReaderAppearance())
        override val appearance = state

        override suspend fun update(appearance: ReaderAppearance) {
            state.value = appearance
        }
    }

    private class FakeAppearanceStore(
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

    private class FakeCfiNavigator(initialReadiness: EpubCfiReadiness) : EpubCfiNavigator {
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

    private fun coordinator(progressCfi: String? = null) = ReaderSessionCoordinator { _, request ->
        ReaderSessionContext(
            sessionId = request.existingSessionId ?: "session-1",
            status = ReaderSessionStatus.ACTIVE,
            savedProgressCfi = progressCfi
        )
    }

    private fun fakeLocalStore(onProgress: suspend (String) -> Unit = {}) =
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

    private companion object {
        const val PROGRESS_CFI = "epubcfi(/6/2!/4/2:3)"
        const val NEXT_CFI = "epubcfi(/6/4!/4/2:7)"
    }

    private fun profile() = ConnectionProfile(
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
