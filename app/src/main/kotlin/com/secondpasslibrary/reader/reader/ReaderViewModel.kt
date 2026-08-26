package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsController
import com.secondpasslibrary.reader.reader.annotations.SplReaderAnnotationsLoader
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
    annotationsLoader: SplReaderAnnotationsLoader
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
    private var exitJob: Job? = null
    private var activeProfile: ConnectionProfile? = null
    private var entryIdentity: ReaderEntryIdentity? = null

    val state = controller.state
    val progress = controller.progress
    val progressSync = controller.progressSync
    val annotations = annotationsController.state
    val connectionEvents = merge(
        controller.connectionEvents,
        annotationsController.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        }
    )

    init {
        viewModelScope.launch {
            controller.state.collect { readerState ->
                val ready = readerState as? ReaderState.Ready ?: return@collect
                activeProfile?.let { profile ->
                    annotationsController.select(profile, ready.session.sessionId)
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
        controller.close { progressSyncJob.cancel() }
    }
}

private data class ReaderEntryIdentity(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String,
    val bookId: String,
    val existingSessionId: String?
)
