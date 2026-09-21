package com.secondpasslibrary.reader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.LocalAccountContext
import com.secondpasslibrary.reader.home.HomeProjectionRepository
import com.secondpasslibrary.reader.home.HomeRefreshAvailability
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import com.secondpasslibrary.reader.reader.persistence.ReaderContinuationOutcomeNoticeStore
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectController
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectOrchestrator
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
    readerSyncScheduler: ReaderPendingSyncScheduler,
    syncOutcomeNoticeStore: ReaderContinuationOutcomeNoticeStore
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
    private val syncOutcomeNotices =
        ReaderSyncOutcomeNoticeController(syncOutcomeNoticeStore, viewModelScope)

    internal val state = controller.state
    internal val connectionEvents = connectionEventChannel.receiveAsFlow()
    internal val syncOutcomeNotice = syncOutcomeNotices.notice

    init {
        viewModelScope.launch {
            state.collect { current ->
                val shell = current as? AppSessionState.AccountShell
                readerSyncWakeup.update(shell?.profile, shell?.profileId)
                reconnect.update(shell?.profile, shell?.profileId, shell?.availability)
                syncOutcomeNotices.update(
                    shell?.let {
                        LocalReaderAccountKey.from(it.profile.serverId, it.profileId)
                    }
                )
            }
        }
    }

    internal fun updateConnection(
        connection: ConnectionUiState,
        localAccount: LocalAccountContext?
    ) = controller.updateConnection(connection, localAccount)

    internal fun updateHomeRefreshAvailability(availability: HomeRefreshAvailability) =
        controller.updateHomeRefreshAvailability(availability)

    internal fun acknowledgeSyncOutcomeNotice(noticeId: Long) =
        syncOutcomeNotices.acknowledge(noticeId)

    override fun onCleared() {
        readerSyncWakeup.clear()
        reconnect.clear()
        syncOutcomeNotices.clear()
        connectionEventChannel.close()
    }
}

internal sealed interface AppConnectionEvent {
    data object AuthenticationRejected : AppConnectionEvent
}
