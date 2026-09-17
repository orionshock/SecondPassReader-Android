package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionCoordinator
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ReaderOfflineProgressControllerTest : ReaderControllerTestSupport() {
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
            assertEquals("local-session", ready.session.sessionId)
            assertEquals(ReaderSessionIdentityKind.PROVISIONAL, ready.session.identityKind)
            assertEquals(ReaderSessionAuthority.LOCAL, ready.authority)
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
            (controller.state.value as ReaderState.Ready).session.serverSessionId
        )
        assertEquals(
            ReaderSessionAuthority.SERVER,
            (controller.state.value as ReaderState.Ready).authority
        )
        controller.close()
        advanceUntilIdle()
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
}
