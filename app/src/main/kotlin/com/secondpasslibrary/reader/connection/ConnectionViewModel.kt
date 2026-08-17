package com.secondpasslibrary.reader.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.SecondPassClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel
@Inject
constructor(
    client: SecondPassClient,
    profileStore: ConnectionProfileStore,
    credentialStore: BearerCredentialStore,
    pollDelay: CoroutinePairingPollDelay,
    clientNameProvider: AndroidClientNameProvider
) : ViewModel() {
    private val coordinator =
        ConnectionCoordinator(
            client = client,
            profileStore = profileStore,
            credentialStore = credentialStore,
            pollDelay = pollDelay,
            defaultClientName = clientNameProvider.defaultName(),
            scope = viewModelScope
        )

    val state = coordinator.state

    init {
        coordinator.restore()
    }

    fun updateServerUrl(value: String) = coordinator.updateServerUrl(value)

    fun verifyServer() = coordinator.verifyServer()

    fun updateClientName(value: String) = coordinator.updateClientName(value)

    fun beginPairing() = coordinator.beginPairing()

    fun resumePolling() = coordinator.resumePolling()

    fun abandonPairing() = coordinator.abandonPairing()

    fun retryProfilePersistence() = coordinator.retryProfilePersistence()

    fun retryStoredVerification() = coordinator.retryStoredVerification()

    fun retryRestore() = coordinator.restore()

    fun forgetLocalConnection() = coordinator.forgetLocalConnection()

    override fun onCleared() {
        coordinator.close()
    }
}
