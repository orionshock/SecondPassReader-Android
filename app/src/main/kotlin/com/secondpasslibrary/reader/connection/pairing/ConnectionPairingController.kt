package com.secondpasslibrary.reader.connection.pairing

import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.SplClient
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionErrorPresenter
import com.secondpasslibrary.reader.connection.ConnectionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Connection-owned pairing lifecycle. Protocol coordinates never leave this owner for UI state.
 * Credential issuance hands off to the coordinator; it does not establish a verified connection.
 */
internal class ConnectionPairingController(
    private val client: SecondPassClient,
    private val pollDelay: PairingPollDelay,
    private val scope: CoroutineScope,
    private val onState: (ConnectionUiState) -> Unit,
    private val onCredentialIssued: (DiscoveredServer, PairingConsumption.CredentialIssued) -> Unit
) {
    private var operation: Job? = null
    private var pending: PendingPairing? = null

    @Suppress("TooGenericExceptionCaught") // Preserve feature mapping for creation failures.
    fun start(server: DiscoveredServer, clientName: String) {
        cancel()
        operation = scope.launch {
            onState(ConnectionUiState.StartingPairing(server, clientName))
            currentCoroutineContext().ensureActive()
            val request = try {
                client.beginPairing(server, clientName, SplClient.ANDROID_CLIENT_TYPE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                terminal(ConnectionErrorPresenter.message(failure))
                return@launch
            }
            currentCoroutineContext().ensureActive()
            val pairing = PendingPairing(server, clientName, request)
            pending = pairing
            poll(pairing)
        }
    }

    fun foregrounded() {
        val pairing = pending ?: return
        operation?.cancel()
        operation = scope.launch { poll(pairing) }
    }

    fun cancel() {
        pending = null
        operation?.cancel()
        operation = null
    }

    @Suppress("ReturnCount") // Terminal protocol states exit the single polling loop.
    private suspend fun poll(pairing: PendingPairing) {
        onState(pairing.presentation())
        var nextDelaySeconds = pairing.request.intervalSeconds
        while (currentCoroutineContext().isActive) {
            pollDelay.wait(nextDelaySeconds)
            currentCoroutineContext().ensureActive()
            val status = try {
                client.checkPairing(pairing.request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SplClientException) {
                currentCoroutineContext().ensureActive()
                if (!failure.isRecoverablePollingFailure()) {
                    terminal(ConnectionErrorPresenter.message(failure))
                    return
                }
                onState(
                    pairing.presentation().copy(
                        statusText = "${ConnectionErrorPresenter.message(failure)} Retrying."
                    )
                )
                nextDelaySeconds = (nextDelaySeconds * RETRY_BACKOFF_MULTIPLIER)
                    .coerceAtMost(maxOf(pairing.request.intervalSeconds, MAX_POLL_RETRY_SECONDS))
                continue
            }
            currentCoroutineContext().ensureActive()
            nextDelaySeconds = pairing.request.intervalSeconds
            when (status) {
                PairingStatus.PENDING -> onState(pairing.presentation())

                PairingStatus.APPROVED -> {
                    consume(pairing)
                    return
                }

                PairingStatus.DENIED -> {
                    terminal("The link request was denied. Start again for a new code.")
                    return
                }

                PairingStatus.EXPIRED -> {
                    terminal("The link request expired. Start again to request a new code.")
                    return
                }

                PairingStatus.CONSUMED -> {
                    terminal("This approval was already used. Start again with a new code.")
                    return
                }
            }
        }
    }

    private suspend fun consume(pairing: PendingPairing) {
        // Retire the poll handle before suspending: foregrounding cannot restart consumption.
        pending = null
        onState(ConnectionUiState.CompletingPairing(pairing.server.name, pairing.request.code))
        currentCoroutineContext().ensureActive()
        val consumption = try {
            client.consumeApprovedPairing(pairing.request)
        } catch (failure: SplClientException) {
            currentCoroutineContext().ensureActive()
            terminal(ConnectionErrorPresenter.message(failure))
            return
        }
        currentCoroutineContext().ensureActive()
        when (consumption) {
            is PairingConsumption.CredentialIssued ->
                onCredentialIssued(pairing.server, consumption)

            PairingConsumption.AlreadyConsumed ->
                terminal("The approval was used but the connection wasn’t completed. Start again.")
        }
    }

    private fun terminal(message: String) {
        pending = null
        onState(ConnectionUiState.TerminalPairingProblem(message))
    }

    private data class PendingPairing(
        val server: DiscoveredServer,
        val clientName: String,
        val request: PairingRequest
    ) {
        fun presentation() = ConnectionUiState.WaitingForApproval(
            serverName = server.name,
            clientName = clientName,
            code = request.code,
            authorizeUrl = request.authorizeUrl,
            expiresAt = request.expiresAt
        )
    }

    private companion object {
        const val RETRY_BACKOFF_MULTIPLIER = 2
        const val MAX_POLL_RETRY_SECONDS = 60L
    }
}

private fun SplClientException.isRecoverablePollingFailure(): Boolean =
    this is SplClientException.ServerUnreachable || this is SplClientException.PairingThrottled
