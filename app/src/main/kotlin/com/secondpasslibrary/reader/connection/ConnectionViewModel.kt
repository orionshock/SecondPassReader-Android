package com.secondpasslibrary.reader.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel
@Inject
internal constructor(
    client: SecondPassClient,
    profileStore: ConnectionProfileStore,
    credentialStore: BearerCredentialStore,
    accountContextStore: PersistedAccountContextStore,
    accountLocalDataCleaner: AccountLocalDataCleaner,
    pollDelay: CoroutinePairingPollDelay,
    clientNameProvider: AndroidClientNameProvider
) : ViewModel() {
    private val coordinator =
        ConnectionCoordinator(
            client = client,
            profileStore = profileStore,
            credentialStore = credentialStore,
            accountContextStore = accountContextStore,
            accountLocalDataCleaner = accountLocalDataCleaner,
            pollDelay = pollDelay,
            defaultClientName = clientNameProvider.defaultName(),
            scope = viewModelScope
        )

    val state = coordinator.state
    internal val localAccountContext = coordinator.localAccountContext
    val onAuthenticatedRequestRejected: () -> Unit = coordinator::authenticatedRequestRejected
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

    init {
        coordinator.restore()
    }

    fun pairingForegrounded() = coordinator.pairingForegrounded()

    fun retryRestore() = coordinator.restore()

    override fun onCleared() {
        coordinator.close()
    }
}
