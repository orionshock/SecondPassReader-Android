package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.client.MarginaliaAnnotationDraft
import com.secondpasslibrary.client.MarginaliaAnnotationLocationInput
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.MarginaliaHighlightBodyInput
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class ReaderAnnotationCreateState(
    val pending: ReaderPendingHighlight? = null,
    val submitting: Boolean = false,
    val failure: ReaderAnnotationCreateFailure? = null
)

internal data class ReaderPendingHighlight(
    val clientId: String,
    val selection: ReaderSelection,
    val color: ReaderAnnotationColor = ReaderAnnotationColor.YELLOW,
    val note: String = ""
)

internal enum class ReaderAnnotationCreateFailure {
    AUTHENTICATION_REQUIRED,
    SESSION_CLOSED,
    REJECTED,
    UNAVAILABLE
}

internal data class ReaderHighlightCreateRequest(
    val sessionId: String,
    val clientId: String,
    val selection: ReaderSelection,
    val color: ReaderAnnotationColor,
    val note: String
)

internal fun interface ReaderHighlightWriter {
    suspend fun create(
        profile: ConnectionProfile,
        request: ReaderHighlightCreateRequest
    ): List<ReaderAnnotation>
}

internal class SplReaderHighlightWriter @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider
) : ReaderHighlightWriter {
    override suspend fun create(
        profile: ConnectionProfile,
        request: ReaderHighlightCreateRequest
    ): List<ReaderAnnotation> {
        val selection = request.selection
        val operation = MarginaliaAnnotationOperation.Upsert(
            MarginaliaAnnotationDraft.Highlight(
                clientId = request.clientId,
                location = MarginaliaAnnotationLocationInput(
                    cfi = selection.cfi.value,
                    locationLabel = selection.locationLabel
                ),
                body = MarginaliaHighlightBodyInput(
                    text = selection.selectedText,
                    prefix = selection.prefix,
                    suffix = selection.suffix,
                    color = request.color.toSdkColor(),
                    note = request.note
                )
            )
        )
        return clientProvider.forProfile(profile)
            .marginalia.sessions.synchronizeAnnotations(request.sessionId, listOf(operation))
            .map { it.toReaderAnnotation() }
    }
}

/** Owns one online highlight-create attempt and its stable Session-scoped client ID. */
internal class ReaderAnnotationCreateController(
    private val writer: ReaderHighlightWriter,
    private val scope: CoroutineScope,
    private val onAuthoritativeAnnotations: (String, List<ReaderAnnotation>) -> Unit,
    private val clientIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val mutableState = MutableStateFlow(ReaderAnnotationCreateState())
    val state = mutableState.asStateFlow()
    private val authenticationRequired = Channel<Unit>(Channel.BUFFERED)
    val authenticationRequiredEvents = authenticationRequired.receiveAsFlow()
    private var owner: Owner? = null
    private var generation = 0L
    private var submitJob: Job? = null

    fun select(profile: ConnectionProfile, sessionId: String, status: ReaderSessionStatus) {
        val next = Owner(profile, sessionId, status)
        if (owner == next) return
        submitJob?.cancel()
        generation += 1
        owner = next
        mutableState.value = ReaderAnnotationCreateState()
    }

    fun begin(selection: ReaderSelection) {
        if (owner?.status != ReaderSessionStatus.ACTIVE || state.value.submitting) return
        if (state.value.pending?.selection?.cfi == selection.cfi) return
        mutableState.value = ReaderAnnotationCreateState(
            pending = ReaderPendingHighlight(
                clientId = clientIdFactory().also(::validateClientId),
                selection = selection
            )
        )
    }

    fun updateColor(color: ReaderAnnotationColor) = updatePending { copy(color = color) }

    fun updateNote(note: String) {
        if (note.length <= MAX_HIGHLIGHT_NOTE_LENGTH) updatePending { copy(note = note) }
    }

    fun submit() {
        val current = owner?.takeIf { it.status == ReaderSessionStatus.ACTIVE }
        val pending = state.value.pending
        if (!state.value.submitting && current != null && pending != null) {
            submit(current, pending, generation)
        }
    }

    fun dismiss() {
        submitJob?.cancel()
        generation += 1
        mutableState.value = ReaderAnnotationCreateState()
    }

    fun clear() {
        submitJob?.cancel()
        generation += 1
        owner = null
        mutableState.value = ReaderAnnotationCreateState()
    }

    private fun submit(owner: Owner, pending: ReaderPendingHighlight, activeGeneration: Long) {
        mutableState.value = ReaderAnnotationCreateState(pending = pending, submitting = true)
        submitJob = scope.launch {
            val result = runCatching {
                writer.create(
                    owner.profile,
                    ReaderHighlightCreateRequest(
                        sessionId = owner.sessionId,
                        clientId = pending.clientId,
                        selection = pending.selection,
                        color = pending.color,
                        note = pending.note
                    )
                )
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (
                generation != activeGeneration ||
                this@ReaderAnnotationCreateController.owner != owner
            ) {
                return@launch
            }
            result.fold(
                onSuccess = { annotations ->
                    onAuthoritativeAnnotations(owner.sessionId, annotations)
                    mutableState.value = ReaderAnnotationCreateState()
                },
                onFailure = { failure ->
                    val mapped = failure.toCreateFailure()
                    mutableState.value = ReaderAnnotationCreateState(
                        pending = pending,
                        failure = mapped
                    )
                    if (mapped == ReaderAnnotationCreateFailure.AUTHENTICATION_REQUIRED) {
                        authenticationRequired.trySend(Unit)
                    }
                }
            )
        }
    }

    private inline fun updatePending(
        transform: ReaderPendingHighlight.() -> ReaderPendingHighlight
    ) {
        val current = state.value
        if (current.submitting) return
        val pending = current.pending ?: return
        mutableState.value = ReaderAnnotationCreateState(pending = pending.transform())
    }

    private data class Owner(
        val profile: ConnectionProfile,
        val sessionId: String,
        val status: ReaderSessionStatus
    )
}

private fun validateClientId(value: String) {
    require(value.isNotBlank() && value.length <= MAX_CLIENT_ID_LENGTH) {
        "Invalid annotation client ID."
    }
}

private const val MAX_CLIENT_ID_LENGTH = 255

private fun Throwable.toCreateFailure(): ReaderAnnotationCreateFailure = when (this) {
    is SplClientException.AuthenticationRejected ->
        ReaderAnnotationCreateFailure.AUTHENTICATION_REQUIRED

    is SplClientException.ReadingSessionLifecycleRejected -> when (reason) {
        ReadingSessionLifecycleRejection.SESSION_CLOSED ->
            ReaderAnnotationCreateFailure.SESSION_CLOSED

        else -> ReaderAnnotationCreateFailure.REJECTED
    }

    else -> ReaderAnnotationCreateFailure.UNAVAILABLE
}

private fun ReaderAnnotationColor.toSdkColor(): MarginaliaHighlightColor = when (this) {
    ReaderAnnotationColor.YELLOW -> MarginaliaHighlightColor.YELLOW
    ReaderAnnotationColor.GREEN -> MarginaliaHighlightColor.GREEN
    ReaderAnnotationColor.BLUE -> MarginaliaHighlightColor.BLUE
    ReaderAnnotationColor.PINK -> MarginaliaHighlightColor.PINK
    ReaderAnnotationColor.PURPLE -> MarginaliaHighlightColor.PURPLE
    ReaderAnnotationColor.ORANGE -> MarginaliaHighlightColor.ORANGE
}
