package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.PairingStatus
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
internal class ConnectionPairingHandoffTest : ConnectionCoordinatorTestSupport() {
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
}
