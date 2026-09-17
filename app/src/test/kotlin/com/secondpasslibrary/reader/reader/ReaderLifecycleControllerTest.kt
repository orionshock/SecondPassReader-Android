package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.session.ReaderSessionCoordinator
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ReaderLifecycleControllerTest : ReaderControllerTestSupport() {
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
}
