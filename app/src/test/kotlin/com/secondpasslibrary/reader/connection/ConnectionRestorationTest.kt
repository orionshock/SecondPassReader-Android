package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `verification uses connection target without exposing endpoint choice to features`() =
        runTest {
            val requestedProfiles = mutableListOf<ConnectionProfile>()
            val coordinator = coordinator(
                FakeClient(),
                FakeProfileStore().apply { stored = profile() },
                storedCredential(),
                target = AuthenticatedConnectionTarget { selected, _ ->
                    requestedProfiles += selected
                    authenticatedContext()
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
            InstallationReachability.UNREACHABLE,
            (coordinator.state.value as ConnectionUiState.Linked).reachability
        )
        assertEquals(resolvedLocalAccount, coordinator.localAccountContext.value)
        assertFalse(profileStore.cleared)
        assertFalse(credentialStore.cleared)
        assertFalse(accountContextStore.cleared)

        coordinator.retryIfUnreachable()
        advanceUntilIdle()
        assertEquals(
            InstallationReachability.REACHABLE,
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
            InstallationReachability.REACHABLE,
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
            InstallationReachability.REACHABLE,
            (coordinator.state.value as ConnectionUiState.Linked).reachability
        )

        failures.add(SplClientException.AuthenticatedRequestFailed())
        coordinator.authenticatedRequestUnreachable()
        advanceUntilIdle()
        assertEquals(
            InstallationReachability.REACHABLE,
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
