package com.secondpasslibrary.reader.marginalia.detail.close

import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionMutationFailure
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import com.secondpasslibrary.reader.marginalia.sessionDetail
import com.secondpasslibrary.reader.marginalia.withClosedMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionCloseControllerTest {
    @Test
    fun `close finalization carries reviewed metadata and unnamed warning`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = closer(capability)
        controller.begin(sessionDetail("session-1"))
        assertTrue(controller.state.value.unnamedWarning)
        controller.updateName("Final pass")
        controller.updateNotes("Final notes")
        controller.submit {}
        advanceUntilIdle()

        val payload = capability.closeRequests.single().second
        assertEquals("Final pass", payload.name)
        assertEquals("Final notes", payload.notes)
        assertEquals(null, payload.progress)
        assertFalse(controller.state.value.open)
    }

    @Test
    fun `ambiguous failure preserves and retries exact payload`() = runTest {
        var attempts = 0
        val capability = RecordingMarginaliaCapability().apply {
            closeCall = { id, input ->
                attempts += 1
                if (attempts == 1) throw SplClientException.ServerUnreachable()
                sessionDetail(id).withClosedMetadata(input.name.orEmpty(), input.notes.orEmpty())
            }
        }
        val controller = closer(capability)
        controller.begin(sessionDetail("session-1"))
        controller.updateName("Exact name")
        controller.updateNotes("Exact notes")
        controller.submit {}
        advanceUntilIdle()

        assertTrue(controller.state.value.exactRetryRequired)
        controller.updateName("Ignored change")
        controller.updateNotes("Ignored change")
        assertEquals("Exact name", controller.state.value.name)
        controller.submit {}
        advanceUntilIdle()

        assertEquals(capability.closeRequests[0].second, capability.closeRequests[1].second)
        assertEquals(2, attempts)
    }

    @Test
    fun `SESSION_CLOSED failure preserves finalization draft`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            closeCall = { _, _ ->
                throw SplClientException.ReadingSessionLifecycleRejected(
                    ReadingSessionLifecycleRejection.SESSION_CLOSED
                )
            }
        }
        val controller = closer(capability)
        controller.begin(sessionDetail("session-1"))
        controller.updateName("Final")
        controller.submit {}
        advanceUntilIdle()

        assertEquals("Final", controller.state.value.name)
        assertEquals(ReadingSessionMutationFailure.SESSION_CLOSED, controller.state.value.failure)
    }

    @Test
    fun `close authentication rejection remains connection owned`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            closeCall = { _, _ -> throw SplClientException.AuthenticationRejected() }
        }
        val controller = closer(capability)
        controller.begin(sessionDetail("session-1"))
        controller.submit {}

        assertEquals(
            MarginaliaConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
    }
}

private fun kotlinx.coroutines.test.TestScope.closer(capability: RecordingMarginaliaCapability) =
    ReadingSessionCloseController(marginaliaProvider(capability), this).also {
        it.prepare(marginaliaProfile())
    }
