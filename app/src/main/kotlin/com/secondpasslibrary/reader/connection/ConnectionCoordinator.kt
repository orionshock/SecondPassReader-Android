package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.SplClient
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // One cohesive, explicit connection state machine.
internal class ConnectionCoordinator(
    private val client: SecondPassClient,
    private val profileStore: ConnectionProfileStore,
    private val credentialStore: BearerCredentialStore,
    private val accountContextStore: PersistedAccountContextStore,
    private val pollDelay: PairingPollDelay,
    private val defaultClientName: String,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Restoring)
    val state: StateFlow<ConnectionUiState> = mutableState.asStateFlow()
    private val mutableLocalAccountContext = MutableStateFlow<LocalAccountContext?>(null)
    val localAccountContext: StateFlow<LocalAccountContext?> =
        mutableLocalAccountContext.asStateFlow()

    private var operation: Job? = null

    fun restore() = replaceOperation {
        val stored = attempt { credentialStore.read() }.getOrElse {
            mutableLocalAccountContext.value = null
            mutableState.value =
                ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
            return@replaceOperation
        }
        var profile = attempt { profileStore.read() }.getOrElse {
            mutableLocalAccountContext.value = null
            mutableState.value =
                ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
            return@replaceOperation
        }
        if (profile == null && stored?.recoveryProfile != null) {
            profile = stored.recoveryProfile
            attempt { profileStore.write(profile) }.onFailure {
                mutableState.value =
                    ConnectionUiState.PersistenceRecovery(
                        profile,
                        "The consumed credential is safe, but its connection profile " +
                            "still needs to be stored."
                    )
                return@replaceOperation
            }
        }
        if (profile == null || stored == null) {
            if (stored != null || profile != null) {
                attempt { clearLocalConnection() }.onFailure {
                    mutableState.value =
                        ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
                    return@replaceOperation
                }
            }
            mutableState.value = ConnectionUiState.ServerEntry()
            return@replaceOperation
        }
        attempt { credentialStore.markProfileCommitted() }
        resolveLocalAccountContext(profile)
        verifyStored(profile, stored.credential, restoring = true)
    }

    fun updateServerUrl(value: String) {
        if (mutableState.value is ConnectionUiState.ServerEntry) {
            mutableState.value = ConnectionUiState.ServerEntry(value)
        }
    }

    fun verifyServer() {
        val entry = mutableState.value as? ConnectionUiState.ServerEntry ?: return
        replaceOperation {
            mutableState.value = ConnectionUiState.VerifyingServer(entry.serverUrl)
            attempt { client.discoverServer(entry.serverUrl) }
                .onSuccess {
                    mutableState.value =
                        ConnectionUiState.ServerConfirmed(it, defaultClientName)
                }
                .onFailure {
                    mutableState.value =
                        ConnectionUiState.ServerEntry(
                            entry.serverUrl,
                            ConnectionErrorPresenter.message(it)
                        )
                }
        }
    }

    fun updateClientName(value: String) {
        val confirmed = mutableState.value as? ConnectionUiState.ServerConfirmed ?: return
        mutableState.value = confirmed.copy(clientName = value.take(MAX_CLIENT_NAME_LENGTH))
    }

    fun beginPairing() {
        val confirmed = mutableState.value as? ConnectionUiState.ServerConfirmed ?: return
        val clientName = confirmed.clientName.trim()
        if (clientName.isEmpty()) {
            mutableState.value = confirmed.copy(clientName = "")
            return
        }
        replaceOperation {
            mutableState.value = ConnectionUiState.StartingPairing(confirmed.server, clientName)
            attempt {
                client.beginPairing(confirmed.server, clientName, SplClient.ANDROID_CLIENT_TYPE)
            }
                .onSuccess { request -> poll(confirmed.server, clientName, request) }
                .onFailure {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem(
                            ConnectionErrorPresenter.message(it)
                        )
                }
        }
    }

    fun pairingForegrounded() {
        val waiting = mutableState.value as? ConnectionUiState.WaitingForApproval ?: return
        replaceOperation { poll(waiting.server, waiting.clientName, waiting.request) }
    }

    fun abandonPairing() {
        operation?.cancel()
        mutableState.value = ConnectionUiState.ServerEntry()
    }

    fun retryProfilePersistence() {
        val recovery = mutableState.value as? ConnectionUiState.PersistenceRecovery ?: return
        replaceOperation {
            val stored = credentialStore.read()
            if (stored == null) {
                mutableState.value =
                    ConnectionUiState.ServerEntry(
                        message = "The consumed credential is no longer available."
                    )
                return@replaceOperation
            }
            persistProfileAndVerify(recovery.profile, stored.credential)
        }
    }

    fun retryStoredVerification() {
        val current = mutableState.value
        val profile =
            when (current) {
                is ConnectionUiState.StoredCredentialProblem -> current.profile
                is ConnectionUiState.RestoreProblem -> current.profile
                else -> return
            }
        replaceOperation {
            val stored = credentialStore.read()
            if (stored == null) {
                mutableState.value =
                    ConnectionUiState.ServerEntry(message = "The stored credential is missing.")
            } else {
                verifyStored(
                    profile,
                    stored.credential,
                    restoring = current is ConnectionUiState.RestoreProblem
                )
            }
        }
    }

    fun forgetLocalConnection() = replaceOperation {
        attempt { clearLocalConnection() }
            .onSuccess {
                mutableState.value =
                    ConnectionUiState.ServerEntry(message = "Local connection data was removed.")
            }
            .onFailure {
                mutableState.value =
                    ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
            }
    }

    fun close() {
        operation?.cancel()
    }

    @Suppress("ReturnCount") // Terminal protocol states exit the single polling loop.
    private suspend fun poll(
        server: com.secondpasslibrary.client.DiscoveredServer,
        clientName: String,
        request: PairingRequest
    ) {
        mutableState.value = ConnectionUiState.WaitingForApproval(server, clientName, request)
        var nextDelaySeconds = request.intervalSeconds
        while (currentCoroutineContext().isActive) {
            pollDelay.wait(nextDelaySeconds)
            val status =
                try {
                    client.checkPairing(request)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: SplClientException) {
                    if (!failure.isRecoverablePollingFailure()) {
                        mutableState.value =
                            ConnectionUiState.TerminalPairingProblem(
                                ConnectionErrorPresenter.message(failure)
                            )
                        return
                    }
                    nextDelaySeconds =
                        reportRecoverablePollingFailure(
                            server,
                            clientName,
                            request,
                            failure,
                            nextDelaySeconds
                        )
                    continue
                }
            nextDelaySeconds = request.intervalSeconds
            when (status) {
                PairingStatus.PENDING ->
                    mutableState.value =
                        ConnectionUiState.WaitingForApproval(server, clientName, request)

                PairingStatus.APPROVED -> {
                    completePairing(server, request)
                    return
                }

                PairingStatus.DENIED -> {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem("The pairing request was denied.")
                    return
                }

                PairingStatus.EXPIRED -> {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem("The pairing request expired.")
                    return
                }

                PairingStatus.CONSUMED -> {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem(
                            "This approval was already consumed. Start a new pairing request."
                        )
                    return
                }
            }
        }
    }

    private fun reportRecoverablePollingFailure(
        server: com.secondpasslibrary.client.DiscoveredServer,
        clientName: String,
        request: PairingRequest,
        failure: SplClientException,
        currentDelaySeconds: Long
    ): Long {
        mutableState.value =
            ConnectionUiState.WaitingForApproval(
                server,
                clientName,
                request,
                "${ConnectionErrorPresenter.message(failure)} Retrying automatically."
            )
        return (currentDelaySeconds * RETRY_BACKOFF_MULTIPLIER)
            .coerceAtMost(maxOf(request.intervalSeconds, MAX_POLL_RETRY_SECONDS))
    }

    private suspend fun completePairing(
        server: com.secondpasslibrary.client.DiscoveredServer,
        request: PairingRequest
    ) {
        mutableState.value = ConnectionUiState.CompletingPairing(server.name, request.code)
        val consumption =
            try {
                client.consumeApprovedPairing(request)
            } catch (failure: SplClientException) {
                mutableState.value =
                    ConnectionUiState.TerminalPairingProblem(
                        ConnectionErrorPresenter.message(failure)
                    )
                return
            }
        when (consumption) {
            is PairingConsumption.CredentialIssued -> {
                val profile = ConnectionProfile.linked(server, consumption.clientSession)
                try {
                    credentialStore.write(consumption.credential, profile)
                } catch (failure: CredentialStorageException) {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem(
                            "The server issued a one-time credential, but secure storage failed. " +
                                ConnectionErrorPresenter.message(failure)
                        )
                    return
                }
                persistProfileAndVerify(profile, consumption.credential)
            }

            PairingConsumption.AlreadyConsumed ->
                mutableState.value =
                    ConnectionUiState.TerminalPairingProblem(
                        "The approval was consumed without returning a credential. " +
                            "Start a new pairing request."
                    )
        }
    }

    private suspend fun persistProfileAndVerify(
        profile: ConnectionProfile,
        credential: BearerCredential
    ) {
        try {
            profileStore.write(profile)
        } catch (_: ConnectionProfileStorageException) {
            mutableState.value =
                ConnectionUiState.PersistenceRecovery(
                    profile,
                    "The one-time credential is encrypted and safe, but the connection " +
                        "profile could not be stored."
                )
            return
        }
        attempt { credentialStore.markProfileCommitted() }
        verifyStored(profile, credential, restoring = false)
    }

    private suspend fun verifyStored(
        profile: ConnectionProfile,
        credential: BearerCredential,
        restoring: Boolean
    ) {
        try {
            val context = client.loadAuthenticatedContext(profile.apiBaseUrl, credential)
            val persistedAccount =
                PersistedAccountContext(
                    connectionIdentity = profile.authenticatedConnectionIdentity,
                    profileId = context.currentUser.profileId
                )
            attempt { accountContextStore.write(persistedAccount) }
                .onSuccess {
                    mutableLocalAccountContext.value =
                        LocalAccountContext(profile, persistedAccount)
                }
            mutableState.value = ConnectionUiState.Linked(profile, context)
        } catch (_: SplClientException.AuthenticationRejected) {
            if (restoring) {
                attempt { clearLocalConnection() }
                    .onSuccess {
                        mutableState.value =
                            ConnectionUiState.ServerEntry(
                                message =
                                    "The saved credential was revoked or rejected. " +
                                        "Link this device again."
                            )
                    }
                    .onFailure {
                        mutableState.value =
                            ConnectionUiState.LocalStorageProblem(
                                ConnectionErrorPresenter.message(it)
                            )
                    }
            } else {
                mutableState.value =
                    ConnectionUiState.StoredCredentialProblem(
                        profile,
                        "The credential was stored, but the server rejected " +
                            "authenticated verification.",
                        retryable = false
                    )
            }
        } catch (failure: SplClientException) {
            val message = ConnectionErrorPresenter.message(failure)
            mutableState.value =
                if (restoring) {
                    ConnectionUiState.RestoreProblem(profile, message)
                } else {
                    ConnectionUiState.StoredCredentialProblem(profile, message, retryable = true)
                }
        }
    }

    private suspend fun clearLocalConnection() {
        mutableLocalAccountContext.value = null
        val profileResult = runCatching { profileStore.clear() }
        val credentialResult = runCatching { credentialStore.clear() }
        val accountContextResult = runCatching { accountContextStore.clear() }
        listOf(credentialResult, profileResult, accountContextResult)
            .firstNotNullOfOrNull { it.exceptionOrNull() }
            ?.let { throw it }
    }

    private suspend fun resolveLocalAccountContext(profile: ConnectionProfile) {
        mutableLocalAccountContext.value =
            attempt { accountContextStore.read() }
                .getOrNull()
                ?.takeIf { it.matches(profile) }
                ?.let { LocalAccountContext(profile, it) }
    }

    private fun replaceOperation(block: suspend () -> Unit) {
        operation?.cancel()
        operation = scope.launch { block() }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        runCatching { block() }.also { result ->
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        }

    private companion object {
        const val MAX_CLIENT_NAME_LENGTH = 200
        const val RETRY_BACKOFF_MULTIPLIER = 2
        const val MAX_POLL_RETRY_SECONDS = 60L
    }

    fun authenticatedRequestRejected() {
        val linked = mutableState.value as? ConnectionUiState.Linked ?: return
        operation?.cancel()
        mutableState.value =
            ConnectionUiState.StoredCredentialProblem(
                profile = linked.profile,
                message = "The server rejected this device's stored credential.",
                retryable = false
            )
    }
}

private fun SplClientException.isRecoverablePollingFailure(): Boolean =
    this is SplClientException.ServerUnreachable || this is SplClientException.PairingThrottled
