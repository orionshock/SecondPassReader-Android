package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import com.secondpasslibrary.reader.marginalia.sessionDetail
import com.secondpasslibrary.reader.marginalia.withClosedMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionDetailControllerTest {
    @Test
    fun `detail starts independent metadata and annotation loads`() = runTest {
        val capability = RecordingMarginaliaCapability()
        val controller = detailController(capability)
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
        val controller = detailController(capability)
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
        val controller = detailController(capability)
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
        val controller = detailController(capability)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")

        assertEquals(
            MarginaliaConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
    }

    @Test
    fun `metadata success emits one authoritative update through construction sink`() = runTest {
        val updates = mutableListOf<ReadingSessionDetailResult>()
        val controller = detailController(RecordingMarginaliaCapability(), updates)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        controller.beginMetadataEdit()
        controller.metadataEditor.updateName("Renamed")
        controller.saveMetadata()
        advanceUntilIdle()

        assertEquals(1, updates.size)
        assertEquals("Renamed", updates.single().session.summary.name)
    }

    @Test
    fun `metadata failure emits no authoritative update`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            metadataCall = { _, _ -> throw SplClientException.ServerUnreachable() }
        }
        val updates = mutableListOf<ReadingSessionDetailResult>()
        val controller = detailController(capability, updates)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        controller.beginMetadataEdit()
        controller.metadataEditor.updateName("Renamed")
        controller.saveMetadata()
        advanceUntilIdle()

        assertTrue(updates.isEmpty())
    }

    @Test
    fun `close success emits one authoritative update`() = runTest {
        val updates = mutableListOf<ReadingSessionDetailResult>()
        val controller = detailController(RecordingMarginaliaCapability(), updates)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        controller.beginClose()
        controller.confirmClose()
        advanceUntilIdle()

        assertEquals(1, updates.size)
        assertEquals(ReadingSessionStatus.CLOSED, updates.single().session.summary.status)
    }

    @Test
    fun `ambiguous close emits only after successful exact retry`() = runTest {
        var attempts = 0
        val capability = RecordingMarginaliaCapability().apply {
            closeCall = { id, input ->
                attempts += 1
                if (attempts == 1) throw SplClientException.ServerUnreachable()
                sessionDetail(id).withClosedMetadata(input.name.orEmpty(), input.notes.orEmpty())
            }
        }
        val updates = mutableListOf<ReadingSessionDetailResult>()
        val controller = detailController(capability, updates)
        controller.prepare(marginaliaProfile())
        controller.select("session-1")
        advanceUntilIdle()

        controller.beginClose()
        controller.confirmClose()
        advanceUntilIdle()
        assertTrue(updates.isEmpty())

        controller.confirmClose()
        advanceUntilIdle()
        assertEquals(1, updates.size)
    }
}

private fun TestScope.detailController(
    capability: RecordingMarginaliaCapability,
    updates: MutableList<ReadingSessionDetailResult> = mutableListOf()
) = ReadingSessionDetailController(
    marginaliaProvider(capability),
    this,
    ReadingSessionAuthoritativeUpdateSink(updates::add)
)
