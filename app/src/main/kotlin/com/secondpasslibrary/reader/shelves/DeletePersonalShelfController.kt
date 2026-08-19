package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CancellationException
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
    private var connectionIdentity: String? = null
    private var deleteJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        this.profile = profile
        if (identity == connectionIdentity) return
        connectionIdentity = identity
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
            val result = runCatching {
                clientProvider.forProfile(activeProfile).shelves.delete(shelfId)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
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
