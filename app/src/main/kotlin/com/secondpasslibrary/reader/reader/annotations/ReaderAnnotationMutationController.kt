package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Owns bounded online create/edit/delete attempts for the current Reader Session. */
internal class ReaderAnnotationMutationController(
    private val writer: ReaderAnnotationWriter,
    private val scope: CoroutineScope,
    private val onAuthoritativeAnnotations: (String, List<ReaderAnnotation>) -> Unit,
    private val clientIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val mutableState = MutableStateFlow(ReaderAnnotationMutationState())
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
        mutableState.value = ReaderAnnotationMutationState()
    }

    fun accept(intent: ReaderAnnotationMutationIntent) {
        when (intent) {
            is ReaderAnnotationMutationIntent.BeginCreate,
            is ReaderAnnotationMutationIntent.UpdateCreate,
            ReaderAnnotationMutationIntent.SubmitCreate -> acceptCreate(intent)

            is ReaderAnnotationMutationIntent.BeginEdit,
            is ReaderAnnotationMutationIntent.UpdateEdit,
            ReaderAnnotationMutationIntent.SaveEdit -> acceptEdit(intent)

            is ReaderAnnotationMutationIntent.CreateBookmark,
            ReaderAnnotationMutationIntent.RetryBookmark -> acceptBookmark(intent)

            is ReaderAnnotationMutationIntent.RequestDelete,
            ReaderAnnotationMutationIntent.ConfirmDelete -> acceptDelete(intent)

            ReaderAnnotationMutationIntent.DismissTransient -> {
                if (!state.value.submitting) {
                    mutableState.value = state.value.copy(
                        editing = null,
                        deleting = null,
                        failure = null
                    )
                }
            }

            ReaderAnnotationMutationIntent.DismissCreate -> {
                submitJob?.cancel()
                generation += 1
                mutableState.value = ReaderAnnotationMutationState()
            }
        }
    }

    private fun acceptCreate(intent: ReaderAnnotationMutationIntent) {
        when (intent) {
            is ReaderAnnotationMutationIntent.BeginCreate -> if (
                canMutate && !state.value.submitting &&
                state.value.pendingCreate?.selection?.cfi != intent.selection.cfi
            ) {
                mutableState.value = ReaderAnnotationMutationState(
                    pendingCreate = ReaderPendingHighlight(
                        clientIdFactory().also(::validateClientId),
                        intent.selection
                    )
                )
            }

            is ReaderAnnotationMutationIntent.UpdateCreate -> updateStateDraft { current ->
                current.copy(
                    pendingCreate = current.pendingCreate?.updated(intent.color, intent.note),
                    failure = null
                )
            }

            ReaderAnnotationMutationIntent.SubmitCreate -> {
                val currentOwner = activeOwner
                val pending = state.value.pendingCreate
                if (currentOwner != null && pending != null) {
                    submit(currentOwner, pending.toRequest(currentOwner.sessionId))
                }
            }

            else -> Unit
        }
    }

    private fun acceptEdit(intent: ReaderAnnotationMutationIntent) {
        when (intent) {
            is ReaderAnnotationMutationIntent.BeginEdit -> if (
                canMutate &&
                !state.value.submitting
            ) {
                mutableState.value = ReaderAnnotationMutationState(
                    editing = ReaderHighlightEditDraft(intent.annotation)
                )
            }

            is ReaderAnnotationMutationIntent.UpdateEdit -> updateStateDraft { current ->
                current.copy(
                    editing = current.editing?.updated(intent.color, intent.note),
                    failure = null
                )
            }

            ReaderAnnotationMutationIntent.SaveEdit -> {
                val currentOwner = activeOwner
                val draft = state.value.editing
                if (currentOwner != null && draft != null) {
                    if (draft.unchanged) {
                        mutableState.value = ReaderAnnotationMutationState()
                    } else {
                        submit(currentOwner, draft.toRequest(currentOwner.sessionId))
                    }
                }
            }

            else -> Unit
        }
    }

    private fun acceptBookmark(intent: ReaderAnnotationMutationIntent) {
        val currentOwner = activeOwner ?: return
        when (intent) {
            is ReaderAnnotationMutationIntent.CreateBookmark -> if (!state.value.submitting) {
                val pending = ReaderPendingBookmark(
                    clientId = clientIdFactory().also(::validateClientId),
                    position = intent.position,
                    locationLabel = readerLocationLabel(
                        intent.position.chapterOrdinal,
                        intent.position.totalProgression
                    )
                )
                mutableState.value = ReaderAnnotationMutationState(pendingBookmark = pending)
                submit(currentOwner, pending.toRequest(currentOwner.sessionId))
            }

            ReaderAnnotationMutationIntent.RetryBookmark -> state.value.pendingBookmark?.let {
                submit(currentOwner, it.toRequest(currentOwner.sessionId))
            }

            else -> Unit
        }
    }

    private fun acceptDelete(intent: ReaderAnnotationMutationIntent) {
        when (intent) {
            is ReaderAnnotationMutationIntent.RequestDelete -> if (
                canMutate && !state.value.submitting
            ) {
                mutableState.value = ReaderAnnotationMutationState(deleting = intent.annotation)
            }

            ReaderAnnotationMutationIntent.ConfirmDelete -> {
                val currentOwner = activeOwner
                val annotation = state.value.deleting
                if (currentOwner != null && annotation != null) {
                    submit(
                        currentOwner,
                        ReaderAnnotationMutationRequest.Delete(
                            currentOwner.sessionId,
                            annotation.clientId
                        )
                    )
                }
            }

            else -> Unit
        }
    }

    fun clear() {
        submitJob?.cancel()
        generation += 1
        owner = null
        mutableState.value = ReaderAnnotationMutationState()
    }

    private fun submit(owner: Owner, request: ReaderAnnotationMutationRequest) {
        if (state.value.submitting) return
        val activeGeneration = generation
        val submittedState = state.value.copy(submitting = true, failure = null)
        mutableState.value = submittedState
        submitJob = scope.launch {
            val result = runCatching { writer.synchronize(owner.profile, request) }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (generation != activeGeneration ||
                this@ReaderAnnotationMutationController.owner != owner
            ) {
                return@launch
            }
            result.fold(
                onSuccess = { annotations ->
                    onAuthoritativeAnnotations(owner.sessionId, annotations)
                    mutableState.value = ReaderAnnotationMutationState()
                },
                onFailure = { failure -> publishFailure(submittedState, failure) }
            )
        }
    }

    private fun publishFailure(submittedState: ReaderAnnotationMutationState, failure: Throwable) {
        val mapped = failure.toMutationFailure()
        mutableState.value = submittedState.copy(submitting = false, failure = mapped)
        if (mapped == ReaderAnnotationMutationFailure.AUTHENTICATION_REQUIRED) {
            authenticationRequired.trySend(Unit)
        }
    }

    private inline fun updateStateDraft(
        transform: (ReaderAnnotationMutationState) -> ReaderAnnotationMutationState
    ) {
        if (state.value.submitting) return
        mutableState.value = transform(state.value)
    }

    private val canMutate: Boolean
        get() = owner?.status == ReaderSessionStatus.ACTIVE

    private val activeOwner: Owner?
        get() = owner?.takeIf { it.status == ReaderSessionStatus.ACTIVE }

    private data class Owner(
        val profile: ConnectionProfile,
        val sessionId: String,
        val status: ReaderSessionStatus
    )
}

private fun ReaderPendingHighlight.updated(color: ReaderAnnotationColor?, note: String?) = copy(
    color = color ?: this.color,
    note = note.validNoteOr(this.note)
)

private fun ReaderHighlightEditDraft.updated(color: ReaderAnnotationColor?, note: String?) = copy(
    color = color ?: this.color,
    note = note.validNoteOr(this.note)
)

private fun String?.validNoteOr(fallback: String) =
    if (this != null && length <= MAX_HIGHLIGHT_NOTE_LENGTH) this else fallback

private fun ReaderPendingHighlight.toRequest(sessionId: String) =
    ReaderAnnotationMutationRequest.UpsertHighlight(
        sessionId = sessionId,
        clientId = clientId,
        cfi = selection.cfi.value,
        locationLabel = selection.locationLabel,
        text = selection.selectedText,
        prefix = selection.prefix,
        suffix = selection.suffix,
        color = color,
        note = note
    )

private fun ReaderHighlightEditDraft.toRequest(sessionId: String) =
    ReaderAnnotationMutationRequest.UpsertHighlight(
        sessionId = sessionId,
        clientId = annotation.clientId,
        cfi = annotation.cfi,
        locationLabel = annotation.locationLabel,
        text = annotation.quote,
        prefix = annotation.prefix,
        suffix = annotation.suffix,
        color = color,
        note = note
    )

private fun ReaderPendingBookmark.toRequest(sessionId: String) =
    ReaderAnnotationMutationRequest.UpsertBookmark(
        sessionId = sessionId,
        clientId = clientId,
        cfi = position.cfi.value,
        locationLabel = locationLabel
    )

private fun validateClientId(value: String) {
    require(value.isNotBlank() && value.length <= MAX_CLIENT_ID_LENGTH) {
        "Invalid annotation client ID."
    }
}

private fun Throwable.toMutationFailure(): ReaderAnnotationMutationFailure = when (this) {
    is SplClientException.AuthenticationRejected ->
        ReaderAnnotationMutationFailure.AUTHENTICATION_REQUIRED

    is SplClientException.ReadingSessionLifecycleRejected -> when (reason) {
        ReadingSessionLifecycleRejection.SESSION_CLOSED ->
            ReaderAnnotationMutationFailure.SESSION_CLOSED

        else -> ReaderAnnotationMutationFailure.REJECTED
    }

    else -> ReaderAnnotationMutationFailure.UNAVAILABLE
}

private const val MAX_CLIENT_ID_LENGTH = 255
