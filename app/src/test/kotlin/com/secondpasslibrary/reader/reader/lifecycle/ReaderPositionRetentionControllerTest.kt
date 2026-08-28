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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPositionRetentionControllerTest {
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
    fun `newest capture wins when an older navigator result completes late`() = runTest {
        val navigator = FakeNavigator()
        val old = CompletableDeferred<EpubCfiOutcome<EpubCfi>>()
        val newest = EpubCfi("epubcfi(/6/8!/4/2/1:12)")
        navigator.positions += old
        navigator.positions += completed(EpubCfiOutcome.Success(newest))
        val controller = controller(navigator)
        val firstAttachment = controller.navigatorAttached()
        controller.completeStartupRestore(null)

        controller.captureBeforeNavigatorLoss()
        runCurrent()
        controller.captureBeforeNavigatorLoss()
        runCurrent()
        old.complete(EpubCfiOutcome.Success(EpubCfi("epubcfi(/6/4!/4/2/1:4)")))
        runCurrent()
        controller.navigatorDetached(firstAttachment)
        controller.navigatorAttached()
        runCurrent()

        assertEquals(listOf(newest), navigator.destinations)
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

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
            destinations += cfi
            return EpubCfiOutcome.Success(Unit)
        }

        override suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> =
            positions.removeFirst().await()

        override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> =
            EpubCfiOutcome.Success(null)

        override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
            EpubCfiOutcome.Failure(EpubCfiFailure.DOM_TARGET_NOT_FOUND)
    }
}
