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
    pollDelay: CoroutinePairingPollDelay,
    clientNameProvider: AndroidClientNameProvider
) : ViewModel() {
    private val coordinator =
        ConnectionCoordinator(
            client = client,
            profileStore = profileStore,
            credentialStore = credentialStore,
            accountContextStore = accountContextStore,
            pollDelay = pollDelay,
            defaultClientName = clientNameProvider.defaultName(),
            scope = viewModelScope
        )

    val state = coordinator.state
    internal val localAccountContext = coordinator.localAccountContext
    val onAuthenticatedRequestRejected: () -> Unit = coordinator::authenticatedRequestRejected

    init {
        coordinator.restore()
    }

    fun updateServerUrl(value: String) = coordinator.updateServerUrl(value)

    fun verifyServer() = coordinator.verifyServer()

    fun updateClientName(value: String) = coordinator.updateClientName(value)

    fun beginPairing() = coordinator.beginPairing()

    fun pairingForegrounded() = coordinator.pairingForegrounded()

    fun abandonPairing() = coordinator.abandonPairing()

    fun retryProfilePersistence() = coordinator.retryProfilePersistence()

    fun retryStoredVerification() = coordinator.retryStoredVerification()

    fun retryRestore() = coordinator.restore()

    fun forgetLocalConnection() = coordinator.forgetLocalConnection()

    override fun onCleared() {
        coordinator.close()
    }
}
