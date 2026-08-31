package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.ConnectionProfileStore
import com.secondpasslibrary.reader.connection.PersistedAccountContext
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.persistence.ReaderBoundOutboxSession
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.ReaderPendingOutboxSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSyncWorkerExecutionTest {
    @Test
    fun `missing or stale account is a successful no-op`() = runTest {
        val store = PendingStore(true)
        var reconnects = 0
        val execution = execution(store, ReaderSyncAccountResolution { null }) { _, _ ->
            reconnects += 1
            successfulReport()
        }

        val result = execution.execute(account().value)

        assertEquals(ReaderSyncWorkerOutcome.SUCCESS, result)
        assertEquals(0, reconnects)
    }

    @Test
    fun `empty outbox exits successfully without reconnect`() = runTest {
        val store = PendingStore(false)
        var reconnects = 0
        val execution = execution(store) { _, _ ->
            reconnects += 1
            successfulReport()
        }

        assertEquals(ReaderSyncWorkerOutcome.SUCCESS, execution.execute(account().value))
        assertEquals(0, reconnects)
    }

    @Test
    fun `pending account invokes shared reconnect and succeeds after drain`() = runTest {
        val store = PendingStore(true)
        var received: LocalReaderAccountKey? = null
        val execution = execution(store) { _, account ->
            received = account
            store.pending = false
            successfulReport()
        }

        val result = execution.execute(account().value)

        assertEquals(ReaderSyncWorkerOutcome.SUCCESS, result)
        assertEquals(account(), received)
    }

    @Test
    fun `transient or remaining work requests exponential WorkManager retry`() = runTest {
        val transient = execution(PendingStore(true)) { _, _ ->
            ReaderReconnectReport(true, true, transientFailure = true)
        }
        val raced = execution(PendingStore(true)) { _, _ ->
            ReaderReconnectReport(true, true)
        }

        assertEquals(ReaderSyncWorkerOutcome.RETRY, transient.execute(account().value))
        assertEquals(ReaderSyncWorkerOutcome.RETRY, raced.execute(account().value))
    }

    @Test
    fun `authentication failure preserves work without WorkManager retry loop`() = runTest {
        val store = PendingStore(true)
        val execution = execution(store) { _, _ ->
            ReaderReconnectReport(true, true, authenticationRequired = true)
        }

        assertEquals(
            ReaderSyncWorkerOutcome.AUTHENTICATION_REQUIRED,
            execution.execute(account().value)
        )
        assertEquals(true, store.pending)
    }

    @Test
    fun `invalid persisted scope cannot reach Reader sync`() = runTest {
        var reconnects = 0
        val execution = execution(PendingStore(true)) { _, _ ->
            reconnects += 1
            successfulReport()
        }

        assertEquals(ReaderSyncWorkerOutcome.SUCCESS, execution.execute("wrong-account"))
        assertEquals(0, reconnects)
    }

    @Test
    fun `stale Worker account cannot resolve replacement account credentials`() = runTest {
        val replacementProfile = profile().copy(
            serverOrigin = "https://other-library.example",
            serverBaseUrl = "https://other-library.example/",
            apiBaseUrl = "https://other-library.example/api/v1/"
        )
        val replacementAccount = LocalReaderAccountKey.from(
            replacementProfile.serverOrigin,
            "profile-1"
        )
        val resolver = ReaderSyncAccountResolver(
            FixedProfileStore(replacementProfile),
            FixedAccountStore(
                PersistedAccountContext(
                    replacementProfile.authenticatedConnectionIdentity,
                    "profile-1",
                    replacementProfile.serverOrigin
                )
            )
        )

        assertNull(resolver.resolve(account()))
        assertEquals(replacementProfile, resolver.resolve(replacementAccount))
    }

    private fun execution(
        store: PendingStore,
        resolver: ReaderSyncAccountResolution = ReaderSyncAccountResolution { profile() },
        reconnect: suspend (ConnectionProfile, LocalReaderAccountKey) -> ReaderReconnectReport
    ) = ReaderSyncWorkerExecution(
        resolver,
        store,
        ReaderReconnectOperation(reconnect)
    )

    private class PendingStore(var pending: Boolean) : ReaderOutboxStore {
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

    private class FixedProfileStore(private val profile: ConnectionProfile) :
        ConnectionProfileStore {
        override suspend fun read() = profile

        override suspend fun write(profile: ConnectionProfile) = Unit

        override suspend fun clear() = Unit
    }

    private class FixedAccountStore(private val account: PersistedAccountContext) :
        PersistedAccountContextStore {
        override suspend fun read() = account

        override suspend fun write(context: PersistedAccountContext) = Unit

        override suspend fun clear() = Unit
    }

    private companion object {
        fun successfulReport() = ReaderReconnectReport(true, false)

        fun account() = LocalReaderAccountKey.from("https://library.example", "profile-1")

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
