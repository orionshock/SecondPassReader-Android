package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaAnnotationLocation
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionAnnotationsControllerTest {
    @Test
    fun `annotations load independently and preserve server order`() = runTest {
        val second = bookmark("server-2", "client-2")
        val first = bookmark("server-1", "client-1")
        val capability = RecordingMarginaliaCapability().apply {
            annotationsCall = { listOf(second, first) }
        }
        val controller = ReadingSessionAnnotationsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        assertEquals(listOf("session-1"), capability.annotationRequests)
        assertEquals(listOf(second, first), controller.state.value.annotations)
        assertTrue(controller.state.value.loaded)
    }

    @Test
    fun `annotation failure is retryable without losing detail ownership`() = runTest {
        var attempts = 0
        val capability = RecordingMarginaliaCapability().apply {
            annotationsCall = {
                attempts += 1
                if (attempts == 1) throw SplClientException.ServerUnreachable()
                listOf(bookmark("server-1", "client-1"))
            }
        }
        val controller = ReadingSessionAnnotationsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        assertEquals(MarginaliaFailure.UNREACHABLE, controller.state.value.failure)
        controller.retry()
        advanceUntilIdle()

        assertEquals(1, controller.state.value.annotations.size)
        assertEquals(2, attempts)
    }

    @Test
    fun `annotation authentication rejection reaches connection owner`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            annotationsCall = { throw SplClientException.AuthenticationRejected() }
        }
        val controller = ReadingSessionAnnotationsController(marginaliaProvider(capability), this)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")

        assertEquals(
            MarginaliaConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
    }
}

private fun bookmark(id: String, clientId: String) = MarginaliaAnnotation.Bookmark(
    id = id,
    clientId = clientId,
    location = MarginaliaAnnotationLocation("epubcfi(/6/2)", "Chapter 1"),
    createdAt = "2026-08-01T00:00:00Z",
    updatedAt = "2026-08-02T00:00:00Z"
)
