package com.secondpasslibrary.reader.marginalia

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

internal class ReadingSessionDetailController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ReadingSessionDetailState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<MarginaliaConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: String? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        this.profile = profile
        if (identity == connectionIdentity) return
        connectionIdentity = identity
        clear()
    }

    fun select(sessionId: String) {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        if (profile == null) return
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReadingSessionDetailState(sessionId = sessionId, loading = true)
        load(sessionId, generation)
    }

    fun retry() {
        val sessionId = state.value.sessionId ?: return
        if (state.value.failure == null) return
        load(sessionId, generation)
    }

    fun clear() {
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReadingSessionDetailState()
    }

    fun close() = loadJob?.cancel()

    private fun load(sessionId: String, activeGeneration: Long) {
        val activeProfile = profile ?: return
        mutableState.value = state.value.copy(loading = true, failure = null)
        loadJob = coroutineScope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).marginalia.sessions.get(sessionId)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { detail ->
                    mutableState.value =
                        ReadingSessionDetailState(sessionId = sessionId, detail = detail)
                },
                onFailure = { failure ->
                    val classified = failure.toMarginaliaFailure()
                    mutableState.value =
                        ReadingSessionDetailState(sessionId = sessionId, failure = classified)
                    if (classified == MarginaliaFailure.AUTHENTICATION_REJECTED) {
                        connectionEventChannel.trySend(
                            MarginaliaConnectionEvent.AuthenticationRejected
                        )
                    }
                }
            )
        }
    }
}
