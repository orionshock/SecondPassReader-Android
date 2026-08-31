package com.secondpasslibrary.reader.reader.readium.cfi

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

class ReadiumCfiOperationLaneTest {
    @Test
    fun `read arriving during navigation waits for navigation to complete`() = runTest {
        val lane = ReadiumCfiOperationLane()
        val navigationStarted = CompletableDeferred<Unit>()
        val releaseNavigation = CompletableDeferred<Unit>()
        val readStarted = CompletableDeferred<Unit>()
        val navigation = async {
            lane.runNavigation {
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
        assertEquals("navigated", navigation.await())
        assertEquals("captured", read.await())
        lane.close()
    }

    @Test
    fun `navigation cancels an active read before entering live renderer`() = runTest {
        val lane = ReadiumCfiOperationLane()
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
            lane.runNavigation {
                assertTrue(readCleanedUp.isCompleted)
                "navigated"
            }
        }

        assertEquals("navigated", navigation.await())
        assertTrue(read.isCancelled)
        lane.close()
    }

    @Test
    fun `new navigation cancels old navigation before entering live renderer`() = runTest {
        val lane = ReadiumCfiOperationLane()
        val firstStarted = CompletableDeferred<Unit>()
        val firstCleanedUp = CompletableDeferred<Unit>()
        val first = async {
            lane.runNavigation {
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
            lane.runNavigation {
                assertTrue(firstCleanedUp.isCompleted)
                "new destination"
            }
        }

        assertEquals("new destination", second.await())
        assertTrue(first.isCancelled)
        lane.close()
    }

    @Test
    fun `reads coalesce to latest while navigation is active`() = runTest {
        val lane = ReadiumCfiOperationLane()
        val navigationStarted = CompletableDeferred<Unit>()
        val releaseNavigation = CompletableDeferred<Unit>()
        val executedReads = mutableListOf<String>()
        val navigation = async {
            lane.runNavigation {
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
        navigation.await()
        third.await()
        assertEquals(listOf("third"), executedReads)
        lane.close()
    }

    @Test
    fun `new navigation waits for non cooperative old navigation cleanup`() = runTest {
        val lane = ReadiumCfiOperationLane()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val first = async {
            lane.runNavigation {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirst.await() }
                "first"
            }
        }
        firstStarted.await()

        val second = async {
            lane.runNavigation {
                secondStarted.complete(Unit)
                "second"
            }
        }
        yield()

        assertFalse(secondStarted.isCompleted)
        releaseFirst.complete(Unit)
        assertEquals("second", second.await())
        assertTrue(first.isCancelled)
        lane.close()
    }

    @Test
    fun `resource identity change rejects captured DOM result`() {
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

    @Test
    fun `stable resource identity publishes captured DOM result`() {
        val identity = ReadiumCfiResourceIdentity(
            navigatorGeneration = 2,
            resourceGeneration = 8,
            href = "EPUB/chapter-2.xhtml"
        )

        assertEquals(
            ReadiumCfiResourceCapture.Stable(identity, "content CFI"),
            coherentResourceCapture(identity, identity, "content CFI")
        )
    }
}
