package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionDestructiveLifecycleTest : ConnectionCoordinatorTestSupport() {
    @Test
    fun `explicit local forget clears account context and account-scoped data`() = runTest {
        val accountContextStore = FakePersistedAccountContextStore().apply {
            stored =
                PersistedAccountContext(
                    profile(),
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
            listOf(AccountLocalScope.from(profile().serverId, "profile-1")),
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
            stored = PersistedAccountContext(profile(), "profile-1")
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
            stored = PersistedAccountContext(profile(), "profile-1")
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
            stored = PersistedAccountContext(profile(), "profile-1")
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
            stored = PersistedAccountContext(profile(), "profile-1")
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
}
