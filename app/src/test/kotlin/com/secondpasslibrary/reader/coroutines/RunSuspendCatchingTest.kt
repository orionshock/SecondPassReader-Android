package com.secondpasslibrary.reader.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSuspendCatchingTest {
    @Test
    fun `returns successful result`() = runTest {
        assertEquals("loaded", runSuspendCatching { "loaded" }.getOrThrow())
    }

    @Test
    fun `captures ordinary failure without replacing it`() = runTest {
        val expected = IllegalStateException("failed")

        val result = runSuspendCatching<String> { throw expected }

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun `rethrows cancellation without replacing it`() = runTest {
        val expected = CancellationException("cancelled")

        assertCancellationIdentity(expected)
    }

    @Test
    fun `rethrows derived cancellation without replacing it`() = runTest {
        val expected = TestCancellationException()

        assertCancellationIdentity(expected)
    }

    private suspend fun assertCancellationIdentity(expected: CancellationException) {
        var caught: CancellationException? = null
        try {
            runSuspendCatching<Unit> { throw expected }
        } catch (cancellation: CancellationException) {
            caught = cancellation
        }
        assertSame(expected, caught)
    }
}

private class TestCancellationException : CancellationException("derived")
