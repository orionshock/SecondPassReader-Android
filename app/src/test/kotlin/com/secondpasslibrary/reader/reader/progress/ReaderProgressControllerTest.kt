package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovement
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressControllerTest {
    @Test
    fun `capture starts only after startup restore terminates`() = runTest {
        val engine = FakeEngine { EpubCfiOutcome.Success(CFI_A) }
        val controller = ReaderProgressController(this)
        controller.prepare(activeSession(), engine)

        engine.move(1)
        advanceUntilIdle()

        assertFalse(requireNotNull(controller.state.value).captureEnabled)
        assertEquals(0, engine.positionRequests)
        assertNull(controller.state.value?.latestCandidate)

        controller.enableAfterStartupRestore()
        runCurrent()
        engine.move(2)
        advanceUntilIdle()

        assertEquals(CFI_A, controller.state.value?.latestCandidate)
        controller.reset()
    }

    @Test
    fun `closed historical Session never captures progress`() = runTest {
        val engine = FakeEngine { EpubCfiOutcome.Success(CFI_A) }
        val controller = ReaderProgressController(this)
        controller.prepare(activeSession(status = ReaderSessionStatus.CLOSED), engine)

        controller.enableAfterStartupRestore()
        engine.move(1)
        advanceUntilIdle()

        assertFalse(requireNotNull(controller.state.value).captureEnabled)
        assertEquals(0, engine.positionRequests)
        assertNull(controller.state.value?.latestCandidate)
        controller.reset()
    }

    @Test
    fun `rapid movement cannot publish a stale earlier capture`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val engine = FakeEngine {
            if (positionRequests == 1) {
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    EpubCfiOutcome.Success(CFI_A)
                }
            } else {
                EpubCfiOutcome.Success(CFI_B)
            }
        }
        val controller = ReaderProgressController(this)
        controller.prepare(activeSession(), engine)
        controller.enableAfterStartupRestore()
        runCurrent()

        engine.move(1)
        runCurrent()
        assertTrue(firstStarted.isCompleted)
        engine.move(2)
        advanceUntilIdle()

        assertEquals(2, engine.positionRequests)
        assertEquals(CFI_B, controller.state.value?.latestCandidate)
        assertEquals(1L, controller.state.value?.candidateVersion)
        controller.reset()
    }

    @Test
    fun `transient capture failure leaves the previous candidate unchanged`() = runTest {
        val outcomes = ArrayDeque<EpubCfiOutcome<EpubCfi>>().apply {
            add(EpubCfiOutcome.Success(CFI_A))
            add(EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_CHANGED_DURING_OPERATION))
        }
        val engine = FakeEngine { outcomes.removeFirst() }
        val controller = ReaderProgressController(this)
        controller.prepare(activeSession(), engine)
        controller.enableAfterStartupRestore()
        runCurrent()

        engine.move(1)
        advanceUntilIdle()
        engine.move(2)
        advanceUntilIdle()

        assertEquals(CFI_A, controller.state.value?.latestCandidate)
        assertEquals(1L, controller.state.value?.candidateVersion)
        controller.reset()
    }

    @Test
    fun `duplicate CFI does not churn candidate state`() = runTest {
        val engine = FakeEngine { EpubCfiOutcome.Success(CFI_A) }
        val controller = ReaderProgressController(this)
        controller.prepare(activeSession(), engine)
        controller.enableAfterStartupRestore()
        runCurrent()

        engine.move(1)
        advanceUntilIdle()
        engine.move(2)
        advanceUntilIdle()

        assertEquals(CFI_A, controller.state.value?.latestCandidate)
        assertEquals(1L, controller.state.value?.candidateVersion)
        controller.reset()
    }

    private class FakeEngine(
        private val position: suspend FakeEngine.() -> EpubCfiOutcome<EpubCfi>
    ) : ReaderEngine {
        private val movementEvents = MutableSharedFlow<ReaderViewportMovement>(
            extraBufferCapacity = 8
        )
        override val viewport = ReaderViewport { }
        override val viewportMovements = ReaderViewportMovements { movementEvents }
        override val cfiNavigator = object : EpubCfiNavigator {
            override val readiness = MutableStateFlow<EpubCfiReadiness>(
                EpubCfiReadiness.Available
            )

            override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> =
                error("Navigation is not used by progress capture tests.")

            override suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> {
                positionRequests += 1
                return position()
            }

            override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> =
                error("Selection is not used by progress capture tests.")

            override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
                error("Resolution is not used by progress capture tests.")
        }
        var positionRequests = 0
            private set

        fun move(sequence: Long) {
            check(movementEvents.tryEmit(ReaderViewportMovement(sequence)))
        }

        override fun close() = Unit
    }

    private fun activeSession(status: ReaderSessionStatus = ReaderSessionStatus.ACTIVE) =
        ReaderSessionContext(
            sessionId = "session-1",
            status = status,
            savedProgressCfi = null
        )

    private companion object {
        val CFI_A = EpubCfi("epubcfi(/6/2!/4/2:3)")
        val CFI_B = EpubCfi("epubcfi(/6/4!/4/2:7)")
    }
}
