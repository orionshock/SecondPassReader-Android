package com.secondpasslibrary.reader.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
@Inject
internal constructor(
    repository: HomeProjectionRepository,
    assetStore: ReaderBookAssetStore
) : ViewModel() {
    private val controller = HomeController(repository, viewModelScope) { scope, bookId ->
        assetStore.findCompleted(
            ReaderAccountScope(scope.serverOrigin, scope.profileId),
            bookId
        ) != null
    }

    internal val state = controller.state
    val navigation = controller.navigation
    internal val connectionEvents = controller.connectionEvents
    internal val refreshAvailability = controller.refreshAvailability

    internal fun initializeCached(scope: HomeAccountScope) = controller.initializeCached(scope)

    internal fun provideVerifiedAuthority(profile: ConnectionProfile, profileId: String) =
        controller.provideVerifiedAuthority(profile, profileId)

    internal fun updateAppAvailability(availability: AppAvailability) =
        controller.updateAppAvailability(availability)

    fun setShowClosedSessions(showClosed: Boolean) = controller.setShowClosedSessions(showClosed)

    fun retryRecentReading() = controller.retryRecentReading()

    fun retryShelves() = controller.retryShelves()

    internal suspend fun refreshAll(profile: ConnectionProfile?, profileId: String?) =
        controller.refreshAll(profile, profileId)

    fun searchLibrary(query: String) =
        controller.navigate(HomeNavigationIntent.LibrarySearch(query))

    fun navigate(intent: HomeNavigationIntent) = controller.navigate(intent)

    fun viewAllSessions() = controller.navigate(HomeNavigationIntent.ViewAllSessions)

    fun openShelves() = controller.navigate(HomeNavigationIntent.OpenShelves)
}
