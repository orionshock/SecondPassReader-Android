package com.secondpasslibrary.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.SplReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    assetResolver: SplReaderBookAssetResolver,
    engineOpener: ReaderEngineOpener
) : ViewModel() {
    private val controller =
        ReaderController(assetResolver, engineOpener, viewModelScope)

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initialize(profile: ConnectionProfile, profileId: String, bookId: String) =
        controller.initialize(profile, profileId, bookId)

    fun retry() = controller.retry()

    override fun onCleared() = controller.close()
}
