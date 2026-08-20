package com.secondpasslibrary.reader.marginalia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class MarginaliaViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider
) : ViewModel() {
    private val controller = MarginaliaController(clientProvider, viewModelScope)

    val state = controller.state
    val sessionsState = controller.sessions.state
    val detailState = controller.detail.state
    val connectionEvents = controller.connectionEvents

    fun initialize(profile: ConnectionProfile) = controller.initialize(profile)

    fun changeStatus(filter: ReadingSessionStatusFilter) = controller.sessions.changeStatus(filter)

    fun commitSearch(query: String) = controller.sessions.commitSearch(query)

    fun loadNextPage() = controller.sessions.loadNextPage()

    fun retrySessions() = controller.sessions.retry()

    fun selectSession(sessionId: String) = controller.selectSession(sessionId)

    fun backFromDetail() = controller.backFromDetail()

    fun retryDetail() = controller.detail.retry()

    override fun onCleared() {
        controller.close()
    }
}
