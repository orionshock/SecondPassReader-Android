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
    fun `new operation cancels old operation before entering live renderer`() = runTest {
        val lane = ReadiumCfiOperationLane()
        val firstStarted = CompletableDeferred<Unit>()
        val firstCleanedUp = CompletableDeferred<Unit>()
        val first = async {
            lane.runLatest {
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
            lane.runLatest {
                assertTrue(firstCleanedUp.isCompleted)
                "second"
            }
        }

        assertEquals("second", second.await())
        assertTrue(first.isCancelled)
        lane.close()
    }

    @Test
    fun `new operation waits for non cooperative renderer call to leave the lane`() = runTest {
        val lane = ReadiumCfiOperationLane()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val first = async {
            lane.runLatest {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirst.await() }
                "first"
            }
        }
        firstStarted.await()

        val second = async {
            lane.runLatest {
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
