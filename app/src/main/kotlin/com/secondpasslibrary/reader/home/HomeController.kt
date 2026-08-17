package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeController(
    private val clientProvider: AuthenticatedClientProvider,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val navigationChannel = Channel<HomeNavigationIntent>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()

    private var profileIdentity: String? = null
    private var client: AuthenticatedSecondPassClient? = null
    private var initialization: Job? = null
    private var recentReadingLoad: Job? = null
    private var shelfLoad: Job? = null

    fun initialize(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        if (identity == profileIdentity) return
        profileIdentity = identity
        initialization?.cancel()
        recentReadingLoad?.cancel()
        shelfLoad?.cancel()
        client = null
        mutableState.value = HomeUiState()
        initialization = scope.launch {
            val resolved = attempt { clientProvider.forProfile(profile) }
            resolved.onSuccess { authenticatedClient ->
                client = authenticatedClient
                loadRecentReading()
                loadShelves()
            }.onFailure { failure ->
                val error = HomeSectionState.Error(HomeErrorPresenter.message(failure))
                mutableState.value = mutableState.value.copy(
                    recentReading = error,
                    shelves = error
                )
            }
        }
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

    private fun loadRecentReading() {
        val activeClient = client ?: return
        recentReadingLoad?.cancel()
        mutableState.value = mutableState.value.copy(recentReading = HomeSectionState.Loading)
        recentReadingLoad = scope.launch {
            attempt {
                activeClient.recentReading(
                    RecentReadingOptions(
                        limit = RECENT_READING_LIMIT,
                        includeClosed = mutableState.value.showClosedSessions
                    )
                )
            }.onSuccess { items ->
                mutableState.value = mutableState.value.copy(
                    recentReading = items.toSectionState()
                )
            }.onFailure { failure ->
                mutableState.value = mutableState.value.copy(
                    recentReading = HomeSectionState.Error(HomeErrorPresenter.message(failure))
                )
            }
        }
    }

    private fun loadShelves() {
        val activeClient = client ?: return
        shelfLoad?.cancel()
        mutableState.value = mutableState.value.copy(shelves = HomeSectionState.Loading)
        shelfLoad = scope.launch {
            attempt {
                activeClient.listShelves(
                    ShelfListOptions(
                        page = SHELF_PAGE,
                        pageSize = SHELF_PAGE_SIZE,
                        previewLimit = SHELF_PREVIEW_LIMIT
                    )
                ).shelves
            }.onSuccess { shelves ->
                mutableState.value = mutableState.value.copy(shelves = shelves.toSectionState())
            }.onFailure { failure ->
                mutableState.value = mutableState.value.copy(
                    shelves = HomeSectionState.Error(HomeErrorPresenter.message(failure))
                )
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        runCatching { block() }.also { result ->
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        }

    private fun <T> List<T>.toSectionState(): HomeSectionState<T> =
        if (isEmpty()) HomeSectionState.Empty else HomeSectionState.Loaded(this)

    private companion object {
        const val RECENT_READING_LIMIT = 10
        const val SHELF_PAGE = 1
        const val SHELF_PAGE_SIZE = 6
        const val SHELF_PREVIEW_LIMIT = 3
    }
}
