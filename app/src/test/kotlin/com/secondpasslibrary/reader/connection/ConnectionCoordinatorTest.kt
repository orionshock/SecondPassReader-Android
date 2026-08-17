package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.AuthenticatedServerInfo
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.ClientSession
import com.secondpasslibrary.client.CurrentUser
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {
    @Test
    fun `approved pairing stores credential then profile before verification`() = runTest {
        val events = mutableListOf<String>()
        val client =
            FakeClient(events = events, pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)))
        val profileStore = FakeProfileStore(events)
        val credentialStore = FakeCredentialStore(events)
        val coordinator = coordinator(client, profileStore, credentialStore)

        startPairing(coordinator)
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        assertEquals(listOf("consume", "credential", "profile", "verify"), events.take(4))
        assertEquals(1, client.consumeCalls)
    }

    @Test
    fun `abandoning pairing cancels its polling loop`() = runTest {
        val client =
            FakeClient(
                pollStatuses = ArrayDeque(listOf(PairingStatus.PENDING, PairingStatus.PENDING))
            )
        val coordinator =
            coordinator(client, FakeProfileStore(), FakeCredentialStore(), timedDelay = true)

        startPairing(coordinator)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, client.pollCalls)

        coordinator.abandonPairing()
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(1, client.pollCalls)
        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
    }

    @Test
    fun `profile failure after consumption retries without consuming again`() = runTest {
        val client = FakeClient(pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)))
        val profileStore = FakeProfileStore().apply { failWrites = true }
        val credentialStore = FakeCredentialStore()
        val coordinator = coordinator(client, profileStore, credentialStore)

        startPairing(coordinator)
        advanceUntilIdle()
        assertTrue(coordinator.state.value is ConnectionUiState.PersistenceRecovery)
        assertEquals(1, client.consumeCalls)
        assertTrue(credentialStore.stored != null)

        profileStore.failWrites = false
        coordinator.retryProfilePersistence()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        assertEquals(1, client.consumeCalls)
    }

    @Test
    fun `invalid restored credential is cleared`() = runTest {
        val profile = profile()
        val profileStore = FakeProfileStore().apply { stored = profile }
        val credentialStore = FakeCredentialStore().apply {
            stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
        }
        val coordinator =
            coordinator(
                FakeClient(authFailure = SplClientException.AuthenticationRejected()),
                profileStore,
                credentialStore
            )

        coordinator.restore()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
        assertTrue(profileStore.cleared)
        assertTrue(credentialStore.cleared)
    }

    @Test
    fun `transient restore failure preserves credential and profile`() = runTest {
        val profileStore = FakeProfileStore().apply { stored = profile() }
        val credentialStore = FakeCredentialStore().apply {
            stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
        }
        val coordinator =
            coordinator(
                FakeClient(authFailure = SplClientException.ServerUnreachable()),
                profileStore,
                credentialStore
            )

        coordinator.restore()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.RestoreProblem)
        assertFalse(profileStore.cleared)
        assertFalse(credentialStore.cleared)
    }

    @Test
    fun `ambiguous consume failure is never retried automatically`() = runTest {
        val client =
            FakeClient(
                pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
                consumeFailure = SplClientException.AmbiguousConsumeFailure()
            )
        val coordinator = coordinator(client, FakeProfileStore(), FakeCredentialStore())

        startPairing(coordinator)
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.TerminalPairingProblem)
        assertEquals(1, client.consumeCalls)
    }

    private suspend fun TestScope.startPairing(coordinator: ConnectionCoordinator) {
        coordinator.restore()
        advanceUntilIdle()
        coordinator.updateServerUrl("https://library.example")
        coordinator.verifyServer()
        advanceUntilIdle()
        coordinator.beginPairing()
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        client: FakeClient,
        profileStore: FakeProfileStore,
        credentialStore: FakeCredentialStore,
        timedDelay: Boolean = false
    ) = ConnectionCoordinator(
        client = client,
        profileStore = profileStore,
        credentialStore = credentialStore,
        pollDelay = PairingPollDelay { seconds -> if (timedDelay) delay(seconds * 1_000) },
        defaultClientName = "Second Pass Reader · Android",
        scope = this
    )

    private class FakeClient(
        private val events: MutableList<String> = mutableListOf(),
        private val pollStatuses: ArrayDeque<PairingStatus> = ArrayDeque(),
        private val authFailure: Exception? = null,
        private val consumeFailure: Exception? = null
    ) : SecondPassClient {
        var pollCalls = 0
        var consumeCalls = 0

        override suspend fun discoverServer(userInput: String): DiscoveredServer = server()

        override suspend fun beginPairing(
            server: DiscoveredServer,
            clientName: String,
            clientType: String
        ): PairingRequest = request()

        override suspend fun checkPairing(request: PairingRequest): PairingStatus {
            pollCalls += 1
            return pollStatuses.removeFirstOrNull() ?: PairingStatus.PENDING
        }

        override suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption {
            consumeCalls += 1
            events += "consume"
            consumeFailure?.let { throw it }
            return PairingConsumption.CredentialIssued(
                BearerCredential.restore("spl_secret"),
                ClientSession("session-1", "Tablet", "second-pass-android-client")
            )
        }

        override suspend fun loadAuthenticatedContext(
            apiBaseUrl: String,
            credential: BearerCredential
        ): AuthenticatedContext {
            events += "verify"
            authFailure?.let { throw it }
            return authenticatedContext()
        }
    }

    private class FakeProfileStore(private val events: MutableList<String> = mutableListOf()) :
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

    private class FakeCredentialStore(private val events: MutableList<String> = mutableListOf()) :
        BearerCredentialStore {
        var stored: StoredCredential? = null
        var cleared = false

        override suspend fun read(): StoredCredential? = stored

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

    private companion object {
        fun server() = DiscoveredServer(
            ServerOrigin.fromUserInput("https://library.example"),
            "https://library.example/",
            "https://library.example/api/v1/",
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

        fun profile() = ConnectionProfile(
            "https://library.example",
            "https://library.example/",
            "https://library.example/api/v1/",
            "Library",
            "Books",
            "1.0",
            "2026-08-16",
            "session-1",
            "Tablet",
            "second-pass-android-client"
        )

        fun authenticatedContext() = AuthenticatedContext(
            CurrentUser("reader", "", "", "", "profile-1", "reader", emptyList(), null, null, null),
            AuthenticatedServerInfo("Library", "", "", false, null, "", null, "1.0", "")
        )
    }
}
