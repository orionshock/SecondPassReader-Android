package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsController
import com.secondpasslibrary.reader.reader.annotations.SplReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationController
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationController
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.SplReaderAnnotationWriter
import com.secondpasslibrary.reader.reader.annotations.mutation.captureReaderBookmarkPosition
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
import com.secondpasslibrary.reader.reader.progress.SplReaderProgressWriter
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.session.SplReaderSessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    assetResolver: SplReaderBookAssetResolver,
    engineOpener: ReaderEngineOpener,
    sessionCoordinator: SplReaderSessionCoordinator,
    progressWriter: SplReaderProgressWriter,
    appearanceStore: ReaderAppearanceStore,
    annotationsLoader: SplReaderAnnotationsLoader,
    annotationWriter: SplReaderAnnotationWriter,
    marginaliaLayerHistoryLoader: SplReaderMarginaliaLayerHistoryLoader,
    marginaliaLayerPreferenceStore: ReaderMarginaliaLayerPreferenceStore,
    marginaliaLayerVisibilityStore: ReaderMarginaliaLayerVisibilityStore
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
            progressSyncScope
        )
    private val annotationsController = ReaderAnnotationsController(
        annotationsLoader,
        viewModelScope
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
    private val selections = ReaderSelectionController(viewModelScope)
    private val annotationMutations = ReaderAnnotationMutationController(
        writer = annotationWriter,
        scope = viewModelScope,
        onAuthoritativeAnnotations = { sessionId, annotations ->
            annotationsController.replaceAuthoritative(sessionId, annotations)
            selections.dismiss()
        }
    )
    private var exitJob: Job? = null
    private var bookmarkCaptureJob: Job? = null
    private var activeProfile: ConnectionProfile? = null
    private var entryIdentity: ReaderEntryIdentity? = null

    val state = controller.state
    val progress = controller.progress
    val progressSync = controller.progressSync
    val annotations = annotationsController.state
    val marginaliaLayers = marginaliaLayersController.state
    val autoShowPreviousMarginalia = marginaliaLayerPolicy.autoShowPrevious
    val selection = selections.selection
    val annotationMutationState = annotationMutations.state
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
        }
    )

    init {
        viewModelScope.launch {
            controller.state.collect { readerState ->
                val ready = readerState as? ReaderState.Ready
                if (ready == null) {
                    selections.detach()
                    annotationMutations.clear()
                } else {
                    selections.attach(ready.engine.selectionEvents, ready.engine.cfiNavigator)
                    activeProfile?.let { profile ->
                        annotationsController.select(profile, ready.session.sessionId)
                        entryIdentity?.let { entry ->
                            marginaliaLayersController.select(
                                profile,
                                entry.bookId,
                                ready.session
                            )
                            marginaliaLayerPolicy.select(
                                profile.authenticatedConnectionIdentity,
                                entry.bookId
                            )
                        }
                        annotationMutations.select(
                            profile,
                            ready.session.sessionId,
                            ready.session.status
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(controller.state, annotationsController.state) { reader, annotations ->
                reader to annotations
            }.collectLatest { (reader, annotations) ->
                val ready = reader as? ReaderState.Ready
                if (ready != null && annotations.sessionId == ready.session.sessionId) {
                    annotationDecorations.replace(
                        sessionId = ready.session.sessionId,
                        target = ready.engine.annotationDecorations,
                        annotations = annotations.annotations
                    )
                } else {
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
        existingSessionId: String?
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
                if (ready != null && layers.currentLayer?.sessionId == ready.session.sessionId) {
                    marginaliaLayerDecorations.replace(
                        readerSessionId = ready.session.sessionId,
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
        controller.initialize(profile, profileId, bookId, existingSessionId)
    }

    fun retry() = controller.retry()

    fun updateAppearance(appearance: ReaderAppearance) = controller.updateAppearance(appearance)

    fun acceptMarginalia(intent: ReaderMarginaliaIntent) {
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
        }
    }

    fun mutateAnnotation(intent: ReaderAnnotationMutationIntent) {
        annotationMutations.accept(intent)
    }

    fun createBookmark() {
        val ready = controller.state.value as? ReaderState.Ready
        when {
            ready == null || ready.session.status != ReaderSessionStatus.ACTIVE -> Unit

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
    }

    fun dismissSelection() {
        annotationMutations.accept(ReaderAnnotationMutationIntent.DismissCreate)
        selections.dismiss()
    }

    fun setAuthorityAvailable(available: Boolean) {
        controller.setAuthorityAvailable(available)
        marginaliaLayerPolicy.setAuthorityAvailable(available)
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
        annotationsController.close()
        marginaliaLayerPolicy.clear()
        marginaliaLayersController.clear()
        selections.detach()
        annotationMutations.clear()
        controller.close { progressSyncJob.cancel() }
    }
}

private data class ReaderEntryIdentity(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String,
    val bookId: String,
    val existingSessionId: String?
)
