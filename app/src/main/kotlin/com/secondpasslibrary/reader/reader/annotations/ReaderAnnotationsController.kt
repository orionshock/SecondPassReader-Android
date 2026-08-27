package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class ReaderAnnotationsState(
    val sessionId: String? = null,
    val annotations: List<ReaderAnnotation> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failure: ReaderAnnotationsFailure? = null
)

internal enum class ReaderAnnotationsFailure {
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE
}

internal class ReaderAnnotationsController(
    private val loader: ReaderAnnotationsLoader,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ReaderAnnotationsState())
    val state = mutableState.asStateFlow()

    private val authenticationRequired = Channel<Unit>(Channel.BUFFERED)
    val authenticationRequiredEvents = authenticationRequired.receiveAsFlow()

    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var profile: ConnectionProfile? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun select(profile: ConnectionProfile, sessionId: String) {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        val nextIdentity = profile.authenticatedConnectionIdentity
        if (state.value.sessionId == sessionId && connectionIdentity == nextIdentity) return
        this.profile = profile
        connectionIdentity = nextIdentity
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReaderAnnotationsState(sessionId = sessionId, loading = true)
        load(sessionId, generation)
    }

    fun retry() {
        val sessionId = state.value.sessionId ?: return
        if (state.value.failure == null) return
        load(sessionId, generation)
    }

    /** Reconciles a successful batch response without issuing a redundant collection GET. */
    fun replaceAuthoritative(sessionId: String, annotations: List<ReaderAnnotation>) {
        if (state.value.sessionId != sessionId) return
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReaderAnnotationsState(
            sessionId = sessionId,
            annotations = annotations,
            loaded = true
        )
    }

    fun clear() {
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReaderAnnotationsState()
    }

    fun close() {
        clear()
        authenticationRequired.close()
    }

    private fun load(sessionId: String, activeGeneration: Long) {
        val activeProfile = profile ?: return
        mutableState.value = state.value.copy(loading = true, failure = null)
        loadJob = scope.launch {
            val result = runCatching { loader.load(activeProfile, sessionId) }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { annotations ->
                    mutableState.value = ReaderAnnotationsState(
                        sessionId = sessionId,
                        annotations = annotations,
                        loaded = true
                    )
                },
                onFailure = { failure -> publishFailure(sessionId, failure) }
            )
        }
    }

    private fun publishFailure(sessionId: String, failure: Throwable) {
        val kind = if (failure is SplClientException.AuthenticationRejected) {
            ReaderAnnotationsFailure.AUTHENTICATION_REQUIRED
        } else {
            ReaderAnnotationsFailure.UNAVAILABLE
        }
        mutableState.value = state.value.copy(
            sessionId = sessionId,
            loading = false,
            failure = kind
        )
        if (kind == ReaderAnnotationsFailure.AUTHENTICATION_REQUIRED) {
            authenticationRequired.trySend(Unit)
        }
    }
}
