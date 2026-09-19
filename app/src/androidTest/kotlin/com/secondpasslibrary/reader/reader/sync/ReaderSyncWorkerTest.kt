package com.secondpasslibrary.reader.reader.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.storage.WorkOfflineStore
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.persistence.ReaderBoundOutboxSession
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.ReaderPendingOutboxSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSyncWorkerTest {
    @Test
    fun pendingWorkRunsTheSharedReconnectOperationThroughTheRealWorker() = runTest {
        val account = LocalReaderAccountKey.from("https://library.example", "profile-1")
        val store = PendingStore()
        val execution = ReaderSyncWorkerExecution(
            ReaderSyncAccountResolution { profile() },
            store,
            ReaderReconnectOperation { _, _ ->
                store.pending = false
                ReaderReconnectReport(hadPendingWork = true, remainingPendingWork = false)
            },
            object : WorkOfflineStore {
                override suspend fun read(accountKey: String) = false
                override suspend fun write(accountKey: String, enabled: Boolean) = Unit
            }
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<ReaderSyncWorker>(context)
            .setInputData(readerSyncWorkRequest(account).workSpec.input)
            .setWorkerFactory(ReaderSyncWorkerFactory(execution))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    private class PendingStore : ReaderOutboxStore {
        var pending = true

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
            sent: List<ReaderOutboxIntent.Annotation>,
            authoritative: List<ReaderAnnotation>
        ) = Unit
    }

    private companion object {
        fun profile() = ConnectionProfile(
            serverOrigin = "https://library.example",
            serverBaseUrl = "https://library.example/",
            apiBaseUrl = "https://library.example/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "2026-08-30",
            clientSessionId = "client-session",
            clientName = "Reader",
            clientType = "reader"
        )
    }
}
