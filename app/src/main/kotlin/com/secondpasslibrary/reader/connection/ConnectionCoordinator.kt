package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.ClientSessionRevocationClient
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
    private val clientSessionRevocationClient: ClientSessionRevocationClient,
    private val profileStore: ConnectionProfileStore,
    private val credentialStore: BearerCredentialStore,
    private val accountContextStore: PersistedAccountContextStore,
    private val accountLocalDataCleaner: AccountLocalDataCleaner,
    private val pollDelay: PairingPollDelay,
    private val defaultClientName: String,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Restoring)
    val state: StateFlow<ConnectionUiState> = mutableState.asStateFlow()
    private val mutableLocalAccountContext = MutableStateFlow<LocalAccountContext?>(null)
    val localAccountContext: StateFlow<LocalAccountContext?> =
        mutableLocalAccountContext.asStateFlow()
    private val mutableLifecycleActionState =
        MutableStateFlow<ConnectionLifecycleActionState>(ConnectionLifecycleActionState.Idle)
    val lifecycleActionState: StateFlow<ConnectionLifecycleActionState> =
        mutableLifecycleActionState.asStateFlow()

    private var operation: Job? = null

    fun restore() = replaceOperation {
        mutableState.value = ConnectionUiState.Restoring
        var (profile, stored) = readPersistedConnection() ?: return@replaceOperation
        if (profile == null && stored?.recoveryProfile != null) {
            profile = stored.recoveryProfile
            attempt { profileStore.write(profile) }.onFailure {
                mutableState.value =
                    ConnectionUiState.PersistenceRecovery(
                        profile,
                        "The connection is secure but couldn’t be saved on this device. Retry."
                    )
                return@replaceOperation
            }
            resolveLocalAccountContext(profile)
        }
        if (profile == null || stored == null) {
            if (stored != null || profile != null) {
                attempt { clearConnectionPersistence() }.onFailure {
                    mutableState.value =
                        ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
                    return@replaceOperation
                }
            }
            mutableState.value = ConnectionUiState.ServerEntry()
            return@replaceOperation
        }
        attempt { credentialStore.markProfileCommitted() }
        verifyStored(profile, stored.credential, restoring = true)
    }

    private suspend fun readPersistedConnection(): Pair<ConnectionProfile?, StoredCredential?>? {
        var persistedConnection: Pair<ConnectionProfile?, StoredCredential?>? = null
        val profileResult = attempt { profileStore.read() }
        profileResult.onFailure {
            mutableLocalAccountContext.value = null
            mutableState.value =
                ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
        }
        if (profileResult.isSuccess) {
            val profile = profileResult.getOrNull()
            profile?.let { resolveLocalAccountContext(it) }
            val storedResult = attempt { credentialStore.read() }
            storedResult.onFailure {
                mutableState.value =
                    ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
            }
            if (storedResult.isSuccess) {
                persistedConnection = profile to storedResult.getOrNull()
            }
        }
        return persistedConnection
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
        mutableState.value =
            mutableLocalAccountContext.value?.let {
                ConnectionUiState.AuthenticationRequired(
                    it.profile,
                    "Repair this connection to continue."
                )
            } ?: ConnectionUiState.ServerEntry()
    }

    fun relinkLocalAccount() {
        val profile =
            mutableLocalAccountContext.value?.profile
                ?: (mutableState.value as? ConnectionUiState.AuthenticationRequired)?.profile
                ?: return
        replaceOperation {
            mutableState.value = ConnectionUiState.VerifyingServer(profile.serverOrigin)
            attempt { client.discoverServer(profile.serverOrigin) }
                .onSuccess { server ->
                    mutableState.value =
                        ConnectionUiState.ServerConfirmed(server, profile.clientName)
                }
                .onFailure { failure ->
                    mutableState.value =
                        ConnectionUiState.AuthenticationRequired(
                            profile,
                            ConnectionErrorPresenter.message(failure)
                        )
                }
        }
    }

    fun retryProfilePersistence() {
        val recovery = mutableState.value as? ConnectionUiState.PersistenceRecovery ?: return
        replaceOperation {
            val stored = credentialStore.read()
            if (stored == null) {
                mutableState.value =
                    ConnectionUiState.ServerEntry(
                        message = "The connection can’t be recovered. Enter the Library address."
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
                    ConnectionUiState.ServerEntry(
                        message = "The saved connection is missing. Enter the Library address."
                    )
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
        mutableLifecycleActionState.value = ConnectionLifecycleActionState.Idle
        attempt { resetLocalAccount() }
            .onSuccess {
                mutableState.value =
                    ConnectionUiState.ServerEntry(message = "Connection and local data removed.")
            }
            .onFailure {
                mutableState.value =
                    ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(it))
            }
    }

    fun logout() {
        val linked = mutableState.value as? ConnectionUiState.Linked ?: return
        replaceOperation {
            mutableLifecycleActionState.value = ConnectionLifecycleActionState.LoggingOut
            val stored =
                attempt { credentialStore.read() }.getOrElse { failure ->
                    reportLogoutFailure(failure)
                    return@replaceOperation
                }
            if (stored == null) {
                reportLogoutFailure(IllegalStateException("The stored credential is missing."))
                return@replaceOperation
            }
            val revokeFailure =
                attempt {
                    clientSessionRevocationClient.revokeCurrentClientSession(
                        linked.profile.apiBaseUrl,
                        stored.credential,
                        linked.profile.clientSessionId
                    )
                }.exceptionOrNull()
            if (revokeFailure != null &&
                revokeFailure !is SplClientException.AuthenticationRejected
            ) {
                reportLogoutFailure(revokeFailure)
                return@replaceOperation
            }
            attempt { resetLocalAccount() }
                .onSuccess {
                    mutableLifecycleActionState.value = ConnectionLifecycleActionState.Idle
                    mutableState.value =
                        ConnectionUiState.ServerEntry(message = "This device was logged out.")
                }
                .onFailure { failure ->
                    mutableLifecycleActionState.value = ConnectionLifecycleActionState.Idle
                    mutableState.value =
                        ConnectionUiState.LocalStorageProblem(
                            ConnectionErrorPresenter.message(failure)
                        )
                }
        }
    }

    private fun reportLogoutFailure(failure: Throwable) {
        mutableLifecycleActionState.value =
            ConnectionLifecycleActionState.LogoutFailed(ConnectionErrorPresenter.message(failure))
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
                    mutableState.value = ConnectionUiState.TerminalPairingProblem(
                        "The link request was denied. Start again for a new code."
                    )
                    return
                }

                PairingStatus.EXPIRED -> {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem(
                            "The link request expired. Start again to request a new code."
                        )
                    return
                }

                PairingStatus.CONSUMED -> {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem(
                            "This approval was already used. Start again with a new code."
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
                "${ConnectionErrorPresenter.message(failure)} Retrying."
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
                } catch (_: CredentialStorageException) {
                    mutableState.value =
                        ConnectionUiState.TerminalPairingProblem(
                            "The connection was approved but couldn’t be saved securely. " +
                                "Start again."
                        )
                    return
                }
                persistProfileAndVerify(profile, consumption.credential)
            }

            PairingConsumption.AlreadyConsumed ->
                mutableState.value =
                    ConnectionUiState.TerminalPairingProblem(
                        "The approval was used but the connection wasn’t completed. Start again."
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
                    "The connection is secure but couldn’t be saved on this device. Retry."
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
                    profileId = context.currentUser.profileId,
                    accountServerOrigin = profile.serverOrigin
                )
            val previousAccount = mutableLocalAccountContext.value?.persistedAccount
                ?: accountContextStore.read()
            if (previousAccount != null &&
                previousAccount.localDataKey() != persistedAccount.localDataKey()
            ) {
                val purgeResult = attempt {
                    accountLocalDataCleaner.purge(previousAccount.localDataKey())
                    accountContextStore.clear()
                }
                purgeResult.exceptionOrNull()?.let { failure ->
                    mutableState.value =
                        ConnectionUiState.LocalStorageProblem(
                            ConnectionErrorPresenter.message(failure)
                        )
                    return
                }
                mutableLocalAccountContext.value = null
            }
            attempt { accountContextStore.write(persistedAccount) }
                .onSuccess {
                    mutableLocalAccountContext.value =
                        LocalAccountContext(profile, persistedAccount)
                }
            mutableState.value = ConnectionUiState.Linked(profile, context)
        } catch (_: SplClientException.AuthenticationRejected) {
            if (restoring || mutableLocalAccountContext.value != null) {
                mutableState.value =
                    ConnectionUiState.AuthenticationRequired(
                        profile,
                        "This connection is no longer authorized. Repair the connection."
                    )
            } else {
                mutableState.value =
                    ConnectionUiState.StoredCredentialProblem(
                        profile,
                        "Second Pass Library rejected this connection. Repair it to continue.",
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

    private suspend fun resetLocalAccount() {
        val localDataKey =
            mutableLocalAccountContext.value?.localDataKey()
                ?: run {
                    val profile = profileStore.read()
                    accountContextStore.read()
                        ?.takeIf { account -> profile != null && account.matches(profile) }
                        ?.let { account ->
                            AccountLocalDataKey.from(
                                checkNotNull(profile).serverOrigin,
                                account.profileId
                            )
                        }
                }
        localDataKey?.let { accountLocalDataCleaner.purge(it) }
        clearConnectionPersistence()
    }

    private suspend fun clearConnectionPersistence() {
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
            ConnectionUiState.AuthenticationRequired(
                profile = linked.profile,
                message = "This connection is no longer authorized. Repair it to continue."
            )
    }

    fun authenticatedRequestUnreachable() {
        val linked = mutableState.value as? ConnectionUiState.Linked ?: return
        operation?.cancel()
        mutableState.value =
            ConnectionUiState.RestoreProblem(
                linked.profile,
                "Couldn’t reach the Library. Check your connection and retry."
            )
    }
}

private fun SplClientException.isRecoverablePollingFailure(): Boolean =
    this is SplClientException.ServerUnreachable || this is SplClientException.PairingThrottled
