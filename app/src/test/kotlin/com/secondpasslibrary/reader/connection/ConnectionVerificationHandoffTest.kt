package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionVerificationHandoffTest : ConnectionCoordinatorTestSupport() {
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
}
