package com.secondpasslibrary.reader.marginalia.detail.metadata

import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionMutationFailure
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionNameError
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import com.secondpasslibrary.reader.marginalia.sessionDetail
import com.secondpasslibrary.reader.marginalia.sessionSummary
import com.secondpasslibrary.reader.marginalia.withMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionMetadataEditorControllerTest {
    @Test
    fun `active edit supports dirty cancel and blank name`() = runTest {
        val controller = editor(RecordingMarginaliaCapability())
        controller.begin(sessionDetail("session-1").withMetadata("Original", "Notes"))

        assertFalse(controller.state.value.dirty)
        controller.updateName("")
        controller.updateNotes("Changed notes")
        assertTrue(controller.state.value.dirty)
        controller.reset()
        assertFalse(controller.state.value.open)
    }

    @Test
    fun `successful edit sends name and notes then clears draft`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = editor(capability)
        controller.begin(sessionDetail("session-1"))
        controller.updateName("")
        controller.updateNotes("Revised notes")
        var resultName: String? = null
        controller.submit { resultName = it.session.summary.name }
        advanceUntilIdle()

        assertEquals("", capability.metadataRequests.single().second.name)
        assertEquals("Revised notes", capability.metadataRequests.single().second.notes)
        assertEquals("", resultName)
        assertFalse(controller.state.value.open)
    }

    @Test
    fun `name over 255 is rejected before SDK call`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = editor(capability)
        controller.begin(sessionDetail("session-1"))
        controller.updateName("x".repeat(256))
        controller.submit {}

        assertEquals(ReadingSessionNameError.TOO_LONG, controller.state.value.nameError)
        assertTrue(capability.metadataRequests.isEmpty())
    }

    @Test
    fun `failed update preserves draft and classifies closed Session`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            metadataCall = { _, _ ->
                throw SplClientException.ReadingSessionLifecycleRejected(
                    ReadingSessionLifecycleRejection.SESSION_CLOSED
                )
            }
        }
        val controller = editor(capability)
        controller.begin(sessionDetail("session-1"))
        controller.updateName("Keep me")
        controller.submit {}
        advanceUntilIdle()

        assertEquals("Keep me", controller.state.value.name)
        assertEquals(ReadingSessionMutationFailure.SESSION_CLOSED, controller.state.value.failure)
    }

    @Test
    fun `closed Session cannot enter editor`() = runTest {
        val controller = editor(RecordingMarginaliaCapability())
        val closed = sessionDetail("session-1").copy(
            session = sessionDetail("session-1").session.copy(
                summary = sessionSummary("session-1", ReadingSessionStatus.CLOSED)
            )
        )
        controller.begin(closed)
        assertFalse(controller.state.value.open)
    }

    @Test
    fun `authentication rejection remains connection owned`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            metadataCall = { _, _ -> throw SplClientException.AuthenticationRejected() }
        }
        val controller = editor(capability)
        controller.begin(sessionDetail("session-1"))
        controller.updateName("Changed")
        controller.submit {}

        assertEquals(
            MarginaliaConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
        assertNull(controller.state.value.nameError)
    }
}

private fun kotlinx.coroutines.test.TestScope.editor(capability: RecordingMarginaliaCapability) =
    ReadingSessionMetadataEditorController(marginaliaProvider(capability), this).also {
        it.prepare(marginaliaProfile())
    }
