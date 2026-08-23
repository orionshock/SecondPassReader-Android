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

    internal fun initializeCached(scope: HomeAccountScope) = controller.initializeCached(scope)

    internal fun provideVerifiedAuthority(profile: ConnectionProfile, profileId: String) =
        controller.provideVerifiedAuthority(profile, profileId)

    fun setShowClosedSessions(showClosed: Boolean) = controller.setShowClosedSessions(showClosed)

    fun retryRecentReading() = controller.retryRecentReading()

    fun retryShelves() = controller.retryShelves()

    fun searchLibrary(query: String) =
        controller.navigate(HomeNavigationIntent.LibrarySearch(query))

    fun navigate(intent: HomeNavigationIntent) = controller.navigate(intent)

    fun viewAllSessions() = controller.navigate(HomeNavigationIntent.ViewAllSessions)

    fun openShelves() = controller.navigate(HomeNavigationIntent.OpenShelves)
}
