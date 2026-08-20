package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionMetadataInput
import com.secondpasslibrary.client.ReadingSessionMutationField
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

internal class ReadingSessionMetadataEditorController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ReadingSessionMetadataEditState())
    val state = mutableState.asStateFlow()
    private val connectionChannel = Channel<MarginaliaConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionChannel.receiveAsFlow()
    private var profile: ConnectionProfile? = null
    private var identity: String? = null
    private var job: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextIdentity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        this.profile = profile
        if (nextIdentity == identity) return
        identity = nextIdentity
        reset()
    }

    fun begin(detail: ReadingSessionDetailResult) {
        if (!detail.session.summary.status.isActive) return
        val summary = detail.session.summary
        mutableState.value = ReadingSessionMetadataEditState(
            open = true,
            sessionId = summary.id,
            name = summary.name,
            notes = summary.notes,
            originalName = summary.name,
            originalNotes = summary.notes
        )
    }

    fun updateName(value: String) {
        mutableState.value = state.value.copy(name = value, nameError = null, failure = null)
    }

    fun updateNotes(value: String) {
        mutableState.value = state.value.copy(notes = value, failure = null)
    }

    fun submit(onUpdated: (ReadingSessionDetailResult) -> Unit) {
        createRequest()?.let { request ->
            mutableState.value = state.value.copy(saving = true, failure = null)
            job = coroutineScope.launch {
                val result = runCatching {
                    clientProvider.forProfile(request.profile).marginalia.sessions.updateMetadata(
                        request.sessionId,
                        request.input
                    )
                }
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                result.fold(
                    onSuccess = {
                        reset()
                        onUpdated(it)
                    },
                    onFailure = ::applyFailure
                )
            }
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        mutableState.value = ReadingSessionMetadataEditState()
    }

    fun close() = job?.cancel()

    private fun applyFailure(throwable: Throwable) {
        val rejection = throwable as? SplClientException.ReadingSessionLifecycleRejected
        mutableState.value = state.value.copy(
            saving = false,
            nameError = ReadingSessionNameError.SERVER_REJECTED.takeIf {
                rejection?.fields?.contains(ReadingSessionMutationField.NAME) == true
            },
            failure = throwable.toReadingSessionMutationFailure()
        )
        if (throwable is SplClientException.AuthenticationRejected) {
            connectionChannel.trySend(MarginaliaConnectionEvent.AuthenticationRejected)
        }
    }

    private fun createRequest(): MetadataEditRequest? {
        val current = state.value
        return when {
            profile == null || current.sessionId == null -> null

            !current.open || !current.dirty || job?.isActive == true -> null

            current.name.length > MAX_READING_SESSION_NAME_LENGTH -> {
                mutableState.value = current.copy(nameError = ReadingSessionNameError.TOO_LONG)
                null
            }

            else -> MetadataEditRequest(
                checkNotNull(profile),
                checkNotNull(current.sessionId),
                ReadingSessionMetadataInput(current.name, current.notes)
            )
        }
    }
}

private val com.secondpasslibrary.client.ReadingSessionStatus.isActive: Boolean
    get() = this == com.secondpasslibrary.client.ReadingSessionStatus.ACTIVE

internal const val MAX_READING_SESSION_NAME_LENGTH = 255

private data class MetadataEditRequest(
    val profile: ConnectionProfile,
    val sessionId: String,
    val input: ReadingSessionMetadataInput
)
