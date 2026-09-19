package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.ClientSessionRevocationClient
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.storage.AccountLocalDataLifecycle
import com.secondpasslibrary.reader.connection.pairing.ConnectionPairingController
import com.secondpasslibrary.reader.connection.pairing.PairingPollDelay
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // One cohesive, explicit connection state machine.
internal class ConnectionCoordinator(
    private val client: SecondPassClient,
    private val clientSessionRevocationClient: ClientSessionRevocationClient,
    private val persistence: ConnectionPersistence,
    private val accountLocalDataLifecycle: AccountLocalDataLifecycle,
    pollDelay: PairingPollDelay,
    private val defaultClientName: String,
    private val scope: CoroutineScope,
    private val connectionTarget: AuthenticatedConnectionTarget =
        client.asAuthenticatedConnectionTarget()
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
    private var reachabilityCheck: Job? = null
    private val pairing = ConnectionPairingController(
        client,
        pollDelay,
        scope,
        onState = { mutableState.value = it },
        onCredentialIssued = { server, issued ->
            replaceOperation { persistPairing(server, issued) }
        }
    )

    fun restore() = replaceOperation {
        mutableState.value = ConnectionUiState.Restoring
        attempt {
            persistence.restore { profile, persistedAccount ->
                mutableLocalAccountContext.value =
                    persistedAccount?.let { LocalAccountContext(profile, it) }
            }
        }.onSuccess { restored ->
            when (restored) {
                DurableConnectionRestore.None -> {
                    mutableLocalAccountContext.value = null
                    mutableState.value = ConnectionUiState.ServerEntry()
                }

                is DurableConnectionRestore.RecoveryRequired ->
                    mutableState.value = persistenceRecoveryState(restored.profile)

                is DurableConnectionRestore.Committed ->
                    verifyStored(
                        restored.connection.profile,
                        restored.connection.credential,
                        restoring = true
                    )
            }
        }.onFailure { failure ->
            if (failure is ConnectionProfileStorageException ||
                failure is ConnectionPersistenceClearException
            ) {
                mutableLocalAccountContext.value = null
            }
            mutableState.value =
                ConnectionUiState.LocalStorageProblem(ConnectionErrorPresenter.message(failure))
        }
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
        operation?.cancel()
        pairing.start(confirmed.server, clientName)
    }

    fun pairingForegrounded() = pairing.foregrounded()

    fun abandonPairing() {
        pairing.cancel()
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
            when (val restored = persistence.retryInterruptedCommit(recovery.profile)) {
                DurableConnectionRestore.None ->
                    mutableState.value =
                        ConnectionUiState.ServerEntry(
                            message =
                                "The connection can’t be recovered. Enter the Library address."
                        )

                is DurableConnectionRestore.RecoveryRequired ->
                    mutableState.value = persistenceRecoveryState(restored.profile)

                is DurableConnectionRestore.Committed ->
                    verifyStored(
                        restored.connection.profile,
                        restored.connection.credential,
                        restoring = false
                    )
            }
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
            val credential = persistence.readCredential()
            if (credential == null) {
                mutableState.value =
                    ConnectionUiState.ServerEntry(
                        message = "The saved connection is missing. Enter the Library address."
                    )
            } else {
                verifyStored(
                    profile,
                    credential,
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
            val credentialResult = attempt { persistence.readCredential() }
            val credential = credentialResult.getOrNull()
            val revokeFailure = when {
                credentialResult.isFailure -> credentialResult.exceptionOrNull()

                credential == null -> MissingLogoutCredentialException()

                else -> attempt {
                    clientSessionRevocationClient.revokeCurrentClientSession(
                        linked.profile.apiBaseUrl,
                        credential,
                        linked.profile.clientSessionId
                    )
                }.exceptionOrNull()
            }
            attempt { resetLocalAccount() }
                .onSuccess {
                    mutableLifecycleActionState.value = ConnectionLifecycleActionState.Idle
                    mutableState.value =
                        ConnectionUiState.ServerEntry(
                            message = logoutCompletionMessage(revokeFailure)
                        )
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

    fun close() {
        pairing.cancel()
        operation?.cancel()
        reachabilityCheck?.cancel()
    }

    private suspend fun persistPairing(
        server: com.secondpasslibrary.client.DiscoveredServer,
        issued: PairingConsumption.CredentialIssued
    ) {
        val profile = ConnectionProfile.linked(server, issued.clientSession)
        when (val committed = persistence.commit(profile, issued.credential)) {
            DurableConnectionCommit.Committed ->
                verifyStored(profile, issued.credential, restoring = false)

            DurableConnectionCommit.SecureCredentialFailed ->
                mutableState.value =
                    ConnectionUiState.TerminalPairingProblem(
                        "The connection was approved but couldn’t be saved securely. " +
                            "Start again."
                    )

            is DurableConnectionCommit.RecoveryRequired ->
                mutableState.value = persistenceRecoveryState(committed.profile)
        }
    }

    private suspend fun verifyStored(
        profile: ConnectionProfile,
        credential: BearerCredential,
        restoring: Boolean
    ) {
        try {
            val context = connectionTarget.loadContext(profile, credential)
            currentCoroutineContext().ensureActive()
            val persistedAccount = PersistedAccountContext(
                connectionIdentity = profile.authenticatedConnectionIdentity,
                profileId = context.currentUser.profileId,
                accountServerOrigin = profile.serverOrigin
            )
            val previousAccount = mutableLocalAccountContext.value?.persistedAccount
                ?: persistence.readAccountContext()
            currentCoroutineContext().ensureActive()
            if (previousAccount != null &&
                previousAccount.localDataScope() != persistedAccount.localDataScope()
            ) {
                val purgeResult = attempt {
                    accountLocalDataLifecycle.purge(previousAccount.localDataScope())
                    persistence.clearAccountContext()
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
            attempt { persistence.writeAccountContext(persistedAccount) }
                .onSuccess {
                    mutableLocalAccountContext.value =
                        LocalAccountContext(profile, persistedAccount)
                }
            mutableState.value = ConnectionUiState.Linked(profile, context)
        } catch (_: SplClientException.AuthenticationRejected) {
            currentCoroutineContext().ensureActive()
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
            currentCoroutineContext().ensureActive()
            val message = ConnectionErrorPresenter.message(failure)
            mutableState.value = if (restoring && failure is SplClientException.ServerUnreachable) {
                ConnectionUiState.RestoreProblem(profile, message)
            } else {
                ConnectionUiState.StoredCredentialProblem(profile, message, retryable = true)
            }
        }
    }

    private suspend fun resetLocalAccount() {
        val localDataScope =
            mutableLocalAccountContext.value?.persistedAccount?.localDataScope()
                ?: persistence.readLocalAccountScope()
        localDataScope?.let { accountLocalDataLifecycle.purge(it) }
        mutableLocalAccountContext.value = null
        persistence.clear()
    }

    private fun persistenceRecoveryState(profile: ConnectionProfile) =
        ConnectionUiState.PersistenceRecovery(
            profile,
            "The connection is secure but couldn’t be saved on this device. Retry."
        )

    private fun replaceOperation(block: suspend () -> Unit) {
        pairing.cancel()
        operation?.cancel()
        reachabilityCheck?.cancel()
        operation = scope.launch { block() }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        runSuspendCatching { block() }.also {
            currentCoroutineContext().ensureActive()
        }

    private companion object {
        const val MAX_CLIENT_NAME_LENGTH = 200
    }

    fun authenticatedRequestRejected() {
        reachabilityCheck?.cancel()
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
        if (reachabilityCheck?.isActive == true ||
            linked.reachability == InstallationReachability.UNREACHABLE
        ) {
            return
        }
        reachabilityCheck = scope.launch { verifyLinkedReachability(linked) }
    }

    fun retryReachabilityOrRestore() {
        val linked = mutableState.value as? ConnectionUiState.Linked
        if (linked?.reachability == InstallationReachability.UNREACHABLE) {
            reachabilityCheck?.cancel()
            reachabilityCheck = scope.launch { verifyLinkedReachability(linked) }
        } else {
            restore()
        }
    }

    fun retryIfUnreachable() {
        when (val current = mutableState.value) {
            is ConnectionUiState.Linked -> {
                if (current.reachability == InstallationReachability.UNREACHABLE) {
                    retryReachabilityOrRestore()
                }
            }

            is ConnectionUiState.RestoreProblem -> restore()

            else -> Unit
        }
    }

    private suspend fun verifyLinkedReachability(linked: ConnectionUiState.Linked) {
        val credential = attempt { persistence.readCredential() }.getOrNull() ?: return
        try {
            val context = connectionTarget.loadContext(linked.profile, credential)
            currentCoroutineContext().ensureActive()
            if (mutableState.value == linked) {
                mutableState.value = if (
                    context.currentUser.profileId == linked.context.currentUser.profileId
                ) {
                    linked.copy(
                        context = context,
                        reachability = InstallationReachability.REACHABLE
                    )
                } else {
                    ConnectionUiState.AuthenticationRequired(
                        linked.profile,
                        "This connection needs repair."
                    )
                }
            }
        } catch (_: SplClientException.ServerUnreachable) {
            currentCoroutineContext().ensureActive()
            if (mutableState.value == linked) {
                mutableState.value = linked.copy(
                    reachability = InstallationReachability.UNREACHABLE
                )
            }
        } catch (_: SplClientException.AuthenticationRejected) {
            currentCoroutineContext().ensureActive()
            if (mutableState.value == linked) authenticatedRequestRejected()
        } catch (_: SplClientException) {
            currentCoroutineContext().ensureActive()
            // A protocol or HTTP response failure does not prove lost reachability.
        }
    }
}

private class MissingLogoutCredentialException : Exception()

private fun logoutCompletionMessage(revokeFailure: Throwable?): String = when (revokeFailure) {
    null,
    is SplClientException.ClientSessionNotFound -> "This device was logged out."

    is SplClientException.ServerUnreachable ->
        "Logged out on this device. The Library couldn’t be reached."

    else -> "Logged out on this device. The Library may still list this device."
}
