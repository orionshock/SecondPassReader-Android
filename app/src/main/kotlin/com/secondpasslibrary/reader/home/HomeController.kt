package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.app.AppAvailability
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
import kotlinx.coroutines.sync.Mutex

@Suppress("TooManyFunctions") // Two cached sections expose independent retry and refresh intents.
internal class HomeController(
    private val repository: HomeProjectionRepository,
    private val scope: CoroutineScope,
    private val localBookAvailable: suspend (
        HomeAccountScope,
        String
    ) -> Boolean = { _, _ -> false }
) {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val navigationChannel = Channel<HomeNavigationIntent>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()
    private val connectionEventChannel = Channel<HomeConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()
    private val refreshAvailabilityTracker = HomeRefreshAvailabilityTracker()
    val refreshAvailability = refreshAvailabilityTracker.changes

    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var accountProfileId: String? = null
    private var cacheScope: HomeAccountScope? = null
    private var account: HomeProjectionAccount? = null
    private var authenticationRejectionReported = false
    private var recentReadingLoad: Job? = null
    private var shelfLoad: Job? = null
    private var offlineAvailabilityLoad: Job? = null
    private val explicitRefresh = Mutex()

    fun updateAppAvailability(availability: AppAvailability) {
        val offline = availability is AppAvailability.Offline
        if (mutableState.value.offline == offline) return
        if (offline) {
            recentReadingLoad?.cancel()
            shelfLoad?.cancel()
            account = null
            connectionIdentity = null
            accountProfileId = null
            refreshAvailabilityTracker.reset()
        }
        offlineAvailabilityLoad?.cancel()
        mutableState.value = mutableState.value.copy(
            offline = offline,
            locallyReadableBookIds = emptySet()
        )
        refreshOfflineBookAvailability()
        if (offline) {
            loadCachedRecentReading()
            loadCachedShelves()
        }
    }

    fun initializeCached(scope: HomeAccountScope) {
        if (scope == cacheScope) {
            if (account != null) {
                connectionIdentity = null
                accountProfileId = null
                account = null
                authenticationRejectionReported = false
                refreshAvailabilityTracker.reset()
                recentReadingLoad?.cancel()
                shelfLoad?.cancel()
                offlineAvailabilityLoad?.cancel()
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
        refreshAvailabilityTracker.reset()
        recentReadingLoad?.cancel()
        shelfLoad?.cancel()
        offlineAvailabilityLoad?.cancel()
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

    fun refreshLocalBookAvailability() = refreshOfflineBookAvailability()

    /** The one explicit Home refresh; both sections remain owned by their existing projection seam. */
    suspend fun refreshAll(profile: ConnectionProfile?, profileId: String?) {
        if (!explicitRefresh.tryLock()) return
        try {
            if (profile != null && profileId != null) {
                val hadAuthority = account != null
                provideVerifiedAuthority(profile, profileId)
                if (hadAuthority) {
                    refreshRecentReading()
                    refreshShelves()
                }
            } else {
                loadCachedRecentReading()
                loadCachedShelves()
            }
            recentReadingLoad?.join()
            shelfLoad?.join()
        } finally {
            explicitRefresh.unlock()
        }
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
            refreshOfflineBookAvailability()
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
        refreshAvailabilityTracker.recentStarted()
        recentReadingLoad = scope.launch {
            repository.refreshReadingHistory(activeAccount, variant).collect { projection ->
                mutableState.value = mutableState.value.copy(recentReading = projection)
                if (projection.refresh.isTerminal()) {
                    refreshAvailabilityTracker.recentCompleted(projection.refresh)
                    reportAuthenticationRejection(projection.refresh)
                    refreshOfflineBookAvailability()
                }
            }
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
        refreshAvailabilityTracker.shelvesStarted()
        shelfLoad = scope.launch {
            repository.refreshShelves(activeAccount).collect { projection ->
                mutableState.value = mutableState.value.copy(shelves = projection)
                if (projection.refresh.isTerminal()) {
                    refreshAvailabilityTracker.shelvesCompleted(projection.refresh)
                    reportAuthenticationRejection(projection.refresh)
                }
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

    private fun refreshOfflineBookAvailability() {
        val current = mutableState.value
        val activeScope = cacheScope ?: return
        if (!current.offline) return
        val bookIds = current.recentReading.content?.items.orEmpty().map { it.book.id }.distinct()
        offlineAvailabilityLoad?.cancel()
        offlineAvailabilityLoad = scope.launch {
            val available = bookIds.filterTo(mutableSetOf()) { bookId ->
                runCatching { localBookAvailable(activeScope, bookId) }.getOrDefault(false)
            }
            if (cacheScope == activeScope && mutableState.value.offline) {
                mutableState.value = mutableState.value.copy(locallyReadableBookIds = available)
            }
        }
    }
}

private fun HomeProjectionRefresh.isTerminal() =
    this == HomeProjectionRefresh.Current || this is HomeProjectionRefresh.Failed
