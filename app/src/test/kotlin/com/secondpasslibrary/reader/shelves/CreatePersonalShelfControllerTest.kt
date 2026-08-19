package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.SplClientException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePersonalShelfControllerTest {
    @Test
    fun `valid submit trims draft and defaults to Private`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.updateName("  Reading  ")
        controller.updateDescription("  Later books  ")
        var created = 0

        controller.submit { created += 1 }
        advanceUntilIdle()

        assertEquals("Reading", capability.createRequests.single().name)
        assertEquals("Later books", capability.createRequests.single().description)
        assertEquals(ShelfVisibility.PRIVATE, capability.createRequests.single().visibility)
        assertEquals(1, created)
        assertEquals(CreatePersonalShelfState(), controller.state.value)
    }

    @Test
    fun `Listed visibility and duplicate names are passed to server`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        repeat(2) {
            controller.updateName("Favorites")
            controller.updateVisibility(ShelfVisibility.LISTED)
            controller.submit {}
            advanceUntilIdle()
        }

        assertEquals(2, capability.createRequests.size)
        assertTrue(capability.createRequests.all { it.visibility == ShelfVisibility.LISTED })
    }

    @Test
    fun `blank and overlong names are rejected locally`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)

        controller.updateName("  ")
        controller.submit {}
        assertEquals(CreateShelfFieldError.REQUIRED, controller.state.value.nameError)
        controller.updateName("x".repeat(256))
        controller.submit {}

        assertEquals(CreateShelfFieldError.TOO_LONG, controller.state.value.nameError)
        assertTrue(capability.createRequests.isEmpty())
    }

    @Test
    fun `submit exposes loading and failure preserves draft`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val capability = RecordingShelvesCapability().apply {
            createCall = {
                calls.incrementAndGet()
                gate.await()
                throw SplClientException.ServerUnreachable()
            }
        }
        val controller = controller(capability, this)
        controller.updateName("Kept")
        controller.updateDescription("Still here")

        controller.submit {}
        runCurrent()
        assertTrue(controller.state.value.submitting)
        controller.submit {}
        assertEquals(1, calls.get())
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(controller.state.value.submitting)
        assertEquals("Kept", controller.state.value.name)
        assertEquals("Still here", controller.state.value.description)
        assertEquals(CreateShelfFailure.UNREACHABLE, controller.state.value.failure)
    }

    @Test
    fun `reset clears draft without mutation`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.updateName("Discarded")
        controller.updateDescription("Discarded too")

        controller.reset()

        assertEquals(CreatePersonalShelfState(), controller.state.value)
        assertTrue(capability.createRequests.isEmpty())
        assertNull(controller.state.value.failure)
    }

    private fun controller(capability: RecordingShelvesCapability, scope: CoroutineScope) =
        CreatePersonalShelfController(
            ShelvesTestClientProvider(ShelvesTestClient(capability)),
            scope
        ).also { it.prepare(shelvesProfile()) }
}
