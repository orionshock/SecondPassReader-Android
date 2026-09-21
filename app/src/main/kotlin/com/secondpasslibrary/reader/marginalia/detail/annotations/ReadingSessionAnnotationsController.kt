package com.secondpasslibrary.reader.marginalia.detail.annotations

import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.toMarginaliaFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class ReadingSessionAnnotationsController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ReadingSessionAnnotationsState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<MarginaliaConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedSessionIdentity? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedSessionIdentity
        this.profile = profile
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        clear()
    }

    fun select(sessionId: String) {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        if (profile == null) return
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReadingSessionAnnotationsState(sessionId = sessionId, loading = true)
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
        mutableState.value = ReadingSessionAnnotationsState()
    }

    fun close() = loadJob?.cancel()

    private fun load(sessionId: String, activeGeneration: Long) {
        val activeProfile = profile ?: return
        mutableState.value = state.value.copy(loading = true, failure = null)
        loadJob = coroutineScope.launch {
            val result = runSuspendCatching {
                clientProvider.forProfile(
                    activeProfile
                ).marginalia.sessions.listAnnotations(sessionId)
            }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { annotations ->
                    mutableState.value = ReadingSessionAnnotationsState(
                        sessionId = sessionId,
                        annotations = annotations,
                        loaded = true
                    )
                },
                onFailure = { throwable ->
                    val failure = throwable.toMarginaliaFailure()
                    mutableState.value = ReadingSessionAnnotationsState(
                        sessionId = sessionId,
                        failure = failure
                    )
                    if (failure == MarginaliaFailure.AUTHENTICATION_REJECTED) {
                        connectionEventChannel.trySend(
                            MarginaliaConnectionEvent.AuthenticationRejected
                        )
                    }
                }
            )
        }
    }
}
