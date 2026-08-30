package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsController
import com.secondpasslibrary.reader.reader.annotations.SplReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderBookmarkHudIntent
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarksController
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationController
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderHighlightActivationController
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationController
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.SplReaderAnnotationWriter
import com.secondpasslibrary.reader.reader.annotations.mutation.captureReaderBookmarkPosition
import com.secondpasslibrary.reader.reader.annotations.navigateToReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionController
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.asset.SplReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerDecorationController
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersController
import com.secondpasslibrary.reader.reader.marginalia.SplReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPolicyController
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPreferenceStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.progress.SplReaderProgressWriter
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataController
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciler
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationController
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.session.SplReaderSessionCoordinator
import com.secondpasslibrary.reader.reader.session.SplReaderSessionMetadataWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    assetResolver: SplReaderBookAssetResolver,
    launchPolicy: ReaderLaunchPolicy,
    engineOpener: ReaderEngineOpener,
    sessionCoordinator: SplReaderSessionCoordinator,
    progressWriter: SplReaderProgressWriter,
    appearanceStore: ReaderAppearanceStore,
    annotationsLoader: SplReaderAnnotationsLoader,
    annotationWriter: SplReaderAnnotationWriter,
    marginaliaLayerHistoryLoader: SplReaderMarginaliaLayerHistoryLoader,
    marginaliaLayerPreferenceStore: ReaderMarginaliaLayerPreferenceStore,
    marginaliaLayerVisibilityStore: ReaderMarginaliaLayerVisibilityStore,
    sessionMetadataWriter: SplReaderSessionMetadataWriter,
    private val sessionReconciler: ReaderSessionReconciler,
    localReaderStateStore: LocalReaderStateStore
) : ViewModel() {
    private val progressSyncJob = SupervisorJob()
    private val progressSyncScope = CoroutineScope(progressSyncJob + Dispatchers.IO)
    private val controller =
        ReaderController(
            assetResolver,
            engineOpener,
            sessionCoordinator,
            progressWriter,
            viewModelScope,
            appearanceStore,
            progressSyncScope,
            launchPolicy,
            localReaderStateStore
        )
    private val annotationsController = ReaderAnnotationsController(
        annotationsLoader,
        viewModelScope,
        localReaderStateStore
    )
    private val marginaliaLayersController = ReaderMarginaliaLayersController(
        marginaliaLayerHistoryLoader,
        annotationsLoader,
        viewModelScope
    )
    private val marginaliaLayerDecorations = ReaderMarginaliaLayerDecorationController()
    private val marginaliaLayerPolicy = ReaderMarginaliaLayerPolicyController(
        marginaliaLayerPreferenceStore,
        marginaliaLayerVisibilityStore,
        marginaliaLayersController,
        viewModelScope
    )
    private val annotationDecorations = ReaderAnnotationDecorationController()
    private val visiblePageBookmarks = ReaderVisiblePageBookmarksController(viewModelScope)
    private val selections = ReaderSelectionController(viewModelScope)
    private val sessionMetadata = ReaderSessionMetadataController(
        sessionMetadataWriter,
        viewModelScope,
        marginaliaLayersController::updateCurrentSessionMetadata
    )
    private val annotationMutations = ReaderAnnotationMutationController(
        writer = annotationWriter,
        scope = viewModelScope,
        onAuthoritativeAnnotations = { sessionId, annotations ->
            annotationsController.replaceAuthoritative(sessionId, annotations)
            selections.dismiss()
        },
        localStore = localReaderStateStore
    )
    private val highlightActivations = ReaderHighlightActivationController(viewModelScope) {
        annotationMutations.accept(ReaderAnnotationMutationIntent.BeginEdit(it))
    }
    private val reconciliationEvents = Channel<ReaderConnectionEvent>(Channel.BUFFERED)
    private val sessionReconciliation = ReaderSessionReconciliationController(
        sessionReconciler,
        viewModelScope,
        controller::acceptReconciledSession,
        onAuthenticationRejected = {
            reconciliationEvents.trySend(ReaderConnectionEvent.AuthenticationRejected)
        }
    )
    private var exitJob: Job? = null
    private var bookmarkCaptureJob: Job? = null
    private var activeProfile: ConnectionProfile? = null
    private var entryIdentity: ReaderEntryIdentity? = null
    private var serverWritesAvailable = true

    val state = controller.state
    val progress = controller.progress
    val progressSync = controller.progressSync
    val annotations = annotationsController.state
    val pageBookmarks = visiblePageBookmarks.state
    val marginaliaLayers = marginaliaLayersController.state
    val autoShowPreviousMarginalia = marginaliaLayerPolicy.autoShowPrevious
    val selection = selections.selection
    val annotationMutationState = annotationMutations.state
    val highlightDetail = highlightActivations.detail
    val dismissHighlightDetail = highlightActivations::dismiss
    val sessionMetadataState = sessionMetadata.state
    val connectionEvents = merge(
        controller.connectionEvents,
        annotationsController.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        },
        annotationMutations.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        },
        marginaliaLayersController.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        },
        sessionMetadata.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        },
        reconciliationEvents.receiveAsFlow()
    )

    init {
        viewModelScope.launch {
            controller.state.collect { readerState ->
                val ready = readerState as? ReaderState.Ready
                val session = ready?.session
                if (ready == null || session == null) {
                    sessionReconciliation.clear()
                    selections.detach()
                    annotationsController.clear()
                    marginaliaLayersController.clear()
                    annotationMutations.clear()
                    visiblePageBookmarks.clear()
                } else {
                    selections.attach(ready.engine.selectionEvents, ready.engine.cfiNavigator)
                    activeProfile?.let { profile ->
                        val entry = entryIdentity ?: return@let
                        annotationsController.select(
                            profile,
                            entry.profileId,
                            session,
                            ready.localOnly
                        )
                        if (ready.localOnly) {
                            marginaliaLayersController.selectLocal(session)
                        } else {
                            marginaliaLayersController.select(profile, entry.bookId, session)
                            marginaliaLayerPolicy.select(
                                profile.authenticatedConnectionIdentity,
                                entry.bookId
                            )
                        }
                        annotationMutations.select(
                            profile,
                            entry.profileId,
                            session,
                            serverWritesAvailable
                        )
                        if (!ready.localOnly) sessionMetadata.select(profile, session)
                        sessionReconciliation.select(
                            profile,
                            entry.profileId,
                            entry.bookId,
                            session,
                            ready.localOnly
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                controller.state,
                annotationsController.state,
                marginaliaLayersController.state
            ) { reader, annotations, layers -> Triple(reader, annotations, layers) }
                .collectLatest { (reader, annotations, layers) ->
                    val ready = reader as? ReaderState.Ready
                    val currentAnnotationValues = annotations.annotations.takeIf {
                        annotations.sessionId == ready?.session?.sessionId
                    }.orEmpty()
                    highlightActivations.select(ready?.engine?.annotationDecorations)
                    highlightActivations.replaceContext(
                        ready?.session,
                        currentAnnotationValues,
                        layers.previousLayers
                    )
                    val session = ready?.session
                    if (ready != null && session != null) {
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
        viewModelScope.launch {
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

    fun initialize(
        profile: ConnectionProfile,
        profileId: String,
        bookId: String,
        existingSessionId: String?,
        titleHint: String?,
        availability: AppAvailability
    ) {
        val nextIdentity = ReaderEntryIdentity(
            profile.authenticatedConnectionIdentity,
            profileId,
            bookId,
            existingSessionId
        )
        if (entryIdentity != nextIdentity) {
            annotationsController.clear()
            marginaliaLayersController.clear()
        }
        viewModelScope.launch {
            combine(controller.state, marginaliaLayersController.state) { reader, layers ->
                reader to layers
            }.collectLatest { (reader, layers) ->
                val ready = reader as? ReaderState.Ready
                val session = ready?.session
                if (ready != null && session != null &&
                    layers.currentLayer?.sessionId == session.sessionId
                ) {
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
        entryIdentity = nextIdentity
        activeProfile = profile
        controller.initialize(
            profile,
            profileId,
            bookId,
            existingSessionId,
            titleHint,
            availability
        )
    }

    fun retry(availability: AppAvailability? = null) = controller.retry(availability)

    fun updateAppearance(appearance: ReaderAppearance) = controller.updateAppearance(appearance)

    fun acceptMarginalia(intent: ReaderMarginaliaIntent) {
        if (!serverWritesAvailable && intent.isServerMutation()) return
        if (sessionMetadata.accept(intent)) return
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
        if (controller.state.value !is ReaderState.Ready) return
        annotationMutations.accept(intent)
    }

    fun acceptBookmark(intent: ReaderBookmarkHudIntent) {
        val ready = controller.state.value as? ReaderState.Ready
        if (ready == null) return
        val session = ready.session ?: return
        when (intent) {
            ReaderBookmarkHudIntent.Create -> when {
                session.status != ReaderSessionStatus.ACTIVE -> Unit

                annotationMutationState.value.pendingBookmark != null ->
                    annotationMutations.accept(ReaderAnnotationMutationIntent.RetryBookmark)

                bookmarkCaptureJob?.isActive == true -> Unit

                else -> bookmarkCaptureJob = viewModelScope.launch {
                    val position = captureReaderBookmarkPosition(ready.engine.cfiNavigator)
                    if (controller.state.value === ready && position is EpubCfiOutcome.Success) {
                        annotationMutations.accept(
                            ReaderAnnotationMutationIntent.CreateBookmark(position.value)
                        )
                    }
                }
            }

            is ReaderBookmarkHudIntent.Navigate -> viewModelScope.launch {
                navigateToReaderAnnotation(intent.bookmark, ready.engine.cfiNavigator)
            }

            is ReaderBookmarkHudIntent.Remove ->
                annotationMutations.accept(
                    ReaderAnnotationMutationIntent.RequestDelete(intent.bookmark)
                )
        }
    }

    fun dismissSelection() {
        annotationMutations.accept(ReaderAnnotationMutationIntent.DismissCreate)
        selections.dismiss()
    }

    fun setAvailability(availability: AppAvailability) {
        val available = availability !is AppAvailability.Offline
        serverWritesAvailable = available
        annotationMutations.setServerAvailable(available)
        controller.setAuthorityAvailable(available)
        marginaliaLayerPolicy.setAuthorityAvailable(available)
        sessionReconciliation.setAvailability(availability)
    }

    fun flushForBackground() {
        viewModelScope.launch { controller.flushLatestProgress() }
    }

    fun flushThenExit(onExit: () -> Unit) {
        if (exitJob?.isActive == true) return
        exitJob = viewModelScope.launch {
            controller.flushLatestProgress()
            onExit()
        }
    }

    override fun onCleared() {
        bookmarkCaptureJob?.cancel()
        sessionReconciliation.clear()
        reconciliationEvents.close()
        annotationsController.close()
        visiblePageBookmarks.clear()
        marginaliaLayerPolicy.clear()
        marginaliaLayersController.clear()
        selections.detach()
        annotationMutations.clear()
        sessionMetadata.clear()
        highlightActivations.clear()
        controller.close { progressSyncJob.cancel() }
    }
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

private data class ReaderEntryIdentity(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String,
    val bookId: String,
    val existingSessionId: String?
)
