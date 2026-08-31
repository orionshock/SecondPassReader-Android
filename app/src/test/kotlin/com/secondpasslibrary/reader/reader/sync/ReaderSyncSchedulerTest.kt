package com.secondpasslibrary.reader.reader.sync

import androidx.work.ExistingWorkPolicy
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.persistence.ReaderBoundOutboxSession
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.ReaderPendingOutboxSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSyncSchedulerTest {
    @Test
    fun `unique work keeps existing retry and backoff state`() {
        assertEquals(ExistingWorkPolicy.KEEP, READER_SYNC_EXISTING_WORK_POLICY)
    }

    @Test
    fun `pending work ensures one account-scoped wakeup without replacement intent`() = runTest {
        val queue = RecordingQueue()
        val scheduler = WorkManagerReaderSyncScheduler(PendingStore(true), queue)

        scheduler.ensureEnqueued(account("one"))
        scheduler.ensureEnqueued(account("one"))

        assertEquals(listOf(account("one"), account("one")), queue.enqueues)
        assertEquals(1, queue.enqueues.toSet().size)
    }

    @Test
    fun `empty outbox does not enqueue work`() = runTest {
        val queue = RecordingQueue()
        val scheduler = WorkManagerReaderSyncScheduler(PendingStore(false), queue)

        scheduler.ensureEnqueued(account("one"))

        assertEquals(emptyList<LocalReaderAccountKey>(), queue.enqueues)
    }

    @Test
    fun `cleanup cancellation targets only exact account scope`() {
        val queue = RecordingQueue()
        val scheduler = WorkManagerReaderSyncScheduler(PendingStore(false), queue)

        scheduler.cancel(account("one"))

        assertEquals(listOf(account("one")), queue.cancellations)
        assertEquals(false, queue.cancellations.contains(account("other")))
    }

    private class RecordingQueue : ReaderSyncWorkQueue {
        val enqueues = mutableListOf<LocalReaderAccountKey>()
        val cancellations = mutableListOf<LocalReaderAccountKey>()

        override fun ensureEnqueued(account: LocalReaderAccountKey) {
            enqueues += account
        }

        override fun cancel(account: LocalReaderAccountKey) {
            cancellations += account
        }
    }

    private class PendingStore(private val pending: Boolean) : ReaderOutboxStore {
        override suspend fun hasPendingWork(account: LocalReaderAccountKey) = pending

        override suspend fun pendingSessions(account: LocalReaderAccountKey) =
            emptyList<ReaderPendingOutboxSession>()

        override suspend fun boundPendingSessions(account: LocalReaderAccountKey) =
            emptyList<ReaderBoundOutboxSession>()

        override suspend fun pendingReaderIntents(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = emptyList<ReaderOutboxIntent>()

        override suspend fun acceptProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            sent: ReaderOutboxIntent.Progress
        ) = Unit

        override suspend fun acceptAnnotationBatch(
            account: LocalReaderAccountKey,
            localSessionId: String,
            sent: List<ReaderOutboxIntent>,
            authoritative: List<ReaderAnnotation>
        ) = Unit
    }

    private companion object {
        fun account(profileId: String) =
            LocalReaderAccountKey.from("https://library.example", profileId)
    }
}
