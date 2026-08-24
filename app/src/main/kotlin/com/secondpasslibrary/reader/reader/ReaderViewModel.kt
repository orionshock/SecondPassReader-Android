package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.SplReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.progress.SplReaderProgressWriter
import com.secondpasslibrary.reader.reader.session.SplReaderSessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    assetResolver: SplReaderBookAssetResolver,
    engineOpener: ReaderEngineOpener,
    sessionCoordinator: SplReaderSessionCoordinator,
    progressWriter: SplReaderProgressWriter
) : ViewModel() {
    private val controller =
        ReaderController(
            assetResolver,
            engineOpener,
            sessionCoordinator,
            progressWriter,
            viewModelScope
        )

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

    fun setAuthorityAvailable(available: Boolean) = controller.setAuthorityAvailable(available)

    override fun onCleared() = controller.close()
}
