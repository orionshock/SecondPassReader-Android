package com.secondpasslibrary.reader.reader.ui.hud

import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatusScope
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderHudControllerTest {
    @Test
    fun `HUD starts visible and auto hides after one idle window`() = runTest {
        val controller = ReaderHudController(backgroundScope, idleTimeoutMillis = 3_000)

        assertTrue(controller.visible.value)
        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(controller.visible.value)
        advanceTimeBy(1)
        runCurrent()

        assertFalse(controller.visible.value)
    }

    @Test
    fun `interaction reveals and restarts while overlays suspend idle hiding`() = runTest {
        val controller = ReaderHudController(backgroundScope, idleTimeoutMillis = 3_000)
        advanceTimeBy(2_000)
        controller.reveal()
        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(controller.visible.value)

        controller.setIdleSuspended(true)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(controller.visible.value)

        controller.setIdleSuspended(false)
        advanceTimeBy(3_000)
        runCurrent()
        assertFalse(controller.visible.value)

        controller.toggle()
        assertTrue(controller.visible.value)
    }

    @Test
    fun `clock and section status formatting are truthful`() {
        assertEquals("7:42 PM", formatReaderClock(LocalTime.of(19, 42), false))
        assertEquals("19:42", formatReaderClock(LocalTime.of(19, 42), true))
        assertEquals(1_000L, millisUntilNextMinute(LocalTime.of(19, 42, 59)))
        assertEquals(
            "1 page left in section",
            readerReadingStatusLabel(
                ReaderReadingStatus(1, ReaderReadingStatusScope.SECTION)
            )
        )
        assertEquals(
            "8 pages left in section",
            readerReadingStatusLabel(
                ReaderReadingStatus(8, ReaderReadingStatusScope.SECTION)
            )
        )
    }
}
