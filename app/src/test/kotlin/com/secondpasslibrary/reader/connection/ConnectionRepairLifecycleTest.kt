package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionRepairLifecycleTest : ConnectionCoordinatorTestSupport() {
    @Test
    fun `invalid restored credential retains same-account local state`() = runTest {
        val profile = profile()
        val profileStore = FakeProfileStore().apply { stored = profile }
        val credentialStore = FakeCredentialStore().apply {
            stored = StoredCredential(BearerCredential.restore("spl_secret"), null)
        }
        val accountContextStore = FakePersistedAccountContextStore().apply {
            stored = PersistedAccountContext(profile, "profile-1")
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
                PersistedAccountContext(oldProfile, "profile-1")
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
        assertEquals("new-session", accountStore.stored?.clientSessionId)
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
                PersistedAccountContext(oldProfile, "profile-1")
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
            listOf(AccountLocalScope.from(oldProfile.serverId, "profile-1")),
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
            PersistedAccountContext(oldProfile, "profile-1")
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
}
