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
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
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
@Suppress("LargeClass") // One fixture protects ordering across connection transitions.
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
    fun `matching persisted account is published before protected credential read completes`() =
        runTest {
            val restoredProfile = profile()
            val persistedAccount =
                PersistedAccountContext(
                    restoredProfile.authenticatedConnectionIdentity,
                    "profile-1"
                )
            val credentialGate = CompletableDeferred<Unit>()
            val credentialStore = storedCredential().apply { readGate = credentialGate }
            val coordinator =
                coordinator(
                    FakeClient(),
                    FakeProfileStore().apply { stored = restoredProfile },
                    credentialStore,
                    FakePersistedAccountContextStore().apply { stored = persistedAccount }
                )

            coordinator.restore()
            runCurrent()

            assertEquals(
                LocalAccountContext(restoredProfile, persistedAccount),
                coordinator.localAccountContext.value
            )
            assertTrue(coordinator.state.value is ConnectionUiState.Restoring)

            credentialGate.complete(Unit)
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
    fun `different server with same profile ID purges exact old account before replacement`() =
        runTest {
            val events = mutableListOf<String>()
            val oldProfile = profile()
            val replacement = oldProfile.copy(
                serverOrigin = "https://other-library.example",
                serverBaseUrl = "https://other-library.example/",
                apiBaseUrl = "https://other-library.example/api/v1/",
                clientSessionId = "other-session"
            )
            val accountStore = FakePersistedAccountContextStore(events).apply {
                stored = PersistedAccountContext(
                    oldProfile.authenticatedConnectionIdentity,
                    "profile-1",
                    oldProfile.serverOrigin
                )
            }
            val cleaner = FakeAccountLocalDataCleaner(events)
            val coordinator = coordinator(
                FakeClient(events = events),
                FakeProfileStore(events).apply { stored = replacement },
                storedCredential(),
                accountStore,
                cleaner
            )

            coordinator.restore()
            advanceUntilIdle()

            assertEquals(
                listOf(AccountLocalScope.from(oldProfile.serverOrigin, "profile-1")),
                cleaner.purged
            )
            assertTrue(events.indexOf("purge") < events.lastIndexOf("account"))
            assertEquals(replacement.serverOrigin, accountStore.stored?.accountServerOrigin)
            assertEquals(
                AccountLocalScope.from(replacement.serverOrigin, "profile-1"),
                coordinator.localAccountContext.value?.persistedAccount?.localDataScope()
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
    fun `explicit local forget clears account context and account-scoped data`() = runTest {
        val accountContextStore = FakePersistedAccountContextStore().apply {
            stored =
                PersistedAccountContext(
                    profile().authenticatedConnectionIdentity,
                    "profile-1"
                )
        }
        val cleaner = FakeAccountLocalDataCleaner()
        val revocation = FakeClientSessionRevocationClient()
        val coordinator =
            coordinator(
                FakeClient(),
                FakeProfileStore().apply { stored = profile() },
                FakeCredentialStore().apply {
                    stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
                },
                accountContextStore,
                cleaner = cleaner,
                revocationClient = revocation
            )

        coordinator.forgetLocalConnection()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
        assertTrue(accountContextStore.cleared)
        assertEquals(null, accountContextStore.stored)
        assertEquals(
            listOf(AccountLocalScope.from(profile().serverOrigin, "profile-1")),
            cleaner.purged
        )
        assertTrue(revocation.sessionIds.isEmpty())
    }

    @Test
    fun `logout revokes exact session before destructive local reset`() = runTest {
        val events = mutableListOf<String>()
        val revocation = FakeClientSessionRevocationClient(events)
        val profileStore = FakeProfileStore(events).apply { stored = profile() }
        val credentialStore = storedCredential()
        val accountStore = FakePersistedAccountContextStore(events).apply {
            stored = PersistedAccountContext(profile().authenticatedConnectionIdentity, "profile-1")
        }
        val cleaner = FakeAccountLocalDataCleaner(events)
        val coordinator =
            coordinator(
                FakeClient(events),
                profileStore,
                credentialStore,
                accountStore,
                cleaner,
                revocationClient = revocation
            )
        coordinator.restore()
        advanceUntilIdle()

        coordinator.logout()
        advanceUntilIdle()

        assertEquals(listOf("session-1"), revocation.sessionIds)
        assertTrue(events.indexOf("revoke") < events.indexOf("purge"))
        assertTrue(profileStore.cleared)
        assertTrue(credentialStore.cleared)
        assertTrue(accountStore.cleared)
        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
    }

    @Test
    fun `unreachable logout still performs destructive local reset`() = runTest {
        val revocation =
            FakeClientSessionRevocationClient(
                failure = SplClientException.ServerUnreachable()
            )
        val profileStore = FakeProfileStore().apply { stored = profile() }
        val credentialStore = storedCredential()
        val accountStore = FakePersistedAccountContextStore().apply {
            stored = PersistedAccountContext(profile().authenticatedConnectionIdentity, "profile-1")
        }
        val cleaner = FakeAccountLocalDataCleaner()
        val coordinator =
            coordinator(
                FakeClient(),
                profileStore,
                credentialStore,
                accountStore,
                cleaner,
                revocationClient = revocation
            )
        coordinator.restore()
        advanceUntilIdle()

        coordinator.logout()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
        assertTrue(profileStore.cleared)
        assertTrue(credentialStore.cleared)
        assertTrue(accountStore.cleared)
        assertEquals(1, cleaner.purged.size)
        assertEquals(ConnectionLifecycleActionState.Idle, coordinator.lifecycleActionState.value)
    }

    @Test
    fun `logout not-found converges to the same destructive local result`() = runTest {
        val revocation =
            FakeClientSessionRevocationClient(
                failure = SplClientException.ClientSessionNotFound()
            )
        val profileStore = FakeProfileStore().apply { stored = profile() }
        val credentialStore = storedCredential()
        val accountStore = FakePersistedAccountContextStore().apply {
            stored = PersistedAccountContext(profile().authenticatedConnectionIdentity, "profile-1")
        }
        val cleaner = FakeAccountLocalDataCleaner()
        val coordinator =
            coordinator(
                FakeClient(),
                profileStore,
                credentialStore,
                accountStore,
                cleaner,
                revocationClient = revocation
            )
        coordinator.restore()
        advanceUntilIdle()

        coordinator.logout()
        advanceUntilIdle()

        assertTrue(profileStore.cleared)
        assertTrue(credentialStore.cleared)
        assertTrue(accountStore.cleared)
        assertEquals(1, cleaner.purged.size)
        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
        assertEquals(ConnectionLifecycleActionState.Idle, coordinator.lifecycleActionState.value)
    }

    @Test
    fun `other logout rejection does not block destructive local reset`() = runTest {
        val revocation = FakeClientSessionRevocationClient(
            failure = SplClientException.ClientSessionRevocationFailed()
        )
        val profileStore = FakeProfileStore().apply { stored = profile() }
        val credentialStore = storedCredential()
        val accountStore = FakePersistedAccountContextStore().apply {
            stored = PersistedAccountContext(profile().authenticatedConnectionIdentity, "profile-1")
        }
        val cleaner = FakeAccountLocalDataCleaner()
        val coordinator = coordinator(
            FakeClient(),
            profileStore,
            credentialStore,
            accountStore,
            cleaner,
            revocationClient = revocation
        )
        coordinator.restore()
        advanceUntilIdle()

        coordinator.logout()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
        assertTrue(profileStore.cleared)
        assertTrue(credentialStore.cleared)
        assertTrue(accountStore.cleared)
        assertEquals(1, cleaner.purged.size)
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
    fun `invalid restored credential retains same-account local state`() = runTest {
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

        assertTrue(coordinator.state.value is ConnectionUiState.AuthenticationRequired)
        assertFalse(profileStore.cleared)
        assertFalse(credentialStore.cleared)
        assertFalse(accountContextStore.cleared)
        assertEquals(
            LocalAccountContext(profile, accountContextStore.stored!!),
            coordinator.localAccountContext.value
        )
    }

    @Test
    fun `same-account relink replaces connection identity without purging cache`() = runTest {
        val oldProfile = profile(clientSessionId = "old-session")
        val profileStore = FakeProfileStore().apply { stored = oldProfile }
        val credentialStore = storedCredential()
        val accountStore = FakePersistedAccountContextStore().apply {
            stored =
                PersistedAccountContext(oldProfile.authenticatedConnectionIdentity, "profile-1")
        }
        val cleaner = FakeAccountLocalDataCleaner()
        val coordinator =
            coordinator(
                FakeClient(
                    pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
                    authFailures = ArrayDeque(listOf(SplClientException.AuthenticationRejected())),
                    issuedSessionId = "new-session"
                ),
                profileStore,
                credentialStore,
                accountStore,
                cleaner
            )

        coordinator.restore()
        advanceUntilIdle()
        coordinator.relinkLocalAccount()
        advanceUntilIdle()
        coordinator.beginPairing()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        assertTrue(cleaner.purged.isEmpty())
        assertEquals("new-session", profileStore.stored?.clientSessionId)
        assertEquals("new-session", accountStore.stored?.connectionIdentity?.clientSessionId)
        assertEquals(
            "profile-1",
            coordinator.localAccountContext.value?.persistedAccount?.profileId
        )
    }

    @Test
    fun `different-account relink purges old account before adopting replacement`() = runTest {
        val events = mutableListOf<String>()
        val oldProfile = profile(clientSessionId = "old-session")
        val accountStore = FakePersistedAccountContextStore(events).apply {
            stored =
                PersistedAccountContext(oldProfile.authenticatedConnectionIdentity, "profile-1")
        }
        val cleaner = FakeAccountLocalDataCleaner(events)
        val coordinator =
            coordinator(
                FakeClient(
                    events = events,
                    pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
                    authFailures = ArrayDeque(listOf(SplClientException.AuthenticationRejected())),
                    authenticatedProfileId = "profile-2",
                    issuedSessionId = "new-session"
                ),
                FakeProfileStore(events).apply { stored = oldProfile },
                storedCredential(),
                accountStore,
                cleaner
            )

        coordinator.restore()
        advanceUntilIdle()
        coordinator.relinkLocalAccount()
        advanceUntilIdle()
        coordinator.beginPairing()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        assertEquals(
            listOf(AccountLocalScope.from(oldProfile.serverOrigin, "profile-1")),
            cleaner.purged
        )
        assertTrue(events.indexOf("purge") < events.lastIndexOf("account"))
        assertEquals("profile-2", accountStore.stored?.profileId)
        assertEquals(
            "profile-2",
            coordinator.localAccountContext.value?.persistedAccount?.profileId
        )
    }

    @Test
    fun `failed relink retains prior account and never purges before verification`() = runTest {
        val oldProfile = profile()
        val oldAccount =
            PersistedAccountContext(oldProfile.authenticatedConnectionIdentity, "profile-1")
        val cleaner = FakeAccountLocalDataCleaner()
        val coordinator =
            coordinator(
                FakeClient(
                    pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
                    authFailures =
                        ArrayDeque(
                            listOf(
                                SplClientException.AuthenticationRejected(),
                                SplClientException.AuthenticationRejected()
                            )
                        )
                ),
                FakeProfileStore().apply { stored = oldProfile },
                storedCredential(),
                FakePersistedAccountContextStore().apply { stored = oldAccount },
                cleaner
            )

        coordinator.restore()
        advanceUntilIdle()
        coordinator.relinkLocalAccount()
        advanceUntilIdle()
        coordinator.beginPairing()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.AuthenticationRequired)
        assertEquals(
            oldAccount.profileId,
            coordinator.localAccountContext.value?.persistedAccount?.profileId
        )
        assertTrue(cleaner.purged.isEmpty())
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

            assertTrue(coordinator.state.value is ConnectionUiState.AuthenticationRequired)
            assertFalse(profileStore.cleared)
            assertFalse(credentialStore.cleared)
            assertFalse(accountContextStore.cleared)
            assertEquals(resolvedLocalAccount, coordinator.localAccountContext.value)
        }

    @Test
    fun `linked unreachable request retains local account for cache-first retry`() = runTest {
        val profileStore = FakeProfileStore().apply { stored = profile() }
        val credentialStore = FakeCredentialStore().apply {
            stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
        }
        val accountContextStore = FakePersistedAccountContextStore()
        val coordinator =
            coordinator(FakeClient(), profileStore, credentialStore, accountContextStore)
        coordinator.restore()
        advanceUntilIdle()
        val resolvedLocalAccount = coordinator.localAccountContext.value

        coordinator.authenticatedRequestUnreachable()

        assertTrue(coordinator.state.value is ConnectionUiState.RestoreProblem)
        assertEquals(resolvedLocalAccount, coordinator.localAccountContext.value)
        assertFalse(profileStore.cleared)
        assertFalse(credentialStore.cleared)
        assertFalse(accountContextStore.cleared)
    }

    @Test
    fun `cancelled consume cannot persist or publish its late credential`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeClient(
            pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
            consumeGate = gate
        )
        val credentials = FakeCredentialStore()
        val profiles = FakeProfileStore()
        val coordinator = coordinator(client, profiles, credentials)
        startPairing(coordinator)
        runCurrent()
        coordinator.abandonPairing()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(credentials.stored)
        assertNull(profiles.stored)
        assertNull(coordinator.localAccountContext.value)
        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
    }

    @Test
    fun `cancelled verification cannot publish a stale paired account`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeClient(
            pollStatuses = ArrayDeque(listOf(PairingStatus.APPROVED)),
            verificationGate = gate,
            ignoreVerificationCancellation = true
        )
        val accounts = FakePersistedAccountContextStore()
        val coordinator = coordinator(client, FakeProfileStore(), FakeCredentialStore(), accounts)
        startPairing(coordinator)
        runCurrent()
        assertTrue(coordinator.state.value is ConnectionUiState.CompletingPairing)
        coordinator.abandonPairing()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(accounts.stored)
        assertNull(coordinator.localAccountContext.value)
        assertTrue(coordinator.state.value is ConnectionUiState.ServerEntry)
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
        cleaner: FakeAccountLocalDataCleaner = FakeAccountLocalDataCleaner(),
        revocationClient: FakeClientSessionRevocationClient =
            FakeClientSessionRevocationClient()
    ) = ConnectionCoordinator(
        client = client,
        clientSessionRevocationClient = revocationClient,
        profileStore = profileStore,
        credentialStore = credentialStore,
        accountContextStore = accountContextStore,
        accountLocalDataLifecycle = cleaner,
        pollDelay = PairingPollDelay {},
        defaultClientName = "Second Pass Reader · Android",
        scope = this
    )

    private class FakeClient(
        private val events: MutableList<String> = mutableListOf(),
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

        override suspend fun discoverServer(userInput: String): DiscoveredServer = server()

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
            apiBaseUrl: String,
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

    private class FakeAccountLocalDataCleaner(
        private val events: MutableList<String> = mutableListOf()
    ) : AccountLocalDataLifecycle {
        val purged = mutableListOf<AccountLocalScope>()

        override suspend fun purge(account: AccountLocalScope) {
            events += "purge"
            purged += account
        }
    }

    private class FakeClientSessionRevocationClient(
        private val events: MutableList<String> = mutableListOf(),
        private val failure: Exception? = null
    ) : ClientSessionRevocationClient {
        val sessionIds = mutableListOf<String>()

        override suspend fun revokeCurrentClientSession(
            apiBaseUrl: String,
            credential: BearerCredential,
            clientSessionId: String
        ) {
            events += "revoke"
            sessionIds += clientSessionId
            failure?.let { throw it }
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

        fun profile(clientSessionId: String = "session-1") = ConnectionProfile(
            "https://library.example",
            "https://library.example/",
            "https://library.example/api/v1/",
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
            AuthenticatedServerInfo("Library", "", "", false, null, "", null, "1.0", "")
        )
    }
}
