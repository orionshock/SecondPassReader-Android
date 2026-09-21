package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionRouteFailoverTest : ConnectionCoordinatorTestSupport() {
    private val first = "https://library.example"
    private val second = "https://library-alt.example"
    private val third = "http://192.168.1.10:8080"

    @Test
    fun `one route verifies without public recheck`() = runTest {
        val client = RouteClient()
        val routes = FakeRoutesStore()
        val connection = savedConnection(client, routes)

        connection.restore()
        advanceUntilIdle()

        assertTrue(connection.state.value is ConnectionUiState.Linked)
        assertEquals(listOf("auth:$first"), client.calls)
        assertEquals(listOf(first), routes.stored?.serverUrls)
    }

    @Test
    fun `transport failures advance through routes in operator order without reordering`() =
        runTest {
            val client = RouteClient().apply {
                unreachable += first
                unreachable += second
                advertised = listOf(first, second, third)
            }
            val routes = savedRoutes(listOf(first, second, third))
            val connection = savedConnection(client, routes)

            connection.restore()
            advanceUntilIdle()

            assertTrue(connection.state.value is ConnectionUiState.Linked)
            assertEquals(
                listOf("auth:$first", "identify:$second", "identify:$third", "auth:$third"),
                client.calls
            )
            assertEquals(listOf(first, second, third), routes.stored?.serverUrls)
            assertEquals(third, routes.stored?.activeLibraryBaseUrl)

            client.calls.clear()
            connection.authenticatedRequestUnreachable(third)
            advanceUntilIdle()
            assertEquals("identify:$first", client.calls.first())
        }

    @Test
    fun `first transport failure uses second route`() = runTest {
        val client = RouteClient().apply {
            unreachable += first
            advertised = listOf(first, second)
        }
        val routes = savedRoutes(listOf(first, second))
        val connection = savedConnection(client, routes)

        connection.restore()
        advanceUntilIdle()

        assertEquals(listOf("auth:$first", "identify:$second", "auth:$second"), client.calls)
        assertEquals(second, routes.stored?.activeLibraryBaseUrl)
    }

    @Test
    fun `all unreachable routes retain offline reachability`() = runTest {
        val client = RouteClient().apply { advertised = listOf(first, second) }
        val routes = savedRoutes(listOf(first, second))
        val connection = savedConnection(client, routes)
        connection.restore()
        advanceUntilIdle()
        client.calls.clear()
        client.unreachable += first
        client.unreachable += second

        connection.authenticatedRequestUnreachable(first)
        advanceUntilIdle()

        assertEquals(
            ServerReachability.UNREACHABLE,
            (connection.state.value as ConnectionUiState.Linked).reachability
        )
        assertEquals(listOf("auth:$first", "identify:$second"), client.calls)
    }

    @Test
    fun `wrong alternate identity is skipped without credentials`() = runTest {
        val client = RouteClient().apply {
            unreachable += first
            identityByUrl[second] = "b6722b5a-7982-4778-8c74-39be4241a654"
            advertised = listOf(first, second, third)
        }
        val routes = savedRoutes(listOf(first, second, third))
        val connection = savedConnection(client, routes)

        connection.restore()
        advanceUntilIdle()

        assertTrue(connection.state.value is ConnectionUiState.Linked)
        assertEquals(
            listOf("auth:$first", "identify:$second", "identify:$third", "auth:$third"),
            client.calls
        )
        assertFalse(client.calls.contains("auth:$second"))
    }

    @Test
    fun `authentication rejection on verified alternate enters repair and stops`() = runTest {
        val client = RouteClient().apply {
            unreachable += first
            rejected += second
        }
        val connection = savedConnection(client, savedRoutes(listOf(first, second, third)))

        connection.restore()
        advanceUntilIdle()

        assertTrue(connection.state.value is ConnectionUiState.AuthenticationRequired)
        assertEquals(listOf("auth:$first", "identify:$second", "auth:$second"), client.calls)
    }

    @Test
    fun `authenticated refresh replaces routes but retains successful active route`() = runTest {
        val client = RouteClient().apply {
            unreachable += first
            advertised = listOf(third, second, first)
        }
        val routes = savedRoutes(listOf(first, second))
        val connection = savedConnection(client, routes)

        connection.restore()
        advanceUntilIdle()

        assertEquals(listOf(third, second, first), routes.stored?.serverUrls)
        assertEquals(second, routes.stored?.activeLibraryBaseUrl)
        assertEquals(listOf("auth:$first", "identify:$second", "auth:$second"), client.calls)
    }

    @Test
    fun `forced offline does not probe and reconnect searches persisted routes`() = runTest {
        val client = RouteClient().apply { advertised = listOf(first, second) }
        val routes = savedRoutes(listOf(first, second))
        val connection = savedConnection(client, routes)
        connection.restore()
        advanceUntilIdle()
        connection.workOffline()
        advanceUntilIdle()
        client.calls.clear()
        client.unreachable += first

        connection.authenticatedRequestUnreachable()
        connection.retryIfUnreachable()
        advanceUntilIdle()
        assertTrue(client.calls.isEmpty())

        val result = async { connection.checkConnectionNow() }
        advanceUntilIdle()
        assertTrue(result.await())
        assertEquals(listOf("auth:$first", "identify:$second", "auth:$second"), client.calls)
    }

    @Test
    fun `restart uses persisted routes without discovery`() = runTest {
        val routes = savedRoutes(listOf(first, second))
        val firstClient = RouteClient().apply {
            unreachable += first
            advertised = listOf(first, second)
        }
        val connection = savedConnection(firstClient, routes)

        connection.restore()
        advanceUntilIdle()
        connection.close()
        val restartedClient = RouteClient().apply {
            unreachable += first
            advertised = listOf(first, second)
        }
        val restarted = savedConnection(restartedClient, routes)
        restarted.restore()
        advanceUntilIdle()

        assertTrue(restarted.state.value is ConnectionUiState.Linked)
        assertEquals(second, routes.stored?.activeLibraryBaseUrl)
        assertEquals(2, routes.reads)
        assertEquals(listOf("identify:$first", "auth:$second"), restartedClient.calls)
        assertEquals(0, firstClient.discoveryCalls + restartedClient.discoveryCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.savedConnection(
        client: RouteClient,
        routes: FakeRoutesStore
    ) = coordinator(
        client,
        FakeProfileStore().apply { stored = profile() },
        storedCredential(),
        routesStore = routes
    )

    private fun savedRoutes(urls: List<String>) = FakeRoutesStore().apply {
        stored = KnownServerRoutes(profile().serverId, urls, first)
    }

    private inner class RouteClient : SecondPassClient {
        val calls = mutableListOf<String>()
        val unreachable = mutableSetOf<String>()
        val rejected = mutableSetOf<String>()
        val identityByUrl = mutableMapOf<String, String>()
        var advertised = listOf(first)
        var discoveryCalls = 0

        override suspend fun discoverServer(userInput: String): DiscoveredServer {
            discoveryCalls += 1
            error("Reconnect must not discover")
        }

        override suspend fun identifyServer(libraryBaseUrl: String): String {
            calls += "identify:$libraryBaseUrl"
            if (libraryBaseUrl in unreachable) throw SplClientException.ServerUnreachable()
            return identityByUrl[libraryBaseUrl] ?: profile().serverId
        }

        override suspend fun loadAuthenticatedContext(
            libraryBaseUrl: String,
            credential: BearerCredential
        ): AuthenticatedContext {
            calls += "auth:$libraryBaseUrl"
            if (libraryBaseUrl in unreachable) throw SplClientException.ServerUnreachable()
            if (libraryBaseUrl in rejected) throw SplClientException.AuthenticationRejected()
            val context = authenticatedContext()
            return context.copy(serverInfo = context.serverInfo.copy(serverUrls = advertised))
        }

        override suspend fun beginPairing(
            server: DiscoveredServer,
            clientName: String,
            clientType: String
        ): PairingRequest = error("unused")

        override suspend fun checkPairing(request: PairingRequest): PairingStatus = error("unused")

        override suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption =
            error("unused")
    }
}
