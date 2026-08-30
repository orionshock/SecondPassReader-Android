package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressPersistenceControllerTest {
    @Test
    fun `candidate persists immediately and quiet window requests durable sync lane`() = runTest {
        val store = RecordingStore()
        var syncRequests = 0
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = ReaderProgressPersistenceController(backgroundScope, store) {
            syncRequests += 1
        }
        controller.start(account(), session(), progress)

        progress.value = activeProgress(CFI_A, 1)
        runCurrent()

        assertEquals(listOf(CFI_A), store.progressWrites)
        assertEquals(0, syncRequests)
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, syncRequests)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, syncRequests)
    }

    @Test
    fun `newer position persists and restarts quiet window`() = runTest {
        val store = RecordingStore()
        var syncRequests = 0
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = ReaderProgressPersistenceController(backgroundScope, store) {
            syncRequests += 1
        }
        controller.start(account(), session(), progress)

        progress.value = activeProgress(CFI_A, 1)
        runCurrent()
        advanceTimeBy(2_000)
        progress.value = activeProgress(CFI_B, 2)
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()

        assertEquals(listOf(CFI_A, CFI_B), store.progressWrites)
        assertEquals(0, syncRequests)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, syncRequests)
    }

    @Test
    fun `flush durably captures latest state before requesting sync`() = runTest {
        val store = RecordingStore()
        var syncRequests = 0
        val progress = MutableStateFlow<ReaderProgressState?>(activeProgress(CFI_A, 1))
        val controller = ReaderProgressPersistenceController(backgroundScope, store) {
            syncRequests += 1
        }
        controller.start(account(), session(), progress)

        val result = controller.flushLatest()

        assertEquals(ReaderProgressFlushResult.PERSISTED, result)
        assertEquals(CFI_A, store.progressWrites.last())
        assertEquals(1, syncRequests)
    }

    @Test
    fun `closed Session cannot persist or request delivery`() = runTest {
        val store = RecordingStore()
        var syncRequests = 0
        val progress = MutableStateFlow<ReaderProgressState?>(
            ReaderProgressState(SESSION_ID, ReaderSessionStatus.CLOSED, true, EpubCfi(CFI_A), 1)
        )
        val controller = ReaderProgressPersistenceController(backgroundScope, store) {
            syncRequests += 1
        }
        controller.start(account(), session(ReaderSessionStatus.CLOSED), progress)
        runCurrent()

        assertEquals(ReaderProgressFlushResult.NOT_WRITABLE, controller.flushLatest())
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(store.progressWrites.isEmpty())
        assertEquals(0, syncRequests)
    }

    @Test
    fun `reset cancels stale quiet-window request`() = runTest {
        val store = RecordingStore()
        var syncRequests = 0
        val progress = MutableStateFlow<ReaderProgressState?>(null)
        val controller = ReaderProgressPersistenceController(backgroundScope, store) {
            syncRequests += 1
        }
        controller.start(account(), session(), progress)
        progress.value = activeProgress(CFI_A, 1)
        runCurrent()

        controller.reset()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(0, syncRequests)
    }

    private fun activeProgress(cfi: String, version: Long) = ReaderProgressState(
        SESSION_ID,
        ReaderSessionStatus.ACTIVE,
        captureEnabled = true,
        EpubCfi(cfi),
        version
    )

    private fun session(status: ReaderSessionStatus = ReaderSessionStatus.ACTIVE) =
        ReaderSessionContext(SESSION_ID, status, null)

    private fun account() = LocalReaderAccountKey.from(
        "https://library.example",
        "profile-1"
    )

    private class RecordingStore : LocalReaderStateStore {
        val progressWrites = mutableListOf<String>()

        override suspend fun selectOfflineSession(account: LocalReaderAccountKey, bookId: String) =
            error("unused")

        override suspend fun retainServerSession(
            account: LocalReaderAccountKey,
            bookId: String,
            session: ReaderSessionContext
        ) = session

        override suspend fun writeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String,
            provenance: LocalReaderWriteProvenance
        ) {
            progressWrites += cfi
        }

        override suspend fun acknowledgeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String
        ) = Unit

        override suspend fun readAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = emptyList<ReaderAnnotation>()

        override suspend fun applyAnnotationMutation(
            account: LocalReaderAccountKey,
            localSessionId: String,
            request: ReaderAnnotationMutationRequest
        ) = emptyList<ReaderAnnotation>()

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            acknowledgedMutation: ReaderAnnotationMutationRequest?
        ) = Unit

        override suspend fun purgeAccount(account: LocalReaderAccountKey) = Unit
    }

    private companion object {
        const val SESSION_ID = "local-session"
        const val CFI_A = "epubcfi(/6/2!/4/2:3)"
        const val CFI_B = "epubcfi(/6/4!/4/2:7)"
    }
}
