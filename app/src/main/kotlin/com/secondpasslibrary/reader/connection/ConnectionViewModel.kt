package com.secondpasslibrary.reader.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.ClientSessionRevocationClient
import com.secondpasslibrary.client.KtorSecondPassClient
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.reader.app.storage.AccountLocalDataLifecycle
import com.secondpasslibrary.reader.connection.discovery.ConnectionLanDiscoveryController
import com.secondpasslibrary.reader.connection.discovery.LanLibraryUrlDiscovery
import com.secondpasslibrary.reader.connection.pairing.CoroutinePairingPollDelay
import com.secondpasslibrary.reader.connection.storage.WorkOfflineStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectionViewModel
@Inject
internal constructor(
    client: SecondPassClient,
    clientSessionRevocationClient: ClientSessionRevocationClient,
    persistence: ConnectionPersistence,
    workOfflineStore: WorkOfflineStore,
    accountLocalDataLifecycle: AccountLocalDataLifecycle,
    pollDelay: CoroutinePairingPollDelay,
    clientNameProvider: AndroidClientNameProvider,
    lanLibraryUrlDiscovery: LanLibraryUrlDiscovery,
    transport: KtorSecondPassClient,
    networkChanges: AndroidNetworkChangeAdapter
) : ViewModel() {
    private val coordinator =
        ConnectionCoordinator(
            client = client,
            clientSessionRevocationClient = clientSessionRevocationClient,
            persistence = persistence,
            workOfflineStore = workOfflineStore,
            accountLocalDataLifecycle = accountLocalDataLifecycle,
            pollDelay = pollDelay,
            defaultClientName = clientNameProvider.defaultName(),
            scope = viewModelScope
        )
    private val lanDiscovery =
        ConnectionLanDiscoveryController(lanLibraryUrlDiscovery, client, viewModelScope)
    private val lanDiscoveryEnabled = MutableStateFlow(false)

    val state =
        combine(coordinator.state, lanDiscovery.suggestions) { connection, suggestions ->
            if (connection is ConnectionUiState.ServerEntry) {
                connection.copy(suggestions = suggestions)
            } else {
                connection
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, coordinator.state.value)
    internal val localAccountContext = coordinator.localAccountContext
    internal val lifecycleActionState = coordinator.lifecycleActionState
    val onAuthenticatedRequestRejected: () -> Unit = coordinator::authenticatedRequestRejected
    val onAuthenticatedRequestUnreachable: () -> Unit =
        coordinator::authenticatedRequestUnreachable
    internal val screenActions =
        ConnectionScreenActions(
            updateServerUrl = coordinator::updateServerUrl,
            selectSuggestedServer = coordinator::updateServerUrl,
            verifyServer = coordinator::verifyServer,
            updateClientName = coordinator::updateClientName,
            beginPairing = coordinator::beginPairing,
            relinkLocalAccount = coordinator::relinkLocalAccount,
            abandonPairing = coordinator::abandonPairing,
            retryProfilePersistence = coordinator::retryProfilePersistence,
            retryStoredVerification = coordinator::retryStoredVerification,
            retryRestore = coordinator::retryReachabilityOrRestore,
            forgetLocalConnection = coordinator::forgetLocalConnection
        )
    internal val lifecycleActions =
        ConnectionLifecycleActions(
            reconnect = coordinator::relinkLocalAccount,
            retryConnection = coordinator::retryReachabilityOrRestore,
            logout = coordinator::logout,
            forget = coordinator::forgetLocalConnection
        )

    init {
        viewModelScope.launch {
            networkChanges.changes.collect { available ->
                if (available) {
                    coordinator.retryIfUnreachable()
                } else {
                    coordinator.authenticatedRequestUnreachable()
                }
            }
        }
        viewModelScope.launch {
            transport.authenticatedAccessFailures.collect { failure ->
                val linked = coordinator.state.value as? ConnectionUiState.Linked
                if (linked?.profile?.apiBaseUrl == failure.apiBaseUrl) {
                    coordinator.authenticatedRequestUnreachable()
                }
            }
        }
        viewModelScope.launch {
            combine(
                coordinator.state.map {
                    it is ConnectionUiState.ServerEntry
                }.distinctUntilChanged(),
                lanDiscoveryEnabled
            ) { onServerEntry, enabled -> onServerEntry && enabled }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) lanDiscovery.start() else lanDiscovery.stop()
                }
        }
        coordinator.restore()
    }

    fun setLanDiscoveryEnabled(enabled: Boolean) {
        lanDiscoveryEnabled.value = enabled
    }

    fun pairingForegrounded() = coordinator.pairingForegrounded()

    fun retryUnreachableOnForeground() = coordinator.retryIfUnreachable()

    fun retryRestore() = coordinator.restore()

    fun workOffline() = coordinator.workOffline()

    fun reconnect() {
        viewModelScope.launch { coordinator.checkConnectionNow() }
    }

    internal suspend fun checkConnectionNow(): Boolean = coordinator.checkConnectionNow()

    override fun onCleared() {
        lanDiscovery.stop()
        coordinator.close()
    }
}
