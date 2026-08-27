package com.secondpasslibrary.reader.reader.annotations.selection

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.cfi.EpubSelectionBounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSelectionControllerTest {
    @Test
    fun `nonblank selection preserves canonical range and context`() = runTest {
        val events = FakeSelectionEvents()
        val navigator = FakeSelectionNavigator(
            EpubCfiSelection(
                EpubCfi(SELECTION_CFI),
                "Selected text",
                "Before",
                "After",
                chapterOrdinal = 3,
                totalProgression = 0.427,
                bounds = EpubSelectionBounds(12f, 34f, 56f, 78f)
            )
        )
        val controller = ReaderSelectionController(backgroundScope)
        controller.attach(events, navigator)
        runCurrent()
        events.emit()
        runCurrent()

        assertEquals(SELECTION_CFI, controller.selection.value?.cfi?.value)
        assertEquals("Selected text", controller.selection.value?.selectedText)
        assertEquals("Before", controller.selection.value?.prefix)
        assertEquals("After", controller.selection.value?.suffix)
        assertEquals(EpubSelectionBounds(12f, 34f, 56f, 78f), controller.selection.value?.bounds)
        assertEquals("Chapter 03 · 43%", controller.selection.value?.locationLabel)
    }

    @Test
    fun `collapsed or blank selection does not publish toolbar state`() = runTest {
        val events = FakeSelectionEvents()
        val navigator = FakeSelectionNavigator(null)
        val controller = ReaderSelectionController(backgroundScope)
        controller.attach(events, navigator)
        runCurrent()
        events.emit()
        runCurrent()
        assertNull(controller.selection.value)

        navigator.selection = EpubCfiSelection(
            EpubCfi(SELECTION_CFI),
            "   ",
            null,
            null,
            chapterOrdinal = 1,
            totalProgression = 0.0
        )
        events.emit()
        runCurrent()
        assertNull(controller.selection.value)
    }

    @Test
    fun `dismiss clears app and renderer selection`() = runTest {
        val events = FakeSelectionEvents()
        val controller = ReaderSelectionController(backgroundScope)
        controller.attach(events, FakeSelectionNavigator(selection()))
        runCurrent()
        events.emit()
        runCurrent()

        controller.dismiss()
        runCurrent()

        assertNull(controller.selection.value)
        assertEquals(1, events.clearCount)
    }

    @Test
    fun `location label is bounded and never contains raw CFI`() {
        assertEquals("Chapter 01 · 0%", readerLocationLabel(1, -1.0))
        assertEquals("Chapter 12 · 100%", readerLocationLabel(12, 2.0))
        assertEquals("Chapter 09", readerLocationLabel(9, null))
    }

    private class FakeSelectionEvents : ReaderSelectionEvents {
        private val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var clearCount = 0

        override fun changes(): Flow<Unit> = signals
        override suspend fun clear() {
            clearCount += 1
        }

        fun emit() {
            signals.tryEmit(Unit)
        }
    }

    private class FakeSelectionNavigator(var selection: EpubCfiSelection?) : EpubCfiNavigator {
        override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)
        override suspend fun goTo(cfi: EpubCfi) = unavailable<Unit>()
        override suspend fun currentPosition() = unavailable<EpubCfi>()
        override suspend fun currentSelection() = EpubCfiOutcome.Success(selection)
        override suspend fun resolve(cfi: EpubCfi) = unavailable<EpubCfiResolution>()
    }

    private fun selection() = EpubCfiSelection(
        EpubCfi(SELECTION_CFI),
        "Selected",
        null,
        null,
        chapterOrdinal = 1,
        totalProgression = 0.1
    )
}

private fun <T> unavailable(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)

private const val SELECTION_CFI = "epubcfi(/6/2!/4/2,/1:0,/1:4)"
