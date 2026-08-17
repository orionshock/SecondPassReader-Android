package com.secondpasslibrary.reader.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
@Inject
constructor(clientProvider: AuthenticatedClientProvider) : ViewModel() {
    private val controller = HomeController(clientProvider, viewModelScope)

    val state = controller.state
    val navigation = controller.navigation

    fun initialize(profile: ConnectionProfile) = controller.initialize(profile)

    fun setShowClosedSessions(showClosed: Boolean) = controller.setShowClosedSessions(showClosed)

    fun retryRecentReading() = controller.retryRecentReading()

    fun retryShelves() = controller.retryShelves()

    fun searchLibrary(query: String) = controller.searchLibrary(query)
}
