package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class HomeController(
    private val repository: HomeProjectionRepository,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val navigationChannel = Channel<HomeNavigationIntent>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()
    private val connectionEventChannel = Channel<HomeConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var identity: String? = null
    private var account: HomeProjectionAccount? = null
    private var authenticationRejectionReported = false
    private var recentReadingLoad: Job? = null
    private var shelfLoad: Job? = null

    fun initialize(profile: ConnectionProfile, profileId: String) {
        val newIdentity = "${profile.apiBaseUrl}\u0000$profileId\u0000${profile.clientSessionId}"
        if (newIdentity == identity) return
        identity = newIdentity
        account = HomeProjectionAccount(profile, profileId)
        authenticationRejectionReported = false
        recentReadingLoad?.cancel()
        shelfLoad?.cancel()
        mutableState.value = HomeUiState()
        loadRecentReading()
        loadShelves()
    }

    fun setShowClosedSessions(showClosed: Boolean) {
        if (mutableState.value.showClosedSessions == showClosed) return
        mutableState.value = mutableState.value.copy(showClosedSessions = showClosed)
        loadRecentReading()
    }

    fun retryRecentReading() = loadRecentReading()

    fun retryShelves() = loadShelves()

    fun searchLibrary(query: String) {
        navigationChannel.trySend(HomeNavigationIntent.LibrarySearch(query))
    }

    fun viewAllSessions() {
        navigationChannel.trySend(HomeNavigationIntent.ViewAllSessions)
    }

    fun openShelves() {
        navigationChannel.trySend(HomeNavigationIntent.OpenShelves)
    }

    private fun loadRecentReading() {
        val activeAccount = account ?: return
        val variant =
            if (mutableState.value.showClosedSessions) {
                HomeRecentReadingVariant.IncludingClosed
            } else {
                HomeRecentReadingVariant.ActiveOnly
            }
        recentReadingLoad?.cancel()
        recentReadingLoad = scope.launch {
            repository.readingHistory(activeAccount, variant).collect { projection ->
                mutableState.value = mutableState.value.copy(recentReading = projection)
                reportAuthenticationRejection(projection.refresh)
            }
        }
    }

    private fun loadShelves() {
        val activeAccount = account ?: return
        shelfLoad?.cancel()
        shelfLoad = scope.launch {
            repository.shelves(activeAccount).collect { projection ->
                mutableState.value = mutableState.value.copy(shelves = projection)
                reportAuthenticationRejection(projection.refresh)
            }
        }
    }

    private fun reportAuthenticationRejection(refresh: HomeProjectionRefresh) {
        val failure = (refresh as? HomeProjectionRefresh.Failed)?.reason
        if (failure != HomeProjectionFailure.AuthenticationRejected ||
            authenticationRejectionReported
        ) {
            return
        }
        authenticationRejectionReported = true
        connectionEventChannel.trySend(HomeConnectionEvent.AuthenticationRejected)
    }
}
