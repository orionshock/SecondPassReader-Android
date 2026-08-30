package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.ReaderBoundOutboxSession
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.ReaderPendingOutboxSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSyncSchedulerTest {
    @Test
    fun `pending work replaces one account-scoped wakeup`() = runTest {
        val queue = RecordingQueue()
        val scheduler = WorkManagerReaderSyncScheduler(PendingStore(true), queue)

        scheduler.scheduleIfPending(account("one"))
        scheduler.scheduleIfPending(account("one"))

        assertEquals(listOf(account("one"), account("one")), queue.replacements)
        assertEquals(1, queue.replacements.toSet().size)
    }

    @Test
    fun `empty outbox does not enqueue work`() = runTest {
        val queue = RecordingQueue()
        val scheduler = WorkManagerReaderSyncScheduler(PendingStore(false), queue)

        scheduler.scheduleIfPending(account("one"))

        assertEquals(emptyList<LocalReaderAccountKey>(), queue.replacements)
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
        val replacements = mutableListOf<LocalReaderAccountKey>()
        val cancellations = mutableListOf<LocalReaderAccountKey>()

        override fun replace(account: LocalReaderAccountKey) {
            replacements += account
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

        override suspend fun pendingSessionEstablishments(account: LocalReaderAccountKey) =
            emptyList<ReaderOutboxIntent>()

        override suspend fun pendingReaderIntents(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = emptyList<ReaderOutboxIntent>()

        override suspend fun acknowledgeIntent(account: LocalReaderAccountKey, outboxId: String) =
            Unit

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
