package com.secondpasslibrary.reader.marginalia.detail.close

import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionFinalization
import com.secondpasslibrary.client.ReadingSessionMutationField
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.detail.MAX_READING_SESSION_NAME_LENGTH
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionMutationFailure
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionNameError
import com.secondpasslibrary.reader.marginalia.detail.toReadingSessionMutationFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class ReadingSessionCloseController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ReadingSessionCloseState())
    val state = mutableState.asStateFlow()
    private val connectionChannel = Channel<MarginaliaConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionChannel.receiveAsFlow()
    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedSessionIdentity? = null
    private var job: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedSessionIdentity
        this.profile = profile
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        reset()
    }

    fun begin(detail: ReadingSessionDetailResult) {
        val summary = detail.session.summary
        if (summary.status != ReadingSessionStatus.ACTIVE) return
        mutableState.value = ReadingSessionCloseState(
            open = true,
            sessionId = summary.id,
            name = summary.name,
            notes = summary.notes
        )
    }

    fun updateName(value: String) {
        if (state.value.exactRetryRequired) return
        mutableState.value = state.value.copy(name = value, nameError = null, failure = null)
    }

    fun updateNotes(value: String) {
        if (state.value.exactRetryRequired) return
        mutableState.value = state.value.copy(notes = value, failure = null)
    }

    fun submit(onClosed: (ReadingSessionDetailResult) -> Unit) {
        createRequest()?.let { request ->
            mutableState.value = state.value.copy(closing = true, failure = null)
            job = coroutineScope.launch {
                val result = runSuspendCatching {
                    clientProvider.forProfile(request.profile).marginalia.sessions.close(
                        request.sessionId,
                        request.finalization
                    )
                }
                result.fold(
                    onSuccess = {
                        reset()
                        onClosed(it)
                    },
                    onFailure = { applyFailure(it, request.finalization) }
                )
            }
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        mutableState.value = ReadingSessionCloseState()
    }

    fun close() = job?.cancel()

    private fun applyFailure(throwable: Throwable, payload: ReadingSessionFinalization) {
        val rejection = throwable as? SplClientException.ReadingSessionLifecycleRejected
        val failure = throwable.toReadingSessionMutationFailure()
        mutableState.value = state.value.copy(
            closing = false,
            nameError = ReadingSessionNameError.SERVER_REJECTED.takeIf {
                rejection?.fields?.contains(ReadingSessionMutationField.NAME) == true
            },
            failure = failure,
            retryFinalization = payload.takeIf {
                failure == ReadingSessionMutationFailure.UNREACHABLE
            }
        )
        if (throwable is SplClientException.AuthenticationRejected) {
            connectionChannel.trySend(MarginaliaConnectionEvent.AuthenticationRejected)
        }
    }

    private fun createRequest(): CloseRequest? {
        val current = state.value
        return when {
            profile == null || current.sessionId == null -> null

            !current.open || job?.isActive == true -> null

            current.name.length > MAX_READING_SESSION_NAME_LENGTH -> {
                mutableState.value = current.copy(nameError = ReadingSessionNameError.TOO_LONG)
                null
            }

            else -> CloseRequest(
                checkNotNull(profile),
                checkNotNull(current.sessionId),
                current.retryFinalization ?: ReadingSessionFinalization(
                    name = current.name,
                    notes = current.notes
                )
            )
        }
    }
}

private data class CloseRequest(
    val profile: ConnectionProfile,
    val sessionId: String,
    val finalization: ReadingSessionFinalization
)
