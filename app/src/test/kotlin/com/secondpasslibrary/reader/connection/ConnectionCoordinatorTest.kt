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
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {
    @Test
    fun `matching persisted account is published before verification completes`() = runTest {
        val restoredProfile = profile()
        val persistedAccount =
            PersistedAccountContext(restoredProfile.authenticatedConnectionIdentity, "profile-1")
        val verificationGate = CompletableDeferred<Unit>()
        val coordinator =
            coordinator(
                FakeClient(verificationGate = verificationGate),
                FakeProfileStore().apply { stored = restoredProfile },
                storedCredential(),
                FakePersistedAccountContextStore().apply { stored = persistedAccount }
            )

        coordinator.restore()
        runCurrent()

        assertEquals(
            LocalAccountContext(restoredProfile, persistedAccount),
            coordinator.localAccountContext.value
        )
        assertTrue(coordinator.state.value is ConnectionUiState.Restoring)

        verificationGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
    }

    @Test
    fun `missing persisted account is not published and successful restore populates it`() =
        runTest {
            val restoredProfile = profile()
            val verificationGate = CompletableDeferred<Unit>()
            val accountStore = FakePersistedAccountContextStore()
            val coordinator =
                coordinator(
                    FakeClient(verificationGate = verificationGate),
                    FakeProfileStore().apply { stored = restoredProfile },
                    storedCredential(),
                    accountStore
                )

            coordinator.restore()
            runCurrent()

            assertNull(coordinator.localAccountContext.value)
            assertTrue(coordinator.state.value is ConnectionUiState.Restoring)

            verificationGate.complete(Unit)
            advanceUntilIdle()

            val repaired =
                PersistedAccountContext(
                    restoredProfile.authenticatedConnectionIdentity,
                    "profile-1"
                )
            assertEquals(repaired, accountStore.stored)
            assertEquals(
                LocalAccountContext(restoredProfile, repaired),
                coordinator.localAccountContext.value
            )
            assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        }

    @Test
    fun `mismatched persisted account is not published and verification repairs it`() = runTest {
        val restoredProfile = profile()
        val stale =
            PersistedAccountContext(
                AuthenticatedConnectionIdentity("https://old.example/api/v1/", "old-session"),
                "old-profile"
            )
        val verificationGate = CompletableDeferred<Unit>()
        val accountStore = FakePersistedAccountContextStore().apply { stored = stale }
        val coordinator =
            coordinator(
                FakeClient(verificationGate = verificationGate),
                FakeProfileStore().apply { stored = restoredProfile },
                storedCredential(),
                accountStore
            )

        coordinator.restore()
        runCurrent()

        assertNull(coordinator.localAccountContext.value)

        verificationGate.complete(Unit)
        advanceUntilIdle()

        val repaired =
            PersistedAccountContext(restoredProfile.authenticatedConnectionIdentity, "profile-1")
        assertEquals(repaired, accountStore.stored)
        assertEquals(
            LocalAccountContext(restoredProfile, repaired),
            coordinator.localAccountContext.value
        )
    }

    @Test
    fun `approved pairing stores credential then profile before verification`() = runTest {
        val events = mutableListOf<String>()
        val client =
            FakeClient(events = events, pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)))
        val profileStore = FakeProfileStore(events)
        val credentialStore = FakeCredentialStore(events)
        val accountContextStore = FakePersistedAccountContextStore(events)
        val coordinator =
            coordinator(client, profileStore, credentialStore, accountContextStore)

        startPairing(coordinator)
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        assertEquals(
            listOf("consume", "credential", "profile", "verify", "account"),
            events.take(5)
        )
        assertEquals(
            PersistedAccountContext(profile().authenticatedConnectionIdentity, "profile-1"),
            accountContextStore.stored
        )
        assertEquals(1, client.consumeCalls)
    }

    @Test
    fun `failed verification does not replace persisted account context`() = runTest {
        val original =
            PersistedAccountContext(
                AuthenticatedConnectionIdentity("https://old.example/api/v1/", "old-session"),
                "old-profile"
            )
        val accountContextStore = FakePersistedAccountContextStore().apply { stored = original }
        val coordinator =
            coordinator(
                FakeClient(authFailure = SplClientException.ServerUnreachable()),
                FakeProfileStore().apply { stored = profile() },
                FakeCredentialStore().apply {
                    stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
                },
                accountContextStore
            )

        coordinator.restore()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.RestoreProblem)
        assertEquals(0, accountContextStore.writeCalls)
        assertEquals(original, accountContextStore.stored)
    }

    @Test
    fun `successful restore populates missing persisted account context`() = runTest {
        val restoredProfile = profile()
        val accountContextStore = FakePersistedAccountContextStore()
        val coordinator =
            coordinator(
                FakeClient(),
                FakeProfileStore().apply { stored = restoredProfile },
                FakeCredentialStore().apply {
                    stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
                },
                accountContextStore
            )

        coordinator.restore()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        assertEquals(
            PersistedAccountContext(
                restoredProfile.authenticatedConnectionIdentity,
                "profile-1"
            ),
            accountContextStore.stored
        )
    }

    @Test
    fun `descriptor persistence failure does not invalidate verified connection`() = runTest {
        val accountContextStore = FakePersistedAccountContextStore().apply { failWrites = true }
        val coordinator =
            coordinator(
                FakeClient(),
                FakeProfileStore().apply { stored = profile() },
                FakeCredentialStore().apply {
                    stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
                },
                accountContextStore
            )

        coordinator.restore()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        assertEquals(1, accountContextStore.writeCalls)
        assertEquals(null, accountContextStore.stored)
    }

    @Test
    fun `explicit local forget clears persisted account context`() = runTest {
        val accountContextStore = FakePersistedAccountContextStore().apply {
            stored =
                PersistedAccountContext(
                    profile().authenticatedConnectionIdentity,
                    "profile-1"
                )
        }
        val coordinator =
            coordinator(
                FakeClient(),
                FakeProfileStore().apply { stored = profile() },
                FakeCredentialStore().apply {
                    stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
                },
                accountContextStore
            )

        coordinator.forgetLocalConnection()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
        assertTrue(accountContextStore.cleared)
        assertEquals(null, accountContextStore.stored)
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
    fun `transient polling failure retries automatically with bounded backoff`() = runTest {
        val client =
            FakeClient(
                pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
                pollFailures = ArrayDeque(listOf(SplClientException.ServerUnreachable()))
            )
        val coordinator =
            coordinator(client, FakeProfileStore(), FakeCredentialStore(), timedDelay = true)

        startPairing(coordinator)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        val retrying = coordinator.state.value as ConnectionUiState.WaitingForApproval
        assertTrue(retrying.statusText.contains("Retrying automatically"))
        assertEquals(1, client.pollCalls)

        advanceTimeBy(5_999)
        runCurrent()
        assertEquals(1, client.pollCalls)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(2, client.pollCalls)
        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
    }

    @Test
    fun `foregrounding pairing restarts one polling loop at the server interval`() = runTest {
        val client =
            FakeClient(
                pollStatuses = ArrayDeque(listOf(PairingStatus.PENDING, PairingStatus.APPROVED))
            )
        val coordinator =
            coordinator(client, FakeProfileStore(), FakeCredentialStore(), timedDelay = true)

        startPairing(coordinator)
        runCurrent()
        advanceTimeBy(1_000)
        coordinator.pairingForegrounded()
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, client.pollCalls)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, client.pollCalls)
        advanceTimeBy(3_000)
        advanceUntilIdle()

        assertEquals(2, client.pollCalls)
        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
    }

    @Test
    fun `invalid polling response remains terminal`() = runTest {
        val client =
            FakeClient(
                pollFailures =
                    ArrayDeque(listOf(SplClientException.ProtocolInvalid("pairing status")))
            )
        val coordinator = coordinator(client, FakeProfileStore(), FakeCredentialStore())

        startPairing(coordinator)
        advanceUntilIdle()

        assertEquals(1, client.pollCalls)
        assertTrue(coordinator.state.value is ConnectionUiState.TerminalPairingProblem)
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
        val accountContextStore = FakePersistedAccountContextStore().apply {
            stored = PersistedAccountContext(profile.authenticatedConnectionIdentity, "profile-1")
        }
        val coordinator =
            coordinator(
                FakeClient(authFailure = SplClientException.AuthenticationRejected()),
                profileStore,
                credentialStore,
                accountContextStore
            )

        coordinator.restore()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
        assertTrue(profileStore.cleared)
        assertTrue(credentialStore.cleared)
        assertTrue(accountContextStore.cleared)
        assertNull(coordinator.localAccountContext.value)
    }

    @Test
    fun `transient restore failure preserves credential and profile`() = runTest {
        val restoredProfile = profile()
        val profileStore = FakeProfileStore().apply { stored = restoredProfile }
        val credentialStore = FakeCredentialStore().apply {
            stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
        }
        val persistedAccount =
            PersistedAccountContext(restoredProfile.authenticatedConnectionIdentity, "profile-1")
        val coordinator =
            coordinator(
                FakeClient(authFailure = SplClientException.ServerUnreachable()),
                profileStore,
                credentialStore,
                FakePersistedAccountContextStore().apply { stored = persistedAccount }
            )

        coordinator.restore()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.RestoreProblem)
        assertEquals(
            LocalAccountContext(restoredProfile, persistedAccount),
            coordinator.localAccountContext.value
        )
        assertFalse(profileStore.cleared)
        assertFalse(credentialStore.cleared)
    }

    @Test
    fun `first pairing publishes no prior local account before verification`() = runTest {
        val verificationGate = CompletableDeferred<Unit>()
        val client =
            FakeClient(
                pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
                verificationGate = verificationGate
            )
        val coordinator = coordinator(client, FakeProfileStore(), FakeCredentialStore())

        startPairing(coordinator)
        runCurrent()

        assertNull(coordinator.localAccountContext.value)
        assertTrue(coordinator.state.value is ConnectionUiState.CompletingPairing)

        verificationGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
    }

    @Test
    fun `linked request rejection returns to connection policy without clearing credential`() =
        runTest {
            val profileStore = FakeProfileStore().apply { stored = profile() }
            val credentialStore = FakeCredentialStore().apply {
                stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
            }
            val accountContextStore = FakePersistedAccountContextStore()
            val coordinator =
                coordinator(FakeClient(), profileStore, credentialStore, accountContextStore)
            coordinator.restore()
            advanceUntilIdle()
            assertTrue(coordinator.state.value is ConnectionUiState.Linked)
            val resolvedLocalAccount = coordinator.localAccountContext.value

            coordinator.authenticatedRequestRejected()

            val problem = coordinator.state.value as ConnectionUiState.StoredCredentialProblem
            assertFalse(problem.retryable)
            assertFalse(profileStore.cleared)
            assertFalse(credentialStore.cleared)
            assertFalse(accountContextStore.cleared)
            assertEquals(resolvedLocalAccount, coordinator.localAccountContext.value)
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
        accountContextStore: FakePersistedAccountContextStore =
            FakePersistedAccountContextStore(),
        timedDelay: Boolean = false
    ) = ConnectionCoordinator(
        client = client,
        profileStore = profileStore,
        credentialStore = credentialStore,
        accountContextStore = accountContextStore,
        pollDelay = PairingPollDelay { seconds -> if (timedDelay) delay(seconds * 1_000) },
        defaultClientName = "Second Pass Reader · Android",
        scope = this
    )

    private class FakeClient(
        private val events: MutableList<String> = mutableListOf(),
        private val pollStatuses: ArrayDeque<PairingStatus> = ArrayDeque(),
        private val pollFailures: ArrayDeque<SplClientException> = ArrayDeque(),
        private val authFailure: Exception? = null,
        private val consumeFailure: Exception? = null,
        private val verificationGate: CompletableDeferred<Unit>? = null
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
            pollFailures.removeFirstOrNull()?.let { throw it }
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
            verificationGate?.await()
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

    private fun storedCredential() = FakeCredentialStore().apply {
        stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
    }

    private class FakePersistedAccountContextStore(
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
