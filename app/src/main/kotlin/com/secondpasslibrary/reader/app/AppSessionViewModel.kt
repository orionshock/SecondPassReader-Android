package com.secondpasslibrary.reader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.LocalAccountContext
import com.secondpasslibrary.reader.home.HomeProjectionRepository
import com.secondpasslibrary.reader.home.HomeRefreshAvailability
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectController
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectOrchestrator
import com.secondpasslibrary.reader.reader.sync.ReaderSyncScheduler
import com.secondpasslibrary.reader.reader.sync.ReaderSyncWakeupController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AppSessionViewModel
@Inject
internal constructor(
    homeRepository: HomeProjectionRepository,
    reconnectOrchestrator: ReaderReconnectOrchestrator,
    readerSyncScheduler: ReaderSyncScheduler
) : ViewModel() {
    private val controller = AppSessionController(homeRepository, viewModelScope)
    private val connectionEventChannel = Channel<AppConnectionEvent>(Channel.BUFFERED)
    private val reconnect = ReaderReconnectController(
        reconnectOrchestrator,
        viewModelScope,
        onAuthenticationRequired = {
            connectionEventChannel.trySend(AppConnectionEvent.AuthenticationRejected)
        }
    )
    private val readerSyncWakeup = ReaderSyncWakeupController(readerSyncScheduler, viewModelScope)

    internal val state = controller.state
    internal val connectionEvents = connectionEventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            state.collect { current ->
                val shell = current as? AppSessionState.AccountShell
                readerSyncWakeup.update(shell?.profile, shell?.profileId)
                reconnect.update(shell?.profile, shell?.profileId, shell?.availability)
            }
        }
    }

    internal fun updateConnection(
        connection: ConnectionUiState,
        localAccount: LocalAccountContext?
    ) = controller.updateConnection(connection, localAccount)

    internal fun updateHomeRefreshAvailability(availability: HomeRefreshAvailability) =
        controller.updateHomeRefreshAvailability(availability)

    override fun onCleared() {
        readerSyncWakeup.clear()
        reconnect.clear()
        connectionEventChannel.close()
    }
}

internal sealed interface AppConnectionEvent {
    data object AuthenticationRejected : AppConnectionEvent
}
