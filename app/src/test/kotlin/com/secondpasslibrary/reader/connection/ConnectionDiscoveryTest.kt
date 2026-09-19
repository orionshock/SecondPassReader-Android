package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionDiscoveryTest : ConnectionCoordinatorTestSupport() {
    @Test
    fun `invalid discovery returns to editable server entry and allows retry`() = runTest {
        val client =
            FakeClient(
                discoveryFailures =
                    ArrayDeque(listOf(SplClientException.NotSecondPassServer()))
            )
        val coordinator = coordinator(client, FakeProfileStore(), FakeCredentialStore())
        coordinator.restore()
        advanceUntilIdle()
        coordinator.updateServerUrl("https://not-a-library.example")

        coordinator.verifyServer()
        advanceUntilIdle()

        val rejected = coordinator.state.value as ConnectionUiState.ServerEntry
        assertEquals("https://not-a-library.example", rejected.serverUrl)
        assertTrue(rejected.message?.isNotBlank() == true)

        coordinator.updateServerUrl("https://library.example")
        coordinator.verifyServer()
        advanceUntilIdle()

        assertTrue(coordinator.state.value is ConnectionUiState.ServerConfirmed)
    }
}
