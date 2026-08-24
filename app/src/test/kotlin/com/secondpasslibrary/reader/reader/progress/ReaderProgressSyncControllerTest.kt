package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressSyncControllerTest {
    @Test
    fun `single candidate writes once after three seconds quiet`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        progress.value = activeProgress(CFI_A, version = 1)
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, writer.calls.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(WriteCall("session-1", CFI_A)), writer.calls)
        assertEquals(1L, controller.state.value?.latestSyncedVersion)
        assertFalse(requireNotNull(controller.state.value).dirty)
        controller.close()
    }

    @Test
    fun `rolling candidates coalesce to latest after quiet window`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        progress.value = activeProgress(CFI_A, 1)
        runCurrent()
        advanceTimeBy(1_000)
        progress.value = activeProgress(CFI_B, 2)
        runCurrent()
        advanceTimeBy(1_000)
        progress.value = activeProgress(CFI_C, 3)
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, writer.calls.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(WriteCall("session-1", CFI_C)), writer.calls)
        controller.close()
    }

    @Test
    fun `duplicate version neither replaces candidate nor restarts timer`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        progress.value = activeProgress(CFI_A, 1)
        runCurrent()
        advanceTimeBy(2_000)
        progress.value = activeProgress(CFI_B, 1)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(WriteCall("session-1", CFI_A)), writer.calls)
        controller.close()
    }

    @Test
    fun `closed Session never writes`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        progress.value = activeProgress(CFI_A, 1).copy(
            sessionStatus = ReaderSessionStatus.CLOSED,
            captureEnabled = true
        )
        runCurrent()
        val flush = async { controller.flushLatest() }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(ReaderProgressFlushResult.NOT_WRITABLE, flush.await())
        assertEquals(0, writer.calls.size)
        assertFalse(requireNotNull(controller.state.value).dirty)
        controller.close()
    }

    @Test
    fun `newer candidate remains dirty while older write is in flight`() = runTest {
        val firstResult = CompletableDeferred<ReaderProgressWriteOutcome>()
        val secondResult = CompletableDeferred<ReaderProgressWriteOutcome>()
        val writer = RecordingWriter { call ->
            if (call.cfi == CFI_A) firstResult.await() else secondResult.await()
        }
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        progress.value = activeProgress(CFI_A, 1)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        progress.value = activeProgress(CFI_B, 2)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(1L, controller.state.value?.inFlightVersion)
        assertEquals(2L, controller.state.value?.latestCapturedVersion)
        assertTrue(requireNotNull(controller.state.value).dirty)
        firstResult.complete(ReaderProgressWriteOutcome.Success)
        runCurrent()

        assertEquals(
            listOf(WriteCall("session-1", CFI_A), WriteCall("session-1", CFI_B)),
            writer.calls
        )
        assertEquals(1L, controller.state.value?.latestSyncedVersion)
        assertEquals(2L, controller.state.value?.inFlightVersion)
        assertTrue(requireNotNull(controller.state.value).dirty)

        secondResult.complete(ReaderProgressWriteOutcome.Success)
        runCurrent()
        assertEquals(2L, controller.state.value?.latestSyncedVersion)
        controller.close()
    }

    @Test
    fun `failure leaves candidate dirty without automatic retry`() = runTest {
        val writer = RecordingWriter {
            ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
        }
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        progress.value = activeProgress(CFI_A, 1)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(1, writer.calls.size)
        assertTrue(requireNotNull(controller.state.value).dirty)
        assertEquals(0L, controller.state.value?.latestSyncedVersion)
        assertEquals(ReaderProgressSyncFailure.UNAVAILABLE, controller.state.value?.lastFailure)
        controller.close()
    }

    @Test
    fun `Session replacement cancels stale pending candidate`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        progress.value = activeProgress(CFI_A, 1, sessionId = "session-a")
        runCurrent()
        advanceTimeBy(2_000)
        progress.value = activeProgress(CFI_B, 1, sessionId = "session-b")
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(listOf(WriteCall("session-b", CFI_B)), writer.calls)
        assertEquals("session-b", controller.state.value?.sessionId)
        controller.close()
    }

    @Test
    fun `unavailable authority retains dirty candidate until authority returns`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = ReaderProgressSyncController(this, writer)
        controller.start(profile(), progress)

        progress.value = activeProgress(CFI_A, 1)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(0, writer.calls.size)
        assertTrue(requireNotNull(controller.state.value).dirty)
        assertNull(controller.state.value?.inFlightVersion)

        controller.setAuthorityAvailable(true)
        runCurrent()
        assertEquals(listOf(WriteCall("session-1", CFI_A)), writer.calls)
        controller.close()
    }

    @Test
    fun `flush with no dirty candidate returns clean without writing`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)

        val result = async { controller.flushLatest() }
        runCurrent()

        assertEquals(ReaderProgressFlushResult.CLEAN, result.await())
        assertTrue(writer.calls.isEmpty())
        controller.close()
    }

    @Test
    fun `flush writes dirty candidate immediately`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)
        progress.value = activeProgress(CFI_A, 1)
        runCurrent()

        val result = async { controller.flushLatest() }
        runCurrent()

        assertEquals(ReaderProgressFlushResult.FLUSHED, result.await())
        assertEquals(listOf(WriteCall("session-1", CFI_A)), writer.calls)
        assertFalse(requireNotNull(controller.state.value).dirty)
        controller.close()
    }

    @Test
    fun `flush stays serialized behind in-flight write and sends newest candidate`() = runTest {
        val firstResult = CompletableDeferred<ReaderProgressWriteOutcome>()
        val writer = RecordingWriter { call ->
            if (call.cfi == CFI_A) firstResult.await() else ReaderProgressWriteOutcome.Success
        }
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)
        progress.value = activeProgress(CFI_A, 1)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        progress.value = activeProgress(CFI_B, 2)
        runCurrent()

        val flush = async { controller.flushLatest() }
        runCurrent()
        assertEquals(listOf(WriteCall("session-1", CFI_A)), writer.calls)

        firstResult.complete(ReaderProgressWriteOutcome.Success)
        runCurrent()

        assertEquals(
            listOf(WriteCall("session-1", CFI_A), WriteCall("session-1", CFI_B)),
            writer.calls
        )
        assertEquals(ReaderProgressFlushResult.FLUSHED, flush.await())
        assertEquals(2L, controller.state.value?.latestSyncedVersion)
        controller.close()
    }

    @Test
    fun `flush timeout is bounded and does not falsely acknowledge candidate`() = runTest {
        val blocked = CompletableDeferred<ReaderProgressWriteOutcome>()
        val writer = RecordingWriter { blocked.await() }
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)
        progress.value = activeProgress(CFI_A, 1)
        runCurrent()

        val flush = async { controller.flushLatest() }
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()

        assertEquals(ReaderProgressFlushResult.TIMED_OUT, flush.await())
        assertTrue(requireNotNull(controller.state.value).dirty)
        assertEquals(0L, controller.state.value?.latestSyncedVersion)
        blocked.complete(ReaderProgressWriteOutcome.Success)
        runCurrent()
        controller.close()
    }

    @Test
    fun `flush without authority leaves candidate dirty`() = runTest {
        val writer = RecordingWriter()
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = ReaderProgressSyncController(this, writer)
        controller.start(profile(), progress)
        progress.value = activeProgress(CFI_A, 1)
        runCurrent()

        val result = async { controller.flushLatest() }
        runCurrent()

        assertEquals(ReaderProgressFlushResult.AUTHORITY_UNAVAILABLE, result.await())
        assertTrue(requireNotNull(controller.state.value).dirty)
        assertTrue(writer.calls.isEmpty())
        controller.close()
    }

    @Test
    fun `duplicate flush signals share one serialized write`() = runTest {
        val writeResult = CompletableDeferred<ReaderProgressWriteOutcome>()
        val writer = RecordingWriter { writeResult.await() }
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)
        progress.value = activeProgress(CFI_A, 1)
        runCurrent()

        val backgroundFlush = async { controller.flushLatest() }
        val exitFlush = async { controller.flushLatest() }
        runCurrent()
        assertEquals(1, writer.calls.size)

        writeResult.complete(ReaderProgressWriteOutcome.Success)
        runCurrent()
        assertEquals(ReaderProgressFlushResult.FLUSHED, backgroundFlush.await())
        assertEquals(ReaderProgressFlushResult.FLUSHED, exitFlush.await())
        assertEquals(1, writer.calls.size)
        controller.close()
    }

    @Test
    fun `flush failure remains dirty without retry loop`() = runTest {
        val writer = RecordingWriter {
            ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
        }
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)
        progress.value = activeProgress(CFI_A, 1)
        runCurrent()

        val result = async { controller.flushLatest() }
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(ReaderProgressFlushResult.FAILED, result.await())
        assertEquals(1, writer.calls.size)
        assertTrue(requireNotNull(controller.state.value).dirty)
        controller.close()
    }

    @Test
    fun `Session replacement ends stale flush ownership`() = runTest {
        val blocked = CompletableDeferred<ReaderProgressWriteOutcome>()
        val writer = RecordingWriter { blocked.await() }
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = controller(writer, progress)
        progress.value = activeProgress(CFI_A, 1, "session-a")
        runCurrent()
        val flush = async { controller.flushLatest() }
        runCurrent()

        progress.value = activeProgress(CFI_B, 1, "session-b")
        runCurrent()

        assertEquals(ReaderProgressFlushResult.OWNERSHIP_CHANGED, flush.await())
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(WriteCall("session-b", CFI_B), writer.calls.last())
        controller.close()
    }

    private fun TestScope.controller(
        writer: ReaderProgressWriter,
        progress: MutableStateFlow<ReaderProgressState?>
    ) = ReaderProgressSyncController(this, writer).also {
        it.setAuthorityAvailable(true)
        it.start(profile(), progress)
        runCurrent()
    }

    private fun activeProgress(cfi: EpubCfi, version: Long, sessionId: String = "session-1") =
        ReaderProgressState(
            sessionId = sessionId,
            sessionStatus = ReaderSessionStatus.ACTIVE,
            captureEnabled = true,
            latestCandidate = cfi,
            candidateVersion = version
        )

    private class RecordingWriter(
        private val outcome: suspend (WriteCall) -> ReaderProgressWriteOutcome = {
            ReaderProgressWriteOutcome.Success
        }
    ) : ReaderProgressWriter {
        val calls = mutableListOf<WriteCall>()

        override suspend fun replace(
            profile: ConnectionProfile,
            sessionId: String,
            cfi: EpubCfi
        ): ReaderProgressWriteOutcome {
            val call = WriteCall(sessionId, cfi)
            calls += call
            return outcome(call)
        }
    }

    private data class WriteCall(val sessionId: String, val cfi: EpubCfi)

    private fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-23",
        clientSessionId = "client-session-1",
        clientName = "Reader",
        clientType = "reader"
    )

    private companion object {
        val CFI_A = EpubCfi("epubcfi(/6/2!/4/2:3)")
        val CFI_B = EpubCfi("epubcfi(/6/4!/4/2:7)")
        val CFI_C = EpubCfi("epubcfi(/6/6!/4/2:11)")
    }
}
