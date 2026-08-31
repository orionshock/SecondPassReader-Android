package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiResourceCapture
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiResourceIdentity
import com.secondpasslibrary.reader.reader.readium.cfi.coherentResourceCapture
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadiumNavigatorOperationLaneTest {
    @Test
    fun `read arriving during navigation waits for navigation to complete`() = runTest {
        val lane = ReadiumNavigatorOperationLane()
        val navigationStarted = CompletableDeferred<Unit>()
        val releaseNavigation = CompletableDeferred<Unit>()
        val readStarted = CompletableDeferred<Unit>()
        val navigation = async {
            lane.runNavigation(1.seconds) {
                navigationStarted.complete(Unit)
                releaseNavigation.await()
                "navigated"
            }
        }
        navigationStarted.await()

        val read = async {
            lane.runLatestRead {
                readStarted.complete(Unit)
                "captured"
            }
        }
        yield()

        assertFalse(navigation.isCancelled)
        assertFalse(readStarted.isCompleted)
        releaseNavigation.complete(Unit)
        assertEquals("navigated", navigation.await().completedValue())
        assertEquals("captured", read.await())
        lane.close()
    }

    @Test
    fun `navigation cancels an active read before entering live renderer`() = runTest {
        val lane = ReadiumNavigatorOperationLane()
        val readStarted = CompletableDeferred<Unit>()
        val readCleanedUp = CompletableDeferred<Unit>()
        val read = async {
            lane.runLatestRead {
                readStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    readCleanedUp.complete(Unit)
                }
            }
        }
        readStarted.await()

        val navigation = async {
            lane.runNavigation(1.seconds) {
                assertTrue(readCleanedUp.isCompleted)
                "navigated"
            }
        }

        assertEquals("navigated", navigation.await().completedValue())
        assertTrue(read.isCancelled)
        lane.close()
    }

    @Test
    fun `new navigation cancels old navigation before entering live renderer`() = runTest {
        val lane = ReadiumNavigatorOperationLane()
        val firstStarted = CompletableDeferred<Unit>()
        val firstCleanedUp = CompletableDeferred<Unit>()
        val first = async {
            lane.runNavigation(1.seconds) {
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    firstCleanedUp.complete(Unit)
                }
            }
        }
        firstStarted.await()

        val second = async {
            lane.runNavigation(1.seconds) {
                assertTrue(firstCleanedUp.isCompleted)
                "new destination"
            }
        }

        assertEquals("new destination", second.await().completedValue())
        assertTrue(first.isCancelled)
        lane.close()
    }

    @Test
    fun `reads coalesce to latest while navigation is active`() = runTest {
        val lane = ReadiumNavigatorOperationLane()
        val navigationStarted = CompletableDeferred<Unit>()
        val releaseNavigation = CompletableDeferred<Unit>()
        val executedReads = mutableListOf<String>()
        val navigation = async {
            lane.runNavigation(1.seconds) {
                navigationStarted.complete(Unit)
                releaseNavigation.await()
            }
        }
        navigationStarted.await()

        val first = async { lane.runLatestRead { executedReads += "first" } }
        yield()
        val second = async { lane.runLatestRead { executedReads += "second" } }
        yield()
        val third = async { lane.runLatestRead { executedReads += "third" } }
        yield()

        first.join()
        second.join()
        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
        assertFalse(third.isCompleted)
        releaseNavigation.complete(Unit)
        navigation.await().completedValue()
        third.await()
        assertEquals(listOf("third"), executedReads)
        lane.close()
    }

    @Test
    fun `timed out command releases lane for later navigation`() = runTest {
        val lane = ReadiumNavigatorOperationLane()

        assertEquals(
            ReadiumNavigatorCommandResult.TimedOut,
            lane.runNavigation(100.milliseconds) { awaitCancellation() }
        )
        assertEquals(
            "next destination",
            lane.runNavigation(1.seconds) { "next destination" }.completedValue()
        )
        lane.close()
    }

    @Test
    fun `new navigation waits for non cooperative old command cleanup`() = runTest {
        val lane = ReadiumNavigatorOperationLane()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val first = async {
            lane.runNavigation(1.seconds) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirst.await() }
                "first"
            }
        }
        firstStarted.await()

        val second = async {
            lane.runNavigation(1.seconds) {
                secondStarted.complete(Unit)
                "second"
            }
        }
        yield()

        assertFalse(secondStarted.isCompleted)
        releaseFirst.complete(Unit)
        assertEquals("second", second.await().completedValue())
        assertTrue(first.isCancelled)
        lane.close()
    }

    @Test
    fun `resource generation change rejects captured DOM result`() {
        val before = ReadiumCfiResourceIdentity(
            navigatorGeneration = 1,
            resourceGeneration = 4,
            href = "EPUB/chapter-1.xhtml"
        )
        val after = before.copy(
            resourceGeneration = 5,
            href = "EPUB/chapter-2.xhtml"
        )

        assertEquals(
            ReadiumCfiResourceCapture.Changed,
            coherentResourceCapture(before, after, "content CFI")
        )
    }
}

private fun <T> ReadiumNavigatorCommandResult<T>.completedValue(): T = when (this) {
    is ReadiumNavigatorCommandResult.Completed -> value
    ReadiumNavigatorCommandResult.TimedOut -> error("Navigator command unexpectedly timed out.")
}
