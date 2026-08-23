package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
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

    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var accountProfileId: String? = null
    private var cacheScope: HomeAccountScope? = null
    private var account: HomeProjectionAccount? = null
    private var authenticationRejectionReported = false
    private var recentReadingLoad: Job? = null
    private var shelfLoad: Job? = null

    fun initializeCached(scope: HomeAccountScope) {
        if (scope == cacheScope) {
            if (account != null) {
                connectionIdentity = null
                accountProfileId = null
                account = null
                authenticationRejectionReported = false
                recentReadingLoad?.cancel()
                shelfLoad?.cancel()
                loadCachedRecentReading()
                loadCachedShelves()
            }
            return
        }
        cacheScope = scope
        connectionIdentity = null
        accountProfileId = null
        account = null
        authenticationRejectionReported = false
        recentReadingLoad?.cancel()
        shelfLoad?.cancel()
        mutableState.value = HomeUiState()
        loadCachedRecentReading()
        loadCachedShelves()
    }

    fun provideVerifiedAuthority(profile: ConnectionProfile, profileId: String) {
        val nextScope = HomeAccountScope(profile.serverOrigin, profileId)
        if (nextScope != cacheScope) initializeCached(nextScope)
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        if (nextConnectionIdentity == connectionIdentity && profileId == accountProfileId) return
        connectionIdentity = nextConnectionIdentity
        accountProfileId = profileId
        account = HomeProjectionAccount(profile, profileId)
        authenticationRejectionReported = false
        refreshRecentReading()
        refreshShelves()
    }

    fun setShowClosedSessions(showClosed: Boolean) {
        if (mutableState.value.showClosedSessions == showClosed) return
        mutableState.value = mutableState.value.copy(showClosedSessions = showClosed)
        if (account == null) loadCachedRecentReading() else refreshRecentReading()
    }

    fun retryRecentReading() {
        if (account == null) loadCachedRecentReading() else refreshRecentReading()
    }

    fun retryShelves() {
        if (account == null) loadCachedShelves() else refreshShelves()
    }

    fun navigate(intent: HomeNavigationIntent) {
        navigationChannel.trySend(intent)
    }

    private fun loadCachedRecentReading() {
        val activeScope = cacheScope ?: return
        val variant =
            if (mutableState.value.showClosedSessions) {
                HomeRecentReadingVariant.IncludingClosed
            } else {
                HomeRecentReadingVariant.ActiveOnly
            }
        recentReadingLoad?.cancel()
        recentReadingLoad = scope.launch {
            val cached = repository.readCachedReadingHistory(activeScope, variant)
            mutableState.value = mutableState.value.copy(recentReading = cached)
        }
    }

    private fun refreshRecentReading() {
        val activeAccount = account ?: return
        val variant =
            if (mutableState.value.showClosedSessions) {
                HomeRecentReadingVariant.IncludingClosed
            } else {
                HomeRecentReadingVariant.ActiveOnly
            }
        recentReadingLoad?.cancel()
        recentReadingLoad = scope.launch {
            val cached = repository.readCachedReadingHistory(activeAccount.scope, variant)
            mutableState.value = mutableState.value.copy(recentReading = cached)
            mutableState.value =
                mutableState.value.copy(
                    recentReading = cached.copy(refresh = HomeProjectionRefresh.Refreshing)
                )
            val refresh = repository.refreshReadingHistory(activeAccount, variant)
            val content =
                if (refresh == HomeProjectionRefresh.Current) {
                    repository.readCachedReadingHistory(activeAccount.scope, variant).content
                } else {
                    cached.content
                }
            mutableState.value =
                mutableState.value.copy(
                    recentReading = HomeProjectionState(content, refresh)
                )
            reportAuthenticationRejection(refresh)
        }
    }

    private fun loadCachedShelves() {
        val activeScope = cacheScope ?: return
        shelfLoad?.cancel()
        shelfLoad = scope.launch {
            val cached = repository.readCachedShelves(activeScope)
            mutableState.value = mutableState.value.copy(shelves = cached)
        }
    }

    private fun refreshShelves() {
        val activeAccount = account ?: return
        shelfLoad?.cancel()
        shelfLoad = scope.launch {
            val cached = repository.readCachedShelves(activeAccount.scope)
            mutableState.value = mutableState.value.copy(shelves = cached)
            mutableState.value =
                mutableState.value.copy(
                    shelves = cached.copy(refresh = HomeProjectionRefresh.Refreshing)
                )
            val refresh = repository.refreshShelves(activeAccount)
            val content =
                if (refresh == HomeProjectionRefresh.Current) {
                    repository.readCachedShelves(activeAccount.scope).content
                } else {
                    cached.content
                }
            mutableState.value =
                mutableState.value.copy(shelves = HomeProjectionState(content, refresh))
            reportAuthenticationRejection(refresh)
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
