package com.secondpasslibrary.reader.reader.annotations.mutation

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.location.ReaderSavedLocationLabelPolicy
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Validates and durably commits annotation intent for the current Reader Session. */
@Suppress("TooManyFunctions") // Each mutation intent keeps its own bounded transition handler.
internal class ReaderAnnotationMutationController(
    private val scope: CoroutineScope,
    private val localStore: LocalReaderStateStore,
    private val onAnnotationsChanged: (String, List<ReaderAnnotation>) -> Unit,
    private val onSyncRequested: () -> Unit = {},
    private val clientIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val mutableState = MutableStateFlow(ReaderAnnotationMutationState())
    val state = mutableState.asStateFlow()
    private var owner: Owner? = null
    private var generation = 0L
    private var submitJob: Job? = null

    fun select(profile: ConnectionProfile, profileId: String, session: ReaderSessionContext) {
        val next = Owner(profile, profileId, session)
        if (owner == next) return
        submitJob?.cancel()
        generation += 1
        owner = next
        mutableState.value = ReaderAnnotationMutationState()
    }

    fun select(profile: ConnectionProfile, sessionId: String, status: ReaderSessionStatus) {
        select(
            profile,
            profile.clientSessionId,
            ReaderSessionContext(sessionId, status, null)
        )
    }

    fun accept(intent: ReaderAnnotationMutationIntent) {
        when (intent) {
            is ReaderAnnotationMutationIntent.BeginCreate,
            is ReaderAnnotationMutationIntent.UpdateCreate,
            is ReaderAnnotationMutationIntent.SubmitQuickCreate,
            ReaderAnnotationMutationIntent.OpenCreateNote,
            ReaderAnnotationMutationIntent.CancelCreateNote,
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
            is ReaderAnnotationMutationIntent.BeginCreate ->
                state.value
                    .beginCreate(intent, canMutate) {
                        clientIdFactory().also(::validateClientId)
                    }?.let { mutableState.value = it }

            is ReaderAnnotationMutationIntent.UpdateCreate -> updateStateDraft { current ->
                current.copy(
                    pendingCreate = current.pendingCreate?.updated(intent.color, intent.note),
                    failure = null
                )
            }

            is ReaderAnnotationMutationIntent.SubmitQuickCreate -> submitQuickCreate(intent)

            ReaderAnnotationMutationIntent.OpenCreateNote -> updateStateDraft { current ->
                current.copy(
                    createNoteEditorVisible = current.pendingCreate != null,
                    failure = null
                )
            }

            ReaderAnnotationMutationIntent.CancelCreateNote -> updateStateDraft { current ->
                current.copy(
                    createNoteEditorVisible = false,
                    failure = null
                )
            }

            ReaderAnnotationMutationIntent.SubmitCreate -> {
                val currentOwner = activeOwner
                val pending = state.value.pendingCreate
                if (currentOwner != null && pending != null) {
                    pending.toRequest(currentOwner.localSessionId)?.let { submit(currentOwner, it) }
                }
            }

            else -> Unit
        }
    }

    private fun submitQuickCreate(intent: ReaderAnnotationMutationIntent.SubmitQuickCreate) {
        val currentOwner = activeOwner
        val pending = state.value.pendingCreate?.copy(color = intent.color, note = "")
        if (currentOwner != null && pending != null && !state.value.submitting) {
            mutableState.value = state.value.copy(
                pendingCreate = pending,
                createNoteEditorVisible = false,
                failure = null
            )
            pending.toRequest(currentOwner.localSessionId)?.let { submit(currentOwner, it) }
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
                        draft.toRequest(currentOwner.localSessionId)?.let {
                            submit(currentOwner, it)
                        }
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
                    locationLabel = ReaderSavedLocationLabelPolicy.create(
                        intent.position.totalProgression,
                        intent.position.sectionLabel,
                        intent.position.chapterOrdinal
                    )
                )
                mutableState.value = ReaderAnnotationMutationState(pendingBookmark = pending)
                submit(currentOwner, pending.toRequest(currentOwner.localSessionId))
            }

            ReaderAnnotationMutationIntent.RetryBookmark -> state.value.pendingBookmark?.let {
                submit(currentOwner, it.toRequest(currentOwner.localSessionId))
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
                            currentOwner.localSessionId,
                            annotation.clientId,
                            annotation
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
            val result = runSuspendCatching {
                val account = LocalReaderAccountKey.from(
                    owner.profile.serverId,
                    owner.profileId
                )
                localStore.applyAnnotationMutation(
                    account,
                    owner.localSessionId,
                    request
                ).also { onSyncRequested() }
            }
            if (generation != activeGeneration ||
                this@ReaderAnnotationMutationController.owner != owner
            ) {
                return@launch
            }
            result.fold(
                onSuccess = { annotations ->
                    onAnnotationsChanged(owner.localSessionId, annotations)
                    mutableState.value = ReaderAnnotationMutationState()
                },
                onFailure = { publishFailure(submittedState) }
            )
        }
    }

    private fun publishFailure(submittedState: ReaderAnnotationMutationState) {
        mutableState.value = submittedState.copy(
            submitting = false,
            failure = ReaderAnnotationMutationFailure.LOCAL_PERSISTENCE
        )
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
        val profileId: String,
        val session: ReaderSessionContext
    ) {
        val localSessionId: String get() = session.sessionId
        val status: ReaderSessionStatus get() = session.status
    }
}

private fun ReaderAnnotationMutationState.beginCreate(
    intent: ReaderAnnotationMutationIntent.BeginCreate,
    canMutate: Boolean,
    clientIdFactory: () -> String
): ReaderAnnotationMutationState? {
    if (!canMutate || submitting || pendingCreate?.selection?.cfi == intent.selection.cfi) {
        return null
    }
    return ReaderAnnotationMutationState(
        pendingCreate = ReaderPendingHighlight(clientIdFactory(), intent.selection)
    )
}
