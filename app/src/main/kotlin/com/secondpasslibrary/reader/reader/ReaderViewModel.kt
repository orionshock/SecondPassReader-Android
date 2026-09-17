package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderBookmarkHudIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPreferenceStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.navigation.ReaderNavigationController
import com.secondpasslibrary.reader.reader.navigation.ReaderNavigationIntent
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.presentation.ReaderMarginaliaPresentationController
import com.secondpasslibrary.reader.reader.session.ReaderSessionCoordinator
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataWriter
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliation
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationController
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectController
import com.secondpasslibrary.reader.reader.sync.ReaderReconnectOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    assetResolver: ReaderBookAssetResolver,
    launchPolicy: ReaderLaunchAdmission,
    engineOpener: ReaderEngineOpener,
    sessionCoordinator: ReaderSessionCoordinator,
    appearanceStore: ReaderAppearanceStore,
    annotationsLoader: ReaderAnnotationsLoader,
    marginaliaLayerHistoryLoader: ReaderMarginaliaLayerHistoryLoader,
    marginaliaLayerPreferenceStore: ReaderMarginaliaLayerPreferenceStore,
    marginaliaLayerVisibilityStore: ReaderMarginaliaLayerVisibilityStore,
    sessionMetadataWriter: ReaderSessionMetadataWriter,
    sessionReconciler: ReaderSessionReconciliation,
    reconnectOrchestrator: ReaderReconnectOperation,
    localReaderStateStore: LocalReaderStateStore
) : ViewModel() {
    private val progressPersistenceJob = SupervisorJob()
    private val progressPersistenceScope = CoroutineScope(progressPersistenceJob + Dispatchers.IO)
    private val reconciliationEvents = Channel<ReaderConnectionEvent>(Channel.BUFFERED)
    private val foregroundSync: ReaderReconnectController = ReaderReconnectController(
        reconnectOrchestrator,
        viewModelScope,
        onAuthenticationRequired = {
            reconciliationEvents.trySend(ReaderConnectionEvent.AuthenticationRejected)
        },
        onRunCompleted = { marginalia.refreshLocalAnnotations() }
    )
    private val controller =
        ReaderController(
            assetResolver,
            engineOpener,
            sessionCoordinator,
            viewModelScope,
            localReaderStateStore,
            appearanceStore,
            progressPersistenceScope,
            launchPolicy,
            foregroundSync::requestSync
        )
    private val navigation = ReaderNavigationController(controller.state, viewModelScope)
    val navigationEvents = navigation.events
    val onNavigationIntent: (ReaderNavigationIntent) -> Unit = navigation::accept
    private val marginalia = ReaderMarginaliaPresentationController(
        controller.state,
        viewModelScope,
        annotationsLoader,
        marginaliaLayerHistoryLoader,
        marginaliaLayerPreferenceStore,
        marginaliaLayerVisibilityStore,
        sessionMetadataWriter,
        localReaderStateStore,
        foregroundSync::requestSync
    )
    private val sessionReconciliation = ReaderSessionReconciliationController(
        sessionReconciler,
        viewModelScope,
        onResolved = { localSessionId, session ->
            controller.acceptReconciledSession(localSessionId, session)
        },
        onAuthenticationRejected = {
            reconciliationEvents.trySend(ReaderConnectionEvent.AuthenticationRejected)
        }
    )
    private var exitJob: Job? = null
    private var activeProfile: ConnectionProfile? = null
    private var entryIdentity: ReaderEntryIdentity? = null

    val state = controller.state
    val progress = controller.progress
    val marginaliaState = marginalia.state
    val connectionEvents = merge(
        controller.connectionEvents,
        marginalia.authenticationRequiredEvents.map {
            ReaderConnectionEvent.AuthenticationRejected
        },
        reconciliationEvents.receiveAsFlow()
    )

    init {
        viewModelScope.launch {
            controller.state.collect { readerState ->
                val ready = readerState as? ReaderState.Ready
                if (ready == null) {
                    sessionReconciliation.clear()
                } else {
                    val profile = activeProfile ?: return@collect
                    val entry = entryIdentity ?: return@collect
                    sessionReconciliation.select(
                        profile,
                        entry.profileId,
                        entry.bookId,
                        ready.session,
                        ready.authority
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
        marginalia.selectEntry(profile, profileId, bookId, existingSessionId)
        entryIdentity = nextIdentity
        activeProfile = profile
        foregroundSync.update(profile, profileId, availability)
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

    fun acceptMarginalia(intent: ReaderMarginaliaIntent) = marginalia.acceptMarginalia(intent)

    fun mutateAnnotation(intent: ReaderAnnotationMutationIntent) =
        marginalia.mutateAnnotation(intent)

    fun acceptBookmark(intent: ReaderBookmarkHudIntent) = marginalia.acceptBookmark(intent)

    fun dismissSelection() = marginalia.dismissSelection()

    val dismissHighlightDetail: () -> Unit = marginalia::dismissHighlightDetail

    fun setAvailability(availability: AppAvailability) {
        foregroundSync.update(activeProfile, entryIdentity?.profileId, availability)
        marginalia.setAvailability(availability)
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
        sessionReconciliation.clear()
        foregroundSync.clear()
        reconciliationEvents.close()
        progressPersistenceScope.launch(
            Dispatchers.Main.immediate,
            start = CoroutineStart.UNDISPATCHED
        ) {
            try {
                marginalia.close()
            } finally {
                controller.close { progressPersistenceJob.cancel() }
            }
        }
    }
}

private data class ReaderEntryIdentity(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String,
    val bookId: String,
    val existingSessionId: String?
)
