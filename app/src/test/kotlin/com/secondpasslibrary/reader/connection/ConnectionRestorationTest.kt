package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionRestorationTest : ConnectionCoordinatorTestSupport() {
    @Test
    fun `pre-release local reset discards old state before verification`() = runTest {
        val events = mutableListOf<String>()
        val accountStore = FakePersistedAccountContextStore(events).apply {
            identityResetRequired = true
        }
        val cleaner = FakeAccountLocalDataCleaner(events)
        val connection = coordinator(
            FakeClient(events = events),
            FakeProfileStore().apply { stored = profile() },
            storedCredential(),
            accountStore,
            cleaner
        )

        connection.restore()
        advanceUntilIdle()

        assertTrue(connection.state.value is ConnectionUiState.Linked)
        assertEquals(1, cleaner.legacyDiscards)
        assertEquals(1, accountStore.identityResetMarks)
        assertTrue(events.indexOf("discard-legacy") < events.indexOf("scope-reset"))
        assertTrue(events.indexOf("scope-reset") < events.indexOf("verify"))
        assertEquals(profile().serverId, accountStore.stored?.connectionIdentity?.serverId)
    }

    @Test
    fun `user offline intent survives restart and blocks automatic recovery`() = runTest {
        val events = mutableListOf<String>()
        val profiles = FakeProfileStore().apply { stored = profile() }
        val credentials = storedCredential()
        val accounts = FakePersistedAccountContextStore()
        val intent = FakeWorkOfflineStore()
        val cleaner = FakeAccountLocalDataCleaner()
        val first = coordinator(
            FakeClient(events = events),
            profiles,
            credentials,
            accounts,
            cleaner = cleaner,
            workOfflineStore = intent
        )
        first.restore()
        advanceUntilIdle()
        first.workOffline()
        advanceUntilIdle()
        assertTrue(first.state.value is ConnectionUiState.WorkingOffline)
        val verifications = events.count { it == "verify" }

        first.authenticatedRequestUnreachable()
        first.retryIfUnreachable()
        advanceUntilIdle()
        assertTrue(first.state.value is ConnectionUiState.WorkingOffline)
        assertEquals(verifications, events.count { it == "verify" })

        val restarted = coordinator(
            FakeClient(events = events),
            profiles,
            credentials,
            accounts,
            workOfflineStore = intent
        )
        restarted.restore()
        advanceUntilIdle()
        assertTrue(restarted.state.value is ConnectionUiState.WorkingOffline)
        assertEquals(verifications, events.count { it == "verify" })
        assertFalse(profiles.cleared)
        assertFalse(credentials.cleared)
        assertTrue(cleaner.purged.isEmpty())

        val reconnect = async { restarted.checkConnectionNow() }
        advanceUntilIdle()
        assertTrue(reconnect.await())
        assertTrue(restarted.state.value is ConnectionUiState.Linked)
    }

    @Test
    fun `explicit reconnect leaves forced offline but unavailable Library stays offline`() =
        runTest {
            val failures = ArrayDeque<Exception>()
            val coordinator = coordinator(
                FakeClient(authFailures = failures),
                FakeProfileStore().apply { stored = profile() },
                storedCredential()
            )
            coordinator.restore()
            advanceUntilIdle()
            coordinator.workOffline()
            advanceUntilIdle()
            failures.add(SplClientException.ServerUnreachable())

            val result = async { coordinator.checkConnectionNow() }
            advanceUntilIdle()

            assertFalse(result.await())
            assertTrue(coordinator.state.value is ConnectionUiState.RestoreProblem)
        }

    @Test
    fun `explicit reconnect keeps authentication rejection in repair category`() = runTest {
        val failures = ArrayDeque<Exception>()
        val coordinator = coordinator(
            FakeClient(authFailures = failures),
            FakeProfileStore().apply { stored = profile() },
            storedCredential()
        )
        coordinator.restore()
        advanceUntilIdle()
        coordinator.workOffline()
        advanceUntilIdle()
        failures.add(SplClientException.AuthenticationRejected())

        val result = async { coordinator.checkConnectionNow() }
        advanceUntilIdle()

        assertFalse(result.await())
        assertTrue(coordinator.state.value is ConnectionUiState.AuthenticationRequired)
    }

    @Test
    fun `verification uses connection target without exposing endpoint choice to features`() =
        runTest {
            val requestedProfiles = mutableListOf<ConnectionProfile>()
            val coordinator = coordinator(
                FakeClient(),
                FakeProfileStore().apply { stored = profile() },
                storedCredential(),
                target = AuthenticatedConnectionTarget { selected, _, routes ->
                    requestedProfiles += selected
                    VerifiedConnection(authenticatedContext(), routes.activeLibraryBaseUrl)
                }
            )

            coordinator.restore()
            advanceUntilIdle()

            assertEquals(listOf(profile()), requestedProfiles)
            assertTrue(coordinator.state.value is ConnectionUiState.Linked)
        }

    @Test
    fun `matching persisted account is published before verification completes`() = runTest {
        val restoredProfile = profile()
        val persistedAccount =
            PersistedAccountContext(restoredProfile, "profile-1")
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
                    restoredProfile,
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
                    restoredProfile,
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
                restoredProfile,
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
    fun `transient restore failure preserves credential and profile`() = runTest {
        val restoredProfile = profile()
        val profileStore = FakeProfileStore().apply { stored = restoredProfile }
        val credentialStore = FakeCredentialStore().apply {
            stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
        }
        val persistedAccount =
            PersistedAccountContext(restoredProfile, "profile-1")
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
    fun `linked unreachable request confirms loss and heals without clearing account`() = runTest {
        val profileStore = FakeProfileStore().apply { stored = profile() }
        val credentialStore = FakeCredentialStore().apply {
            stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
        }
        val accountContextStore = FakePersistedAccountContextStore()
        val failures = ArrayDeque<Exception>()
        val coordinator = coordinator(
            FakeClient(authFailures = failures),
            profileStore,
            credentialStore,
            accountContextStore
        )
        coordinator.restore()
        advanceUntilIdle()
        val resolvedLocalAccount = coordinator.localAccountContext.value

        failures.add(SplClientException.ServerUnreachable())
        coordinator.authenticatedRequestUnreachable()
        advanceUntilIdle()

        assertEquals(
            ServerReachability.UNREACHABLE,
            (coordinator.state.value as ConnectionUiState.Linked).reachability
        )
        assertEquals(resolvedLocalAccount, coordinator.localAccountContext.value)
        assertFalse(profileStore.cleared)
        assertFalse(credentialStore.cleared)
        assertFalse(accountContextStore.cleared)

        coordinator.retryIfUnreachable()
        advanceUntilIdle()
        assertEquals(
            ServerReachability.REACHABLE,
            (coordinator.state.value as ConnectionUiState.Linked).reachability
        )
        assertEquals(resolvedLocalAccount, coordinator.localAccountContext.value)
    }

    @Test
    fun `a transient feature failure does not mark a reachable installation offline`() = runTest {
        val coordinator = coordinator(
            FakeClient(),
            FakeProfileStore().apply { stored = profile() },
            storedCredential()
        )
        coordinator.restore()
        advanceUntilIdle()

        coordinator.authenticatedRequestUnreachable()
        advanceUntilIdle()

        assertEquals(
            ServerReachability.REACHABLE,
            (coordinator.state.value as ConnectionUiState.Linked).reachability
        )
    }

    @Test
    fun `malformed and server error responses do not become offline`() = runTest {
        val failures = ArrayDeque<Exception>()
        val coordinator = coordinator(
            FakeClient(authFailures = failures),
            FakeProfileStore().apply { stored = profile() },
            storedCredential()
        )
        coordinator.restore()
        advanceUntilIdle()

        failures.add(SplClientException.ProtocolInvalid("account"))
        coordinator.authenticatedRequestUnreachable()
        advanceUntilIdle()
        assertEquals(
            ServerReachability.REACHABLE,
            (coordinator.state.value as ConnectionUiState.Linked).reachability
        )

        failures.add(SplClientException.AuthenticatedRequestFailed())
        coordinator.authenticatedRequestUnreachable()
        advanceUntilIdle()
        assertEquals(
            ServerReachability.REACHABLE,
            (coordinator.state.value as ConnectionUiState.Linked).reachability
        )
    }

    @Test
    fun `authentication rejection during reachability check requires repair`() = runTest {
        val failures = ArrayDeque<Exception>()
        val credentialStore = storedCredential()
        val coordinator = coordinator(
            FakeClient(authFailures = failures),
            FakeProfileStore().apply { stored = profile() },
            credentialStore
        )
        coordinator.restore()
        advanceUntilIdle()

        failures.add(SplClientException.AuthenticationRejected())
        coordinator.authenticatedRequestUnreachable()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.AuthenticationRequired)
        assertFalse(credentialStore.cleared)
    }
}
