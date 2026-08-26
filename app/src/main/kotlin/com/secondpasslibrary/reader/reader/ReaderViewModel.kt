package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationCreateController
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationDecorationController
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsController
import com.secondpasslibrary.reader.reader.annotations.ReaderSelectionController
import com.secondpasslibrary.reader.reader.annotations.SplReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.SplReaderHighlightWriter
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.asset.SplReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.domain.ReaderAppearance
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.progress.SplReaderProgressWriter
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
    highlightWriter: SplReaderHighlightWriter
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
    private val annotationDecorations = ReaderAnnotationDecorationController()
    private val selections = ReaderSelectionController(viewModelScope)
    private val annotationCreate = ReaderAnnotationCreateController(
        writer = highlightWriter,
        scope = viewModelScope,
        onAuthoritativeAnnotations = { sessionId, annotations ->
            annotationsController.replaceAuthoritative(sessionId, annotations)
            selections.dismiss()
        }
    )
    private var exitJob: Job? = null
    private var activeProfile: ConnectionProfile? = null
    private var entryIdentity: ReaderEntryIdentity? = null

    val state = controller.state
    val progress = controller.progress
    val progressSync = controller.progressSync
    val annotations = annotationsController.state
    val selection = selections.selection
    val annotationCreateState = annotationCreate.state
    val connectionEvents = merge(
        controller.connectionEvents,
        annotationsController.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        },
        annotationCreate.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        }
    )

    init {
        viewModelScope.launch {
            controller.state.collect { readerState ->
                val ready = readerState as? ReaderState.Ready
                if (ready == null) {
                    selections.detach()
                    annotationCreate.clear()
                } else {
                    selections.attach(ready.engine.selectionEvents, ready.engine.cfiNavigator)
                    activeProfile?.let { profile ->
                        annotationsController.select(profile, ready.session.sessionId)
                        annotationCreate.select(
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
        if (entryIdentity != nextIdentity) annotationsController.clear()
        entryIdentity = nextIdentity
        activeProfile = profile
        controller.initialize(profile, profileId, bookId, existingSessionId)
    }

    fun retry() = controller.retry()

    fun updateAppearance(appearance: ReaderAppearance) = controller.updateAppearance(appearance)

    fun retryAnnotations() = annotationsController.retry()

    fun createHighlight(color: ReaderAnnotationColor) {
        selection.value?.let { annotationCreate.create(it, color) }
    }

    fun retryHighlight() = annotationCreate.retry()

    fun dismissSelection() = selections.dismiss()

    fun setAuthorityAvailable(available: Boolean) = controller.setAuthorityAvailable(available)

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
        annotationsController.close()
        selections.detach()
        annotationCreate.clear()
        controller.close { progressSyncJob.cancel() }
    }
}

private data class ReaderEntryIdentity(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String,
    val bookId: String,
    val existingSessionId: String?
)
