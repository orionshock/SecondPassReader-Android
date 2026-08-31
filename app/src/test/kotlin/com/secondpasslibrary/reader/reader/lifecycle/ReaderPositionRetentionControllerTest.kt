package com.secondpasslibrary.reader.reader.lifecycle

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPositionRetentionControllerTest {
    @Test
    fun `fresh pre-loss capture replaces existing retained fallback`() = runTest {
        val navigator = FakeNavigator()
        val retainedA = EpubCfi("epubcfi(/6/2!/4/2/1:2)")
        val capturedB = EpubCfi("epubcfi(/6/4!/4/2/1:8)")
        navigator.positions += completed(EpubCfiOutcome.Success(capturedB))
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(retainedA)

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        controller.awaitPendingCapture()
        controller.navigatorAttached()
        runCurrent()

        assertEquals(listOf(capturedB), navigator.destinations)
        controller.close()
    }

    @Test
    fun `successful pre-detach capture restores exact CFI after readiness`() = runTest {
        val navigator = FakeNavigator(EpubCfiReadiness.AwaitingViewport)
        val retained = EpubCfi("epubcfi(/6/4!/4/2/1:8)")
        navigator.positions += completed(EpubCfiOutcome.Success(retained))
        var suppressions = 0
        val controller = controller(navigator) { operation ->
            suppressions += 1
            operation()
        }
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(null)

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        controller.navigatorAttached()
        runCurrent()

        assertTrue(navigator.destinations.isEmpty())
        navigator.readiness.value = EpubCfiReadiness.Available
        runCurrent()

        assertEquals(listOf(retained), navigator.destinations)
        assertEquals(1, suppressions)
        controller.close()
    }

    @Test
    fun `failed capture retains last valid startup position`() = runTest {
        val navigator = FakeNavigator()
        val startup = EpubCfi("epubcfi(/6/2!/4/2/1:2)")
        navigator.positions += completed(
            EpubCfiOutcome.Failure(EpubCfiFailure.VISIBLE_POSITION_UNAVAILABLE)
        )
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(startup)

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        controller.navigatorAttached()
        runCurrent()

        assertEquals(listOf(startup), navigator.destinations)
        controller.close()
    }

    @Test
    fun `timed out pre-loss capture retains existing fallback`() = runTest {
        val navigator = FakeNavigator()
        val retained = EpubCfi("epubcfi(/6/2!/4/2/1:2)")
        navigator.positions += CompletableDeferred()
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(retained)

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        advanceTimeBy(1_501)
        controller.awaitPendingCapture()
        controller.navigatorAttached()
        runCurrent()

        assertEquals(listOf(retained), navigator.destinations)
        controller.close()
    }

    @Test
    fun `timed out old attachment capture cannot overwrite replacement state`() = runTest {
        val navigator = FakeNavigator()
        val retained = EpubCfi("epubcfi(/6/2!/4/2/1:2)")
        val late = CompletableDeferred<EpubCfiOutcome<EpubCfi>>()
        navigator.positions += late
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(retained)

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        advanceTimeBy(1_501)
        controller.awaitPendingCapture()
        controller.navigatorAttached()
        late.complete(EpubCfiOutcome.Success(EpubCfi("epubcfi(/6/8!/4/2/1:12)")))
        runCurrent()

        assertEquals(listOf(retained), navigator.destinations)
        controller.close()
    }

    @Test
    fun `newer settled position wins over in-flight pre-loss capture`() = runTest {
        val navigator = FakeNavigator()
        val olderCapture = CompletableDeferred<EpubCfiOutcome<EpubCfi>>()
        val newest = EpubCfi("epubcfi(/6/8!/4/2/1:12)")
        navigator.positions += olderCapture
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(EpubCfi("epubcfi(/6/2!/4/2/1:2)"))

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.retainPosition(newest)
        olderCapture.complete(EpubCfiOutcome.Success(EpubCfi("epubcfi(/6/4!/4/2/1:8)")))
        controller.navigatorDetached(firstAttachment)
        controller.navigatorAttached()
        runCurrent()

        assertEquals(listOf(newest), navigator.destinations)
        controller.close()
    }

    @Test
    fun `repeated pre-loss signals coalesce to one bounded capture`() = runTest {
        val navigator = FakeNavigator()
        val captured = EpubCfi("epubcfi(/6/8!/4/2/1:12)")
        navigator.positions += completed(EpubCfiOutcome.Success(captured))
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(null)

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        controller.awaitPendingCapture()
        controller.navigatorAttached()
        runCurrent()

        assertEquals(listOf(captured), navigator.destinations)
        controller.close()
    }

    @Test
    fun `passive observation leaves movement capture reusable by pre-loss lifecycle`() = runTest {
        val navigator = FakeNavigator()
        val retained = EpubCfi("epubcfi(/6/4!/4/2/1:8)")
        navigator.positions += completed(EpubCfiOutcome.Success(retained))
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(null)

        controller.captureAfterViewportMovement()
        runCurrent()
        assertEquals(retained, controller.observePendingCapture())
        controller.captureBeforeNavigatorLoss()
        runCurrent()

        assertEquals(1, navigator.positionRequests)
        controller.navigatorDetached(firstAttachment)
        controller.awaitPendingCapture()
        controller.navigatorAttached()
        runCurrent()
        assertEquals(listOf(retained), navigator.destinations)
        controller.close()
    }

    @Test
    fun `startup progress is only seeded and is not navigated on initial attachment`() = runTest {
        val navigator = FakeNavigator()
        val startup = EpubCfi("epubcfi(/6/6!/4/2/1:6)")
        val controller = controller(navigator)

        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(startup)
        runCurrent()

        assertTrue(navigator.destinations.isEmpty())
        controller.navigatorDetached(firstAttachment)
        controller.navigatorAttached()
        runCurrent()
        assertEquals(listOf(startup), navigator.destinations)
        controller.close()
    }

    @Test
    fun `rapid recreations retain the freshest capture from each attachment`() = runTest {
        val navigator = FakeNavigator()
        val positionB = EpubCfi("epubcfi(/6/4!/4/2/1:8)")
        val positionC = EpubCfi("epubcfi(/6/8!/4/2/1:12)")
        navigator.positions += completed(EpubCfiOutcome.Success(positionB))
        navigator.positions += completed(EpubCfiOutcome.Success(positionC))
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(EpubCfi("epubcfi(/6/2!/4/2/1:2)"))

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        controller.awaitPendingCapture()
        val secondAttachment = controller.navigatorAttached()
        runCurrent()

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.navigatorDetached(secondAttachment)
        controller.awaitPendingCapture()
        controller.navigatorAttached()
        runCurrent()

        assertEquals(listOf(positionB, positionC), navigator.destinations)
        controller.close()
    }

    @Test
    fun `close cancels pending recreation without navigation`() = runTest {
        val navigator = FakeNavigator(EpubCfiReadiness.AwaitingViewport)
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(EpubCfi("epubcfi(/6/2!/4/2/1:2)"))
        controller.navigatorDetached(firstAttachment)
        controller.navigatorAttached()
        runCurrent()

        controller.close()
        navigator.readiness.value = EpubCfiReadiness.Available
        runCurrent()

        assertTrue(navigator.destinations.isEmpty())
    }

    private fun CoroutineScope.controller(
        navigator: FakeNavigator,
        suppress: suspend (suspend () -> Unit) -> Unit = { it() }
    ) = ReaderPositionRetentionController(navigator, this, suppress)

    private fun <T> completed(value: T) = CompletableDeferred(value)

    private class FakeNavigator(initialReadiness: EpubCfiReadiness = EpubCfiReadiness.Available) :
        EpubCfiNavigator {
        override val readiness = MutableStateFlow(initialReadiness)
        val positions = ArrayDeque<CompletableDeferred<EpubCfiOutcome<EpubCfi>>>()
        val destinations = mutableListOf<EpubCfi>()
        var positionRequests = 0

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
            destinations += cfi
            return EpubCfiOutcome.Success(Unit)
        }

        override suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> {
            positionRequests += 1
            return positions.removeFirst().await()
        }

        override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> =
            EpubCfiOutcome.Success(null)

        override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
            EpubCfiOutcome.Failure(EpubCfiFailure.DOM_TARGET_NOT_FOUND)
    }
}
