package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderEpubIntegrityException
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpenException
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
internal class ReaderLaunchRestorationControllerTest : ReaderControllerTestSupport() {
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
            this,
            fakeLocalStore()
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
        val ready = controller.state.value as ReaderState.Ready
        assertEquals("Academ's Fury", ready.title)
        assertEquals("session-1", ready.session.sessionId)
        assertEquals(ReaderSessionAuthority.SERVER, ready.authority)
        assertEquals(
            ReaderProgressRestore.NOT_NEEDED,
            ready.restore
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
            this,
            fakeLocalStore()
        )

        controller.initialize(profile(), "profile-1", "book-1", "session-existing")
        runCurrent()

        val waiting = controller.state.value as ReaderState.Ready
        assertEquals("session-existing", waiting.session.sessionId)
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
            this,
            fakeLocalStore()
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
    fun `stalled startup restore cannot permanently disable progress capture`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val engine = FakeEngine().apply {
            navigator.goToGate = CompletableDeferred()
        }
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { engine },
            coordinator(PROGRESS_CFI),
            this,
            fakeLocalStore()
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
            this,
            fakeLocalStore()
        )
        val rejected = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { rejectedEngine },
            coordinator(PROGRESS_CFI),
            this,
            fakeLocalStore()
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
            this,
            fakeLocalStore()
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
            this,
            fakeLocalStore()
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
            this,
            fakeLocalStore()
        )
        val noEpub = ReaderController(
            ReaderBookAssetResolver { _, _ -> throw ReaderEpubUnavailableException() },
            ReaderEngineOpener { FakeEngine() },
            coordinator(),
            this,
            fakeLocalStore()
        )

        openFailure.initialize(profile(), "profile-1", "book-1", null)
        noEpub.initialize(profile(), "profile-1", "book-2", null)
        advanceUntilIdle()

        assertEquals(ReaderState.Failure(ReaderFailure.OPEN), openFailure.state.value)
        assertEquals(ReaderState.Failure(ReaderFailure.NO_EPUB), noEpub.state.value)
        openFailure.close()
        noEpub.close()
    }
}
