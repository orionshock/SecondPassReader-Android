package com.secondpasslibrary.reader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.LocalAccountContext
import com.secondpasslibrary.reader.home.HomeProjectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel
@Inject
internal constructor(homeRepository: HomeProjectionRepository) :
    ViewModel() {
    private val controller = AppSessionController(homeRepository, viewModelScope)

    internal val state = controller.state

    internal fun updateConnection(
        connection: ConnectionUiState,
        localAccount: LocalAccountContext?
    ) = controller.updateConnection(connection, localAccount)
}
