package com.secondpasslibrary.reader.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.ClientSessionRevocationClient
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.reader.app.storage.AccountLocalDataLifecycle
import com.secondpasslibrary.reader.connection.pairing.CoroutinePairingPollDelay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel
@Inject
internal constructor(
    client: SecondPassClient,
    clientSessionRevocationClient: ClientSessionRevocationClient,
    persistence: ConnectionPersistence,
    accountLocalDataLifecycle: AccountLocalDataLifecycle,
    pollDelay: CoroutinePairingPollDelay,
    clientNameProvider: AndroidClientNameProvider
) : ViewModel() {
    private val coordinator =
        ConnectionCoordinator(
            client = client,
            clientSessionRevocationClient = clientSessionRevocationClient,
            persistence = persistence,
            accountLocalDataLifecycle = accountLocalDataLifecycle,
            pollDelay = pollDelay,
            defaultClientName = clientNameProvider.defaultName(),
            scope = viewModelScope
        )

    val state = coordinator.state
    internal val localAccountContext = coordinator.localAccountContext
    internal val lifecycleActionState = coordinator.lifecycleActionState
    val onAuthenticatedRequestRejected: () -> Unit = coordinator::authenticatedRequestRejected
    val onAuthenticatedRequestUnreachable: () -> Unit =
        coordinator::authenticatedRequestUnreachable
    internal val screenActions =
        ConnectionScreenActions(
            updateServerUrl = coordinator::updateServerUrl,
            verifyServer = coordinator::verifyServer,
            updateClientName = coordinator::updateClientName,
            beginPairing = coordinator::beginPairing,
            relinkLocalAccount = coordinator::relinkLocalAccount,
            abandonPairing = coordinator::abandonPairing,
            retryProfilePersistence = coordinator::retryProfilePersistence,
            retryStoredVerification = coordinator::retryStoredVerification,
            retryRestore = coordinator::restore,
            forgetLocalConnection = coordinator::forgetLocalConnection
        )
    internal val lifecycleActions =
        ConnectionLifecycleActions(
            reconnect = coordinator::relinkLocalAccount,
            retryConnection = coordinator::restore,
            logout = coordinator::logout,
            forget = coordinator::forgetLocalConnection
        )

    init {
        coordinator.restore()
    }

    fun pairingForegrounded() = coordinator.pairingForegrounded()

    fun retryRestore() = coordinator.restore()

    override fun onCleared() {
        coordinator.close()
    }
}
