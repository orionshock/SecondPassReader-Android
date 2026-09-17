package com.secondpasslibrary.reader.shelves.management

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.shelves.ShelvesConnectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class DeletePersonalShelfController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(DeletePersonalShelfState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<ShelvesConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var deleteJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        this.profile = profile
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        reset()
    }

    fun begin(shelf: Shelf) {
        mutableState.value = DeletePersonalShelfState(shelf.id, shelf.name)
    }

    fun confirm(onDeleted: (String) -> Unit) {
        val activeProfile = profile
        val shelfId = state.value.shelfId
        if (activeProfile == null || shelfId == null || deleteJob?.isActive == true) return
        mutableState.value = state.value.copy(deleting = true, failure = null)
        deleteJob = coroutineScope.launch {
            val result = runSuspendCatching {
                clientProvider.forProfile(activeProfile).shelves.delete(shelfId)
            }
            result.fold(
                onSuccess = {
                    reset()
                    onDeleted(shelfId)
                },
                onFailure = ::applyFailure
            )
        }
    }

    fun reset() {
        deleteJob?.cancel()
        deleteJob = null
        mutableState.value = DeletePersonalShelfState()
    }

    fun close() = deleteJob?.cancel()

    private fun applyFailure(throwable: Throwable) {
        mutableState.value =
            state.value.copy(deleting = false, failure = throwable.toShelfManagementFailure())
        if (throwable is SplClientException.AuthenticationRejected) {
            connectionEventChannel.trySend(ShelvesConnectionEvent.AuthenticationRejected)
        }
    }
}
