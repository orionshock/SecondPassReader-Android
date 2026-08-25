package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionProfile
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
import kotlinx.coroutines.launch

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    assetResolver: SplReaderBookAssetResolver,
    engineOpener: ReaderEngineOpener,
    sessionCoordinator: SplReaderSessionCoordinator,
    progressWriter: SplReaderProgressWriter,
    appearanceStore: ReaderAppearanceStore
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
    private var exitJob: Job? = null

    val state = controller.state
    val progress = controller.progress
    val progressSync = controller.progressSync
    val connectionEvents = controller.connectionEvents

    fun initialize(
        profile: ConnectionProfile,
        profileId: String,
        bookId: String,
        existingSessionId: String?
    ) = controller.initialize(profile, profileId, bookId, existingSessionId)

    fun retry() = controller.retry()

    fun updateAppearance(appearance: ReaderAppearance) = controller.updateAppearance(appearance)

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

    override fun onCleared() = controller.close { progressSyncJob.cancel() }
}
