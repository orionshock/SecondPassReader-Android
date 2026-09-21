package com.secondpasslibrary.reader.reader.presentation

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderSessionAuthority
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsController
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderBookmarkHudIntent
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarksController
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationController
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderHighlightActivationController
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationController
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.captureReaderBookmarkPosition
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionController
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerDecorationController
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersController
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPolicyController
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPreferenceStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataController
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataWriter
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the screen's marginalia graph; launch, reconciliation and engine ownership stay outside.
 * Wiring, command routing and teardown stay together so this topology has one lifetime owner.
 * Child controllers retain annotation, layer, selection and mutation rules.
 */
internal class ReaderMarginaliaPresentationController(
    private val readerState: StateFlow<ReaderState>,
    parentScope: CoroutineScope,
    annotationsLoader: ReaderAnnotationsLoader,
    marginaliaLayerHistoryLoader: ReaderMarginaliaLayerHistoryLoader,
    marginaliaLayerPreferenceStore: ReaderMarginaliaLayerPreferenceStore,
    marginaliaLayerVisibilityStore: ReaderMarginaliaLayerVisibilityStore,
    sessionMetadataWriter: ReaderSessionMetadataWriter,
    private val localReaderStateStore: LocalReaderStateStore,
    onSyncRequested: () -> Unit
) {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private var closed = false
    private var activeProfile: ConnectionProfile? = null
    private var entryIdentity: Entry? = null
    private var serverWritesAvailable = true
    private var bookmarkCaptureJob: Job? = null
    private val annotationsController = ReaderAnnotationsController(
        annotationsLoader,
        scope,
        localReaderStateStore
    )
    private val marginaliaLayersController = ReaderMarginaliaLayersController(
        marginaliaLayerHistoryLoader,
        annotationsLoader,
        scope
    )
    private val marginaliaLayerDecorations = ReaderMarginaliaLayerDecorationController()
    private val marginaliaLayerPolicy = ReaderMarginaliaLayerPolicyController(
        marginaliaLayerPreferenceStore,
        marginaliaLayerVisibilityStore,
        marginaliaLayersController,
        scope
    )
    private val annotationDecorations = ReaderAnnotationDecorationController()
    private val visiblePageBookmarks = ReaderVisiblePageBookmarksController(scope)
    private val selections = ReaderSelectionController(scope)
    private val sessionMetadata = ReaderSessionMetadataController(
        sessionMetadataWriter,
        scope,
        marginaliaLayersController::updateCurrentSessionMetadata
    )
    private val annotationMutations = ReaderAnnotationMutationController(
        scope = scope,
        localStore = localReaderStateStore,
        onAnnotationsChanged = { sessionId, annotations ->
            annotationsController.replaceProjection(sessionId, annotations)
            selections.dismiss()
        },
        onSyncRequested = onSyncRequested
    )
    private val highlightActivations = ReaderHighlightActivationController(scope) {
        annotationMutations.accept(ReaderAnnotationMutationIntent.BeginEdit(it))
    }

    val state = marginaliaPresentationState(
        scope, annotationsController, marginaliaLayersController, marginaliaLayerPolicy,
        visiblePageBookmarks, selections, annotationMutations, highlightActivations, sessionMetadata
    )
    private val authenticationRequired = Channel<Unit>(Channel.BUFFERED)
    val authenticationRequiredEvents = authenticationRequired.receiveAsFlow()

    init {
        scope.launch {
            merge(
                annotationsController.authenticationRequiredEvents,
                marginaliaLayersController.authenticationRequiredEvents,
                sessionMetadata.authenticationRequiredEvents
            ).collect { authenticationRequired.send(it) }
        }
        scope.launch {
            readerState.collect { readerState ->
                val ready = readerState as? ReaderState.Ready
                if (ready == null) {
                    selections.detach()
                    annotationsController.clear()
                    marginaliaLayersController.clear()
                    annotationMutations.clear()
                    visiblePageBookmarks.clear()
                } else {
                    val session = ready.session
                    selections.attach(ready.engine.selectionEvents, ready.engine.cfiNavigator)
                    activeProfile?.let { profile ->
                        val entry = entryIdentity ?: return@let
                        annotationsController.select(
                            profile,
                            entry.profileId,
                            session,
                            ready.authority
                        )
                        if (ready.authority == ReaderSessionAuthority.LOCAL) {
                            marginaliaLayersController.selectLocal(session)
                        } else {
                            marginaliaLayersController.select(profile, entry.bookId, session)
                            marginaliaLayerPolicy.select(
                                profile.authenticatedConnectionIdentity(entry.profileId),
                                entry.bookId
                            )
                        }
                        annotationMutations.select(
                            profile,
                            entry.profileId,
                            session
                        )
                        if (ready.authority == ReaderSessionAuthority.SERVER) {
                            sessionMetadata.select(profile, session)
                        }
                    }
                }
            }
        }
        scope.launch {
            combine(
                readerState,
                annotationsController.state,
                marginaliaLayersController.state
            ) { reader, annotations, layers -> Triple(reader, annotations, layers) }
                .collectLatest { (reader, annotations, layers) ->
                    val ready = reader as? ReaderState.Ready
                    val currentAnnotationValues = annotations.annotations.takeIf {
                        ready != null && annotations.sessionId == ready.session.sessionId
                    }.orEmpty()
                    highlightActivations.select(ready?.engine?.annotationDecorations)
                    highlightActivations.replaceContext(
                        ready?.session,
                        currentAnnotationValues,
                        layers.previousLayers
                    )
                    if (ready != null) {
                        val session = ready.session
                        visiblePageBookmarks.select(
                            session.sessionId,
                            ready.engine.visiblePageBookmarks
                        )
                        if (annotations.sessionId == session.sessionId) {
                            visiblePageBookmarks.replace(
                                session.sessionId,
                                annotations.annotations
                            )
                            annotationDecorations.replace(
                                sessionId = session.sessionId,
                                target = ready.engine.annotationDecorations,
                                annotations = annotations.annotations
                            )
                        } else {
                            visiblePageBookmarks.replace(session.sessionId, emptyList())
                            annotationDecorations.clear()
                        }
                    } else {
                        visiblePageBookmarks.clear()
                        annotationDecorations.clear()
                    }
                }
        }
        scope.launch {
            combine(readerState, marginaliaLayersController.state) { reader, layers ->
                reader to layers
            }.collectLatest { (reader, layers) ->
                val ready = reader as? ReaderState.Ready
                if (ready != null &&
                    layers.currentLayer?.sessionId == ready.session.sessionId
                ) {
                    val session = ready.session
                    marginaliaLayerDecorations.replace(
                        readerSessionId = session.sessionId,
                        target = ready.engine.annotationDecorations,
                        layers = layers.previousLayers
                    )
                } else {
                    marginaliaLayerDecorations.clear()
                }
            }
        }
        scope.launch {
            selections.selection.collect { selection ->
                if (selection == null) {
                    annotationMutations.accept(ReaderAnnotationMutationIntent.DismissCreate)
                } else {
                    annotationMutations.accept(
                        ReaderAnnotationMutationIntent.BeginCreate(selection)
                    )
                }
            }
        }
    }

    fun selectEntry(
        profile: ConnectionProfile,
        profileId: String,
        bookId: String,
        existingSessionId: String?
    ) {
        if (closed) return
        val next =
            Entry(profile.authenticatedSessionIdentity, profileId, bookId, existingSessionId)
        if (entryIdentity != next) {
            annotationsController.clear()
            marginaliaLayersController.clear()
        }
        entryIdentity = next
        activeProfile = profile
    }

    fun refreshLocalAnnotations() {
        if (closed) return
        val sessionId = (readerState.value as? ReaderState.Ready)?.let { it.session.sessionId }
        val profile = activeProfile
        val entry = entryIdentity
        if (sessionId == null || profile == null || entry == null) return
        scope.launch {
            val account = LocalReaderAccountKey.from(profile.serverId, entry.profileId)
            val annotations = localReaderStateStore.readAnnotations(account, sessionId)
            annotationsController.replaceProjection(sessionId, annotations)
        }
    }

    fun acceptMarginalia(intent: ReaderMarginaliaIntent) {
        if (closed || (!serverWritesAvailable && intent.isServerMutation())) return
        if (sessionMetadata.accept(intent)) return
        routeMarginalia(intent)
    }

    private fun routeMarginalia(intent: ReaderMarginaliaIntent) {
        when (intent) {
            ReaderMarginaliaIntent.RetryCurrentAnnotations -> annotationsController.retry()

            is ReaderMarginaliaIntent.LoadPreviousLayer ->
                marginaliaLayersController.loadLayer(intent.sessionId)

            is ReaderMarginaliaIntent.SetPreviousLayerVisible ->
                marginaliaLayerPolicy.setVisible(intent.sessionId, intent.visible)

            ReaderMarginaliaIntent.ShowAllPreviousLayers ->
                marginaliaLayerPolicy.setAllVisible(true)

            ReaderMarginaliaIntent.HideAllPreviousLayers ->
                marginaliaLayerPolicy.setAllVisible(false)

            is ReaderMarginaliaIntent.SetAutoShowPrevious ->
                marginaliaLayerPolicy.setAutoShowPrevious(intent.enabled)

            ReaderMarginaliaIntent.LoadMoreLayers -> marginaliaLayersController.loadMore()

            ReaderMarginaliaIntent.RetryLayerHistory -> marginaliaLayersController.retry()

            ReaderMarginaliaIntent.DismissCurrentSessionMetadataEditor ->
                sessionMetadata.dismissEditor()

            ReaderMarginaliaIntent.EditCurrentSessionMetadata,
            is ReaderMarginaliaIntent.ChangeCurrentSessionName,
            is ReaderMarginaliaIntent.ChangeCurrentSessionNotes,
            ReaderMarginaliaIntent.SaveCurrentSessionMetadata -> Unit
        }
    }

    fun mutateAnnotation(intent: ReaderAnnotationMutationIntent) {
        if (closed) return
        if (readerState.value !is ReaderState.Ready) return
        annotationMutations.accept(intent)
    }

    fun acceptBookmark(intent: ReaderBookmarkHudIntent) {
        val ready = readerState.value as? ReaderState.Ready
        if (closed || ready == null) return
        val session = ready.session
        when (intent) {
            ReaderBookmarkHudIntent.Create -> when {
                session.status != ReaderSessionStatus.ACTIVE -> Unit

                annotationMutations.state.value.pendingBookmark != null ->
                    annotationMutations.accept(ReaderAnnotationMutationIntent.RetryBookmark)

                bookmarkCaptureJob?.isActive == true -> Unit

                else -> bookmarkCaptureJob = scope.launch {
                    val position = captureReaderBookmarkPosition(ready.engine.cfiNavigator)
                    if (readerState.value === ready && position is EpubCfiOutcome.Success) {
                        annotationMutations.accept(
                            ReaderAnnotationMutationIntent.CreateBookmark(position.value)
                        )
                    }
                }
            }

            is ReaderBookmarkHudIntent.Remove ->
                annotationMutations.accept(
                    ReaderAnnotationMutationIntent.RequestDelete(intent.bookmark)
                )
        }
    }

    fun dismissSelection() {
        if (closed) return
        annotationMutations.accept(ReaderAnnotationMutationIntent.DismissCreate)
        selections.dismiss()
    }

    fun dismissHighlightDetail() {
        if (!closed) highlightActivations.dismiss()
    }

    fun setAvailability(availability: AppAvailability) {
        if (closed) return
        serverWritesAvailable = availability !is AppAvailability.Offline
        marginaliaLayerPolicy.setAuthorityAvailable(serverWritesAvailable)
    }

    suspend fun close() {
        if (closed) return
        closed = true
        lifetime.cancel()
        authenticationRequired.close()
        annotationsController.close()
        visiblePageBookmarks.clear()
        marginaliaLayerPolicy.clear()
        marginaliaLayersController.clear()
        selections.detach()
        annotationMutations.clear()
        sessionMetadata.clear()
        highlightActivations.clear()
        withContext(NonCancellable) {
            annotationDecorations.clear()
            marginaliaLayerDecorations.clear()
        }
    }

    private data class Entry(
        val connectionIdentity: AuthenticatedSessionIdentity,
        val profileId: String,
        val bookId: String,
        val existingSessionId: String?
    )
}

private fun ReaderMarginaliaIntent.isServerMutation(): Boolean = when (this) {
    ReaderMarginaliaIntent.EditCurrentSessionMetadata,
    is ReaderMarginaliaIntent.ChangeCurrentSessionName,
    is ReaderMarginaliaIntent.ChangeCurrentSessionNotes,
    ReaderMarginaliaIntent.SaveCurrentSessionMetadata -> true

    else -> false
}

private fun ReaderSessionMetadataController.accept(intent: ReaderMarginaliaIntent): Boolean =
    when (intent) {
        ReaderMarginaliaIntent.EditCurrentSessionMetadata -> true.also { beginEdit() }

        is ReaderMarginaliaIntent.ChangeCurrentSessionName -> true.also { updateName(intent.name) }

        is ReaderMarginaliaIntent.ChangeCurrentSessionNotes -> true.also {
            updateNotes(intent.notes)
        }

        ReaderMarginaliaIntent.SaveCurrentSessionMetadata -> true.also { submit() }

        else -> false
    }
