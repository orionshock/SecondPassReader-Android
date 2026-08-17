package com.secondpasslibrary.reader.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
@Inject
internal constructor(repository: HomeProjectionRepository) :
    ViewModel() {
    private val controller = HomeController(repository, viewModelScope)

    internal val state = controller.state
    val navigation = controller.navigation
    internal val connectionEvents = controller.connectionEvents

    fun initialize(profile: ConnectionProfile, profileId: String) =
        controller.initialize(profile, profileId)

    fun setShowClosedSessions(showClosed: Boolean) = controller.setShowClosedSessions(showClosed)

    fun retryRecentReading() = controller.retryRecentReading()

    fun retryShelves() = controller.retryShelves()

    fun searchLibrary(query: String) = controller.searchLibrary(query)

    fun viewAllSessions() = controller.viewAllSessions()

    fun openShelves() = controller.openShelves()
}
