package com.secondpasslibrary.reader.reader.readium

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettledViewportMovementTrackerTest {
    @Test
    fun `movement is debounced after the attached location baseline`() = runTest {
        val tracker = SettledViewportMovementTracker<String>(settleDelayMillis = 250)
        val location = MutableStateFlow("initial")
        val events = mutableListOf<Long>()
        backgroundScope.launch {
            tracker.settled().collect { events += it.sequence }
        }
        tracker.bind(location)
        runCurrent()

        location.value = "page-2"
        advanceTimeBy(100)
        location.value = "page-3"
        advanceTimeBy(249)
        assertEquals(emptyList<Long>(), events)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(1L), events)
    }

    @Test
    fun `navigator rebind establishes a new baseline and drops stale navigator changes`() =
        runTest {
            val tracker = SettledViewportMovementTracker<String>(settleDelayMillis = 250)
            val first = MutableStateFlow("page-1")
            val second = MutableStateFlow("page-5")
            val events = mutableListOf<Long>()
            backgroundScope.launch {
                tracker.settled().collect { events += it.sequence }
            }

            tracker.bind(first)
            runCurrent()
            first.value = "page-2"
            advanceTimeBy(100)
            tracker.bind(second)
            runCurrent()
            first.value = "page-3"
            advanceTimeBy(250)
            runCurrent()
            assertEquals(emptyList<Long>(), events)

            second.value = "page-6"
            advanceTimeBy(250)
            runCurrent()
            assertEquals(listOf(1L), events)
        }

    @Test
    fun `recreation restoration is suppressed and resumed from the restored baseline`() = runTest {
        val tracker = SettledViewportMovementTracker<String>(settleDelayMillis = 250)
        val location = MutableStateFlow("cover")
        val events = mutableListOf<Long>()
        backgroundScope.launch {
            tracker.settled().collect { events += it.sequence }
        }
        tracker.bind(location)
        runCurrent()

        tracker.suppress()
        location.value = "restored-page"
        advanceTimeBy(250)
        tracker.resumeWithCurrentAsBaseline()
        runCurrent()
        advanceTimeBy(250)
        assertEquals(emptyList<Long>(), events)

        location.value = "user-page"
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf(1L), events)
    }
}
