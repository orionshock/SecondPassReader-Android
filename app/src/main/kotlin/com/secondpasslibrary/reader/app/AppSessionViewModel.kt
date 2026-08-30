package com.secondpasslibrary.reader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.LocalAccountContext
import com.secondpasslibrary.reader.home.HomeProjectionRepository
import com.secondpasslibrary.reader.home.HomeRefreshAvailability
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciler
import com.secondpasslibrary.reader.reader.sync.ReaderOutboxSynchronizer
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectOrchestrator
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
    sessionReconciler: ReaderSessionReconciler,
    readerOutboxStore: ReaderOutboxStore,
    outboxSynchronizer: ReaderOutboxSynchronizer
) : ViewModel() {
    private val controller = AppSessionController(homeRepository, viewModelScope)
    private val connectionEventChannel = Channel<AppConnectionEvent>(Channel.BUFFERED)
    private val reconnect = ReaderReconnectOrchestrator(
        sessionReconciler,
        readerOutboxStore,
        outboxSynchronizer,
        viewModelScope
    ) {
        connectionEventChannel.trySend(AppConnectionEvent.AuthenticationRejected)
    }

    internal val state = controller.state
    internal val connectionEvents = connectionEventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            state.collect { current ->
                val shell = current as? AppSessionState.AccountShell
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
        reconnect.clear()
        connectionEventChannel.close()
    }
}

internal sealed interface AppConnectionEvent {
    data object AuthenticationRejected : AppConnectionEvent
}
