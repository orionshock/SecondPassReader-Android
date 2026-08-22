package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import com.secondpasslibrary.reader.marginalia.sessionDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionDetailControllerTest {
    @Test
    fun `detail starts independent metadata and annotation loads`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = ReadingSessionDetailController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        assertEquals(listOf("session-1"), capability.detailRequests)
        assertEquals(listOf("session-1"), capability.annotationRequests)
        assertEquals("session-1", controller.state.value.detail?.session?.summary?.id)
        assertNull(controller.state.value.failure)
    }

    @Test
    fun `annotation failure does not erase loaded metadata`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            annotationsCall = { throw SplClientException.ServerUnreachable() }
        }
        val controller = ReadingSessionDetailController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        assertEquals("session-1", controller.state.value.detail?.session?.summary?.id)
        assertEquals(MarginaliaFailure.UNREACHABLE, controller.annotations.state.value.failure)
    }

    @Test
    fun `detail failure is retryable`() = runTest {
        var attempts = 0
        val capability = RecordingMarginaliaCapability().apply {
            detailCall = { id ->
                attempts += 1
                if (attempts == 1) throw SplClientException.ServerUnreachable()
                sessionDetail(id)
            }
        }
        val controller = ReadingSessionDetailController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()
        assertEquals(MarginaliaFailure.UNREACHABLE, controller.state.value.failure)

        controller.retry()
        advanceUntilIdle()
        assertEquals("session-1", controller.state.value.detail?.session?.summary?.id)
        assertEquals(2, attempts)
    }

    @Test
    fun `authentication rejection is emitted for connection ownership`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            detailCall = { throw SplClientException.AuthenticationRejected() }
        }
        val controller = ReadingSessionDetailController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")

        assertEquals(
            MarginaliaConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
    }
}
