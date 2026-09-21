package com.secondpasslibrary.reader.connection.discovery

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.PublicServerInfo
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionLanDiscoveryControllerTest {
    @Test
    fun `valid candidates expose HTTP metadata and invalid candidates stay hidden`() = runTest {
        val discovery = FakeLanLibraryUrlDiscovery()
        val client = FakeDiscoveryClient().apply {
            responses[VALID_URL] = server(VALID_URL, SERVER_ID_ONE, "Library One", "Books")
            failures[INVALID_URL] = SplClientException.NotSecondPassServer()
            failures[MISSING_ID_URL] = SplClientException.NotSecondPassServer()
        }
        val controller = ConnectionLanDiscoveryController(discovery, client, this)
        controller.start()

        discovery.urls.value = setOf(VALID_URL, INVALID_URL, MISSING_ID_URL)
        advanceUntilIdle()

        assertEquals(
            listOf(
                ConnectionLibrarySuggestion(
                    SERVER_ID_ONE,
                    "Library One",
                    "Books",
                    VALID_URL
                )
            ),
            controller.suggestions.value
        )
        controller.stop()
    }

    @Test
    fun `multiple servers remain and duplicate identity chooses deterministic URL`() = runTest {
        val discovery = FakeLanLibraryUrlDiscovery()
        val alternate = "https://alternate.example"
        val second = "https://second.example"
        val client = FakeDiscoveryClient().apply {
            responses[VALID_URL] = server(VALID_URL, SERVER_ID_ONE, "Library One", "Books")
            responses[alternate] = server(alternate, SERVER_ID_ONE, "Library One", "Books")
            responses[second] = server(second, SERVER_ID_TWO, "Library Two", "More books")
        }
        val controller = ConnectionLanDiscoveryController(discovery, client, this)
        controller.start()

        discovery.urls.value = setOf(VALID_URL, alternate, second)
        advanceUntilIdle()

        assertEquals(2, controller.suggestions.value.size)
        assertEquals(alternate, controller.suggestions.value.first().url)
        assertEquals(SERVER_ID_TWO, controller.suggestions.value.last().serverId)
        controller.stop()
    }

    @Test
    fun `disappearance prevents stale validation from publishing`() = runTest {
        val discovery = FakeLanLibraryUrlDiscovery()
        val gate = CompletableDeferred<Unit>()
        val client = FakeDiscoveryClient(gate).apply {
            responses[VALID_URL] = server(VALID_URL, SERVER_ID_ONE, "Library One", "Books")
        }
        val controller = ConnectionLanDiscoveryController(discovery, client, this)
        controller.start()
        discovery.urls.value = setOf(VALID_URL)
        advanceUntilIdle()

        discovery.urls.value = emptySet()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(controller.suggestions.value.isEmpty())
        controller.stop()
    }

    @Test
    fun `stop prevents stale validation from publishing`() = runTest {
        val discovery = FakeLanLibraryUrlDiscovery()
        val gate = CompletableDeferred<Unit>()
        val client = FakeDiscoveryClient(gate).apply {
            responses[VALID_URL] = server(VALID_URL, SERVER_ID_ONE, "Library One", "Books")
        }
        val controller = ConnectionLanDiscoveryController(discovery, client, this)
        controller.start()
        discovery.urls.value = setOf(VALID_URL)
        advanceUntilIdle()

        controller.stop()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(controller.suggestions.value.isEmpty())
    }

    private companion object {
        const val VALID_URL = "https://library.example"
        const val INVALID_URL = "https://invalid.example"
        const val MISSING_ID_URL = "https://missing-id.example"
        const val SERVER_ID_ONE = "a6722b5a-7982-4778-8c74-39be4241a654"
        const val SERVER_ID_TWO = "5222fe20-919b-4d2e-a6e9-9be21c508801"
    }
}

private class FakeLanLibraryUrlDiscovery : LanLibraryUrlDiscovery {
    val urls = MutableStateFlow<Set<String>>(emptySet())

    override fun candidateUrls() = urls
}

private class FakeDiscoveryClient(private val gate: CompletableDeferred<Unit>? = null) :
    SecondPassClient {
    val responses = mutableMapOf<String, DiscoveredServer>()
    val failures = mutableMapOf<String, Exception>()

    override suspend fun publicServerInfo(libraryBaseUrl: String): PublicServerInfo {
        gate?.let { withContext(NonCancellable) { it.await() } }
        failures[libraryBaseUrl]?.let { throw it }
        val server = checkNotNull(responses[libraryBaseUrl])
        return PublicServerInfo(
            server.serverId,
            server.name,
            server.description,
            server.version,
            server.releaseDate
        )
    }

    override suspend fun discoverServer(userInput: String): DiscoveredServer =
        error("Pairing discovery must not run for nearby suggestions")

    override suspend fun beginPairing(
        server: DiscoveredServer,
        clientName: String,
        clientType: String
    ): PairingRequest = error("Pairing is outside LAN discovery")

    override suspend fun checkPairing(request: PairingRequest): PairingStatus =
        error("Pairing is outside LAN discovery")

    override suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption =
        error("Pairing is outside LAN discovery")

    override suspend fun loadAuthenticatedContext(
        libraryBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedContext = error("Authentication is outside LAN discovery")
}

private fun server(
    url: String,
    serverId: String,
    name: String,
    description: String
): DiscoveredServer = DiscoveredServer(
    serverOrigin = ServerOrigin.fromUserInput(url),
    serverId = serverId,
    libraryBaseUrl = "$url",
    name = name,
    description = description,
    version = "1",
    releaseDate = "2026-09-17",
    discoveryVersion = "1",
    loginRequestUrl = "$url/api/v1/client-api/login-requests/",
    tokenType = "Bearer"
)
