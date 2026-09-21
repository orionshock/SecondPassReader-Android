package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.AuthenticatedServerInfo
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.ClientSession
import com.secondpasslibrary.client.ClientSessionRevocationClient
import com.secondpasslibrary.client.CurrentUser
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.storage.AccountLocalDataLifecycle
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.connection.pairing.PairingPollDelay
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import com.secondpasslibrary.reader.connection.storage.WorkOfflineStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class ConnectionCoordinatorTestSupport {
    protected class FakeWorkOfflineStore : WorkOfflineStore {
        private val enabled = mutableSetOf<String>()

        override suspend fun read(accountKey: String): Boolean = accountKey in enabled

        override suspend fun write(accountKey: String, enabled: Boolean) {
            if (enabled) this.enabled.add(accountKey) else this.enabled.remove(accountKey)
        }
    }
    protected suspend fun TestScope.startPairing(coordinator: ConnectionCoordinator) {
        coordinator.restore()
        advanceUntilIdle()
        coordinator.updateServerUrl("https://library.example")
        coordinator.verifyServer()
        advanceUntilIdle()
        coordinator.beginPairing()
    }

    protected fun kotlinx.coroutines.test.TestScope.coordinator(
        client: FakeClient,
        profileStore: FakeProfileStore,
        credentialStore: FakeCredentialStore,
        accountContextStore: FakePersistedAccountContextStore =
            FakePersistedAccountContextStore(),
        cleaner: FakeAccountLocalDataCleaner = FakeAccountLocalDataCleaner(),
        workOfflineStore: WorkOfflineStore = FakeWorkOfflineStore(),
        revocationClient: FakeClientSessionRevocationClient =
            FakeClientSessionRevocationClient(),
        target: AuthenticatedConnectionTarget = client.asAuthenticatedConnectionTarget()
    ) = ConnectionCoordinator(
        client = client,
        clientSessionRevocationClient = revocationClient,
        persistence = ConnectionPersistence(profileStore, credentialStore, accountContextStore),
        accountLocalDataLifecycle = cleaner,
        workOfflineStore = workOfflineStore,
        pollDelay = PairingPollDelay {},
        defaultClientName = "Second Pass Reader · Android",
        scope = this,
        connectionTarget = target
    )

    protected class FakeClient(
        private val events: MutableList<String> = mutableListOf(),
        private val discoveryFailures: ArrayDeque<Exception> = ArrayDeque(),
        private val pollStatuses: ArrayDeque<PairingStatus> = ArrayDeque(),
        private val authFailure: Exception? = null,
        private val authFailures: ArrayDeque<Exception> = ArrayDeque(),
        private val consumeGate: CompletableDeferred<Unit>? = null,
        private val ignoreVerificationCancellation: Boolean = false,
        private val verificationGate: CompletableDeferred<Unit>? = null,
        private val authenticatedProfileId: String = "profile-1",
        private val issuedSessionId: String = "session-1"
    ) : SecondPassClient {
        var consumeCalls = 0

        override suspend fun discoverServer(userInput: String): DiscoveredServer {
            discoveryFailures.removeFirstOrNull()?.let { throw it }
            return server()
        }

        override suspend fun beginPairing(
            server: DiscoveredServer,
            clientName: String,
            clientType: String
        ): PairingRequest = request()

        override suspend fun checkPairing(request: PairingRequest): PairingStatus =
            pollStatuses.removeFirstOrNull() ?: PairingStatus.PENDING

        override suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption {
            consumeCalls += 1
            events += "consume"
            consumeGate?.let { withContext(NonCancellable) { it.await() } }
            return PairingConsumption.CredentialIssued(
                BearerCredential.restore("spl_secret"),
                ClientSession(issuedSessionId, "Tablet", "second-pass-android-client")
            )
        }

        override suspend fun loadAuthenticatedContext(
            libraryBaseUrl: String,
            credential: BearerCredential
        ): AuthenticatedContext {
            events += "verify"
            if (ignoreVerificationCancellation) {
                withContext(NonCancellable) { verificationGate?.await() }
            } else {
                verificationGate?.await()
            }
            authFailures.removeFirstOrNull()?.let { throw it }
            authFailure?.let { throw it }
            return authenticatedContext(authenticatedProfileId)
        }
    }

    protected class FakeProfileStore(private val events: MutableList<String> = mutableListOf()) :
        ConnectionProfileStore {
        var stored: ConnectionProfile? = null
        var failWrites = false
        var cleared = false

        override suspend fun read(): ConnectionProfile? = stored

        override suspend fun write(profile: ConnectionProfile) {
            if (failWrites) throw ConnectionProfileStorageException("disk full")
            events += "profile"
            stored = profile
        }

        override suspend fun clear() {
            cleared = true
            stored = null
        }
    }

    protected class FakeCredentialStore(private val events: MutableList<String> = mutableListOf()) :
        BearerCredentialStore {
        var stored: StoredCredential? = null
        var cleared = false
        var readGate: CompletableDeferred<Unit>? = null

        override suspend fun read(): StoredCredential? {
            readGate?.await()
            return stored
        }

        override suspend fun write(
            credential: BearerCredential,
            recoveryProfile: ConnectionProfile
        ) {
            events += "credential"
            stored = StoredCredential(credential, recoveryProfile)
        }

        override suspend fun markProfileCommitted() {
            stored = stored?.copy(recoveryProfile = null)
        }

        override suspend fun clear() {
            cleared = true
            stored = null
        }
    }

    protected fun storedCredential() = FakeCredentialStore().apply {
        stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
    }

    protected class FakePersistedAccountContextStore(
        private val events: MutableList<String> = mutableListOf()
    ) : PersistedAccountContextStore {
        var stored: PersistedAccountContext? = null
        var writeCalls = 0
        var cleared = false
        var failWrites = false

        override suspend fun read(): PersistedAccountContext? = stored

        override suspend fun write(context: PersistedAccountContext) {
            events += "account"
            writeCalls += 1
            if (failWrites) throw IllegalStateException("disk full")
            stored = context
        }

        override suspend fun clear() {
            cleared = true
            stored = null
        }
    }

    protected class FakeAccountLocalDataCleaner(
        private val events: MutableList<String> = mutableListOf()
    ) : AccountLocalDataLifecycle {
        val purged = mutableListOf<AccountLocalScope>()

        override suspend fun purge(account: AccountLocalScope) {
            events += "purge"
            purged += account
        }
    }

    protected class FakeClientSessionRevocationClient(
        private val events: MutableList<String> = mutableListOf(),
        private val failure: Exception? = null
    ) : ClientSessionRevocationClient {
        val sessionIds = mutableListOf<String>()

        override suspend fun revokeCurrentClientSession(
            libraryBaseUrl: String,
            credential: BearerCredential,
            clientSessionId: String
        ) {
            events += "revoke"
            sessionIds += clientSessionId
            failure?.let { throw it }
        }
    }

    protected companion object {
        fun server() = DiscoveredServer(
            ServerOrigin.fromUserInput("https://library.example"),
            "a6722b5a-7982-4778-8c74-39be4241a654",
            "https://library.example",
            "Library",
            "Books",
            "1.0",
            "2026-08-16",
            "0.1",
            "https://library.example/api/v1/client-api/login-requests/",
            "Bearer"
        )

        fun request() = PairingRequest(
            "ABCD-EFGH",
            "https://library.example/approve",
            "https://library.example/poll",
            "https://library.example/consume",
            "2026-08-16T22:00:00Z",
            3
        )

        fun profile(clientSessionId: String = "session-1") = ConnectionProfile(
            "a6722b5a-7982-4778-8c74-39be4241a654",
            "https://library.example",
            "https://library.example",
            "Library",
            "Books",
            "1.0",
            "2026-08-16",
            clientSessionId,
            "Tablet",
            "second-pass-android-client"
        )

        fun authenticatedContext(profileId: String = "profile-1") = AuthenticatedContext(
            CurrentUser("reader", "", "", "", profileId, "reader", emptyList(), null, null, null),
            AuthenticatedServerInfo(
                "a6722b5a-7982-4778-8c74-39be4241a654",
                listOf("https://library.example"),
                "Library", "", "", false, null, "", null, "1.0", ""
            )
        )
    }
}
