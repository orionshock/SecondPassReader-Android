package com.secondpasslibrary.reader.connection.pairing

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.ClientSession
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionPairingControllerTest {
    @Test
    fun `presentation contains approval data but no protocol handle or transport coordinates`() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.start()
            runCurrent()
            assertEquals(
                ConnectionUiState.WaitingForApproval(
                    "Library",
                    "Tablet",
                    "ABCD-EFGH",
                    "https://library.example/approve",
                    "expiry"
                ),
                fixture.state
            )
            val fields = ConnectionUiState.WaitingForApproval::class.java.declaredFields
                .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            assertTrue(fields.all { it.type == String::class.java })
            assertFalse(fields.any { it.name in setOf("request", "pollUrl", "consumeUrl") })
            fixture.controller.cancel()
        }

    @Test
    fun `abandoning pairing cancels its polling loop`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.start()
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, fixture.client.pollCalls)
        fixture.controller.cancel()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, fixture.client.pollCalls)
        assertEquals(0, fixture.client.consumeCalls)
        assertTrue(fixture.issued.isEmpty())
    }

    @Test
    fun `recoverable failures back off to cap then reset after success`() = runTest {
        val fixture = Fixture(this)
        fixture.client.failures.addAll(
            listOf(
                SplClientException.ServerUnreachable(),
                SplClientException.PairingThrottled()
            ) +
                List(5) { SplClientException.ServerUnreachable() }
        )
        fixture.client.statuses.addAll(listOf(PairingStatus.PENDING, PairingStatus.APPROVED))
        fixture.start()
        advanceUntilIdle()
        assertEquals(listOf(3L, 6L, 12L, 24L, 48L, 60L, 60L, 60L, 3L), fixture.delays)
        assertEquals(1, fixture.issued.size)
        assertTrue(
            fixture.states.filterIsInstance<ConnectionUiState.WaitingForApproval>()
                .any { it.statusText.endsWith("Retrying.") }
        )
    }

    @Test
    fun `server interval above cap is respected`() = runTest {
        val fixture = Fixture(this)
        fixture.client.request = fixture.client.request.copy(intervalSeconds = 90)
        fixture.client.failures += SplClientException.PairingThrottled()
        fixture.client.statuses += PairingStatus.APPROVED
        fixture.start()
        advanceUntilIdle()
        assertEquals(listOf(90L, 90L), fixture.delays)
    }

    @Test
    fun `foregrounding replaces one loop and waits the server interval`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.start()
        runCurrent()
        advanceTimeBy(1_000)
        fixture.controller.foregrounded()
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, fixture.client.pollCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, fixture.client.pollCalls)
        fixture.controller.cancel()
    }

    @Test
    fun `denied expired consumed and invalid polling results remain terminal`() = runTest {
        val terminal = listOf(PairingStatus.DENIED, PairingStatus.EXPIRED, PairingStatus.CONSUMED)
        terminal.forEach { status ->
            val fixture = Fixture(this)
            fixture.client.statuses += status
            fixture.start()
            advanceUntilIdle()
            fixture.controller.foregrounded()
            advanceUntilIdle()
            assertTrue(fixture.state is ConnectionUiState.TerminalPairingProblem)
            assertTrue(
                (fixture.state as ConnectionUiState.TerminalPairingProblem).message.isNotBlank()
            )
            assertEquals(1, fixture.client.pollCalls)
            assertEquals(0, fixture.client.consumeCalls)
        }
        val invalid = Fixture(this)
        invalid.client.failures += SplClientException.ProtocolInvalid("pairing status")
        invalid.start()
        advanceUntilIdle()
        assertTrue(invalid.state is ConnectionUiState.TerminalPairingProblem)
        assertEquals(1, invalid.client.pollCalls)
    }

    @Test
    fun `approval consumes once even when foregrounded during consumption`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.client.statuses.addAll(listOf(PairingStatus.APPROVED, PairingStatus.APPROVED))
        fixture.client.consumeGate = { gate.await() }
        fixture.start()
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(fixture.state is ConnectionUiState.CompletingPairing)
        repeat(3) { fixture.controller.foregrounded() }
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, fixture.client.pollCalls)
        assertEquals(1, fixture.client.consumeCalls)
        assertEquals(1, fixture.issued.size)
        assertFalse(fixture.state is ConnectionUiState.Linked)
    }

    @Test
    fun `ambiguous consumption and already consumed never retry`() = runTest {
        for (failure in listOf(null, SplClientException.AmbiguousConsumeFailure())) {
            val fixture = Fixture(this)
            fixture.client.statuses += PairingStatus.APPROVED
            fixture.client.consumeFailure = failure
            fixture.client.consumption = PairingConsumption.AlreadyConsumed
            fixture.start()
            advanceUntilIdle()
            fixture.controller.foregrounded()
            advanceUntilIdle()
            assertTrue(fixture.state is ConnectionUiState.TerminalPairingProblem)
            assertEquals(1, fixture.client.consumeCalls)
            assertTrue(fixture.issued.isEmpty())
        }
    }

    @Test
    fun `cancelled in flight approval cannot consume even if transport ignores cancellation`() =
        runTest {
            val fixture = Fixture(this)
            val gate = CompletableDeferred<Unit>()
            fixture.client.pollGate = { withContext(NonCancellable) { gate.await() } }
            fixture.client.statuses += PairingStatus.APPROVED
            fixture.start()
            advanceTimeBy(3_000)
            runCurrent()
            fixture.controller.cancel()
            val published = fixture.states.size
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(0, fixture.client.consumeCalls)
            assertEquals(published, fixture.states.size)
            assertTrue(fixture.issued.isEmpty())
        }

    @Test
    fun `superseded consumption cannot hand stale credentials to coordinator`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.client.consumeGate = { withContext(NonCancellable) { gate.await() } }
        fixture.client.statuses += PairingStatus.APPROVED
        fixture.start()
        advanceTimeBy(3_000)
        runCurrent()
        fixture.client.consumeGate = {}
        fixture.client.statuses += PairingStatus.DENIED
        fixture.start()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(fixture.state is ConnectionUiState.TerminalPairingProblem)
        assertTrue(fixture.issued.isEmpty())
        assertEquals(1, fixture.client.consumeCalls)
    }

    @Test
    fun `cancelled creation cannot replace a newer request`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.client.createGate = { withContext(NonCancellable) { gate.await() } }
        fixture.start()
        runCurrent()
        fixture.client.createGate = {}
        fixture.client.request = fixture.client.request.copy(code = "NEW-CODE")
        fixture.client.statuses += PairingStatus.DENIED
        fixture.start()
        runCurrent()
        val published = fixture.states.size
        gate.complete(Unit)
        runCurrent()
        assertEquals(published, fixture.states.size)
        assertEquals("NEW-CODE", (fixture.state as ConnectionUiState.WaitingForApproval).code)
        advanceUntilIdle()
    }

    private class Fixture(scope: CoroutineScope) {
        val client = FakePairingClient()
        val states = mutableListOf<ConnectionUiState>()
        val issued = mutableListOf<PairingConsumption.CredentialIssued>()
        val delays = mutableListOf<Long>()
        val state: ConnectionUiState get() = states.last()
        val controller = ConnectionPairingController(
            client,
            PairingPollDelay { seconds ->
                delays += seconds
                delay(seconds * 1_000)
            },
            scope,
            states::add,
            { _, credential -> issued += credential }
        )
        fun start() = controller.start(server(), "Tablet")
    }
}

private class FakePairingClient : SecondPassClient {
    var request = PairingRequest(
        "ABCD-EFGH",
        "https://library.example/approve",
        "https://library.example/poll",
        "https://library.example/consume",
        "expiry",
        3
    )
    val statuses = ArrayDeque<PairingStatus>()
    val failures = ArrayDeque<SplClientException>()
    var pollCalls = 0
    var consumeCalls = 0
    var createGate: suspend () -> Unit = {}
    var pollGate: suspend () -> Unit = {}
    var consumeGate: suspend () -> Unit = {}
    var consumeFailure: SplClientException? = null
    var consumption: PairingConsumption = PairingConsumption.CredentialIssued(
        BearerCredential.restore("test-secret"),
        ClientSession("session", "Tablet", "reader")
    )
    override suspend fun discoverServer(userInput: String): DiscoveredServer = error("Not pairing")

    override suspend fun beginPairing(
        server: DiscoveredServer,
        clientName: String,
        clientType: String
    ): PairingRequest {
        val created = request
        createGate()
        return created
    }

    override suspend fun checkPairing(request: PairingRequest): PairingStatus {
        pollCalls++
        pollGate()
        failures.removeFirstOrNull()?.let { throw it }
        return statuses.removeFirstOrNull() ?: PairingStatus.PENDING
    }

    override suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption {
        consumeCalls++
        consumeGate()
        consumeFailure?.let { throw it }
        return consumption
    }

    override suspend fun loadAuthenticatedContext(
        libraryBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedContext = error("Verification belongs to the coordinator")
}

private fun server() = DiscoveredServer(
    ServerOrigin.fromUserInput("https://library.example"),
    "a6722b5a-7982-4778-8c74-39be4241a654",
    "https://library.example", "Library", "Books",
    "1.0", "", "0.1", "https://library.example/login-requests/", "Bearer"
)
