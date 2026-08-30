package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.client.ReadingSessionMetadataInput
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class ReaderSessionMetadata(
    val sessionId: String,
    val name: String,
    val notes: String
)

internal data class ReaderSessionMetadataState(
    val metadata: ReaderSessionMetadata? = null,
    val editorOpen: Boolean = false,
    val draftName: String = "",
    val draftNotes: String = "",
    val saving: Boolean = false,
    val nameTooLong: Boolean = false,
    val failure: Boolean = false
) {
    val dirty: Boolean
        get() = metadata?.let { draftName != it.name || draftNotes != it.notes } == true
}

internal fun interface ReaderSessionMetadataWriter {
    suspend fun update(
        profile: ConnectionProfile,
        sessionId: String,
        name: String,
        notes: String
    ): ReaderSessionMetadata
}

internal class SplReaderSessionMetadataWriter @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider
) : ReaderSessionMetadataWriter {
    override suspend fun update(
        profile: ConnectionProfile,
        sessionId: String,
        name: String,
        notes: String
    ): ReaderSessionMetadata {
        val result = clientProvider.forProfile(profile).marginalia.sessions.updateMetadata(
            sessionId,
            ReadingSessionMetadataInput(name, notes)
        )
        return result.session.summary.let { ReaderSessionMetadata(it.id, it.name, it.notes) }
    }
}

internal class ReaderSessionMetadataController(
    private val writer: ReaderSessionMetadataWriter,
    private val scope: CoroutineScope,
    private val onUpdated: (ReaderSessionMetadata) -> Unit = {}
) {
    private val mutableState = MutableStateFlow(ReaderSessionMetadataState())
    val state = mutableState.asStateFlow()
    private val authenticationRequired = Channel<Unit>(Channel.BUFFERED)
    val authenticationRequiredEvents = authenticationRequired.receiveAsFlow()
    private var profile: ConnectionProfile? = null
    private var serverSessionId: String? = null
    private var writable = false
    private var job: Job? = null

    fun select(profile: ConnectionProfile, session: ReaderSessionContext) {
        val current = state.value.metadata
        this.profile = profile
        serverSessionId = session.serverSessionId
        writable = session.status == ReaderSessionStatus.ACTIVE && serverSessionId != null
        if (current?.sessionId != session.sessionId) {
            job?.cancel()
            mutableState.value = ReaderSessionMetadataState(
                metadata = ReaderSessionMetadata(
                    session.sessionId,
                    session.sessionName.orEmpty(),
                    session.sessionNotes
                )
            )
        }
    }

    fun beginEdit() {
        val metadata = state.value.metadata ?: return
        if (!writable) return
        mutableState.value = state.value.copy(
            editorOpen = true,
            draftName = metadata.name,
            draftNotes = metadata.notes,
            nameTooLong = false,
            failure = false
        )
    }

    fun updateName(name: String) {
        mutableState.value = state.value.copy(
            draftName = name,
            nameTooLong = false,
            failure = false
        )
    }

    fun updateNotes(notes: String) {
        mutableState.value = state.value.copy(draftNotes = notes, failure = false)
    }

    fun submit() {
        val request = createRequest() ?: return
        mutableState.value = state.value.copy(saving = true, failure = false)
        job = scope.launch {
            val result = runCatching {
                writer.update(
                    request.profile,
                    request.serverSessionId,
                    request.name,
                    request.notes
                )
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = { updated ->
                    val localUpdated = updated.copy(sessionId = request.localSessionId)
                    mutableState.value = ReaderSessionMetadataState(metadata = localUpdated)
                    onUpdated(localUpdated)
                },
                onFailure = { failure ->
                    mutableState.value = state.value.copy(saving = false, failure = true)
                    if (failure is SplClientException.AuthenticationRejected) {
                        authenticationRequired.trySend(Unit)
                    }
                }
            )
        }
    }

    private fun createRequest(): ReaderSessionMetadataRequest? {
        val current = state.value
        val activeProfile = profile
        val activeServerSessionId = serverSessionId
        val metadata = current.metadata
        val valid = writable && current.editorOpen && current.dirty && job?.isActive != true
        return when {
            activeProfile == null || activeServerSessionId == null ||
                metadata == null || !valid -> null

            current.draftName.length > MAX_SESSION_NAME_LENGTH -> {
                mutableState.value = current.copy(nameTooLong = true)
                null
            }

            else -> ReaderSessionMetadataRequest(
                activeProfile,
                metadata.sessionId,
                activeServerSessionId,
                current.draftName,
                current.draftNotes
            )
        }
    }

    fun dismissEditor() {
        if (state.value.saving) return
        val metadata = state.value.metadata
        mutableState.value = ReaderSessionMetadataState(metadata = metadata)
    }

    fun clear() {
        job?.cancel()
        job = null
        profile = null
        serverSessionId = null
        writable = false
        mutableState.value = ReaderSessionMetadataState()
    }
}

internal const val MAX_SESSION_NAME_LENGTH = 255

private data class ReaderSessionMetadataRequest(
    val profile: ConnectionProfile,
    val localSessionId: String,
    val serverSessionId: String,
    val name: String,
    val notes: String
)
