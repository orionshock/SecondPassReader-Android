package com.secondpasslibrary.reader.shelves.collection

import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.shelves.SHELF_CARD_PREVIEW_LIMIT
import com.secondpasslibrary.reader.shelves.ShelvesConnectionEvent
import com.secondpasslibrary.reader.shelves.ShelvesFailure
import com.secondpasslibrary.reader.shelves.ShelvesLoadPhase
import com.secondpasslibrary.reader.shelves.toShelvesFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal abstract class ShelfCollectionController(
    private val shelfScope: ShelfScope,
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ShelfCollectionState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<ShelvesConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedSessionIdentity? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedSessionIdentity
        this.profile = profile
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        loadJob?.cancel()
        generation += 1
        mutableState.value = ShelfCollectionState(ordering = state.value.ordering)
    }

    fun activate() {
        if (profile == null || loadJob?.isActive == true || state.value.hasLoaded) return
        resetAndLoadNormal()
    }

    fun changeOrdering(ordering: ShelfOrdering) {
        if (state.value.ordering == ordering) return
        val current = state.value
        mutableState.value = current.copy(ordering = ordering)
        if (current.search.query == null) resetAndLoadNormal() else resetAndLoadSearch()
    }

    fun acceptSearch(intent: ShelfSearchIntent) {
        when (intent) {
            is ShelfSearchIntent.Update ->
                mutableState.value = state.value.copy(searchInput = intent.value)

            ShelfSearchIntent.Submit -> {
                val query = state.value.searchInput.trim()
                if (query.isEmpty()) {
                    acceptSearch(ShelfSearchIntent.Clear)
                } else {
                    val current = state.value
                    mutableState.value = current.copy(
                        searchInput = query,
                        search = ShelfSearchState(query = query)
                    )
                    resetAndLoadSearch()
                }
            }

            ShelfSearchIntent.Clear -> {
                val current = state.value
                if (current.search.query == null && current.searchInput.isEmpty()) return
                loadJob?.cancel()
                generation += 1
                mutableState.value = current.copy(searchInput = "", search = ShelfSearchState())
                activate()
            }
        }
    }

    fun loadNextPage() {
        val current = state.value
        if (loadJob?.isActive == true || current.activeCurrentPage == 0 || !current.activeHasNext) {
            return
        }
        launchPage(
            current.activeCurrentPage + 1,
            ShelvesLoadPhase.NEXT_PAGE,
            target = current.loadTarget
        )
    }

    fun retry() {
        when (state.value.activeError?.phase) {
            ShelvesLoadPhase.INITIAL ->
                if (state.value.search.query == null) resetAndLoadNormal() else resetAndLoadSearch()

            ShelvesLoadPhase.NEXT_PAGE -> loadNextPage()

            null -> Unit
        }
    }

    fun applyAuthoritativeChange(change: ShelfCollectionChange, refresh: Boolean = false) {
        val current = state.value
        val changed = current.apply(change)
        mutableState.value =
            current.copy(
                shelves = changed.first.sortedFor(current.ordering),
                totalCount = changed.second
            )
        if (refresh) resetAndLoadNormal()
    }

    fun close() = loadJob?.cancel()

    private fun resetAndLoadNormal() {
        if (profile == null) return
        loadJob?.cancel()
        generation += 1
        val current = state.value
        mutableState.value =
            current.copy(
                initialLoading = !current.hasLoaded,
                refreshing = current.hasLoaded,
                nextPageLoading = false,
                error = null
            )
        launchPage(1, ShelvesLoadPhase.INITIAL, generation, ShelfLoadTarget.NORMAL)
    }

    private fun resetAndLoadSearch() {
        if (profile == null || state.value.search.query == null) return
        loadJob?.cancel()
        generation += 1
        val current = state.value
        val search = current.search
        mutableState.value = current.copy(
            search = search.copy(
                initialLoading = !search.hasLoaded,
                refreshing = search.hasLoaded,
                nextPageLoading = false,
                error = null
            )
        )
        launchPage(1, ShelvesLoadPhase.INITIAL, generation, ShelfLoadTarget.SEARCH)
    }

    private fun launchPage(
        page: Int,
        phase: ShelvesLoadPhase,
        activeGeneration: Long = generation,
        target: ShelfLoadTarget
    ) {
        val activeProfile = profile ?: return
        val current = state.value
        val options =
            ShelfListOptions(
                scope = shelfScope,
                q = current.search.query.takeIf { target == ShelfLoadTarget.SEARCH },
                ordering = current.ordering,
                page = page,
                pageSize = current.pageSize,
                previewLimit = SHELF_CARD_PREVIEW_LIMIT
            )
        mutableState.value = current.withLoading(phase, target)
        loadJob = coroutineScope.launch {
            val result = runSuspendCatching {
                clientProvider.forProfile(activeProfile).shelves.list(options)
            }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { mutableState.value = state.value.applyPage(it, phase, target) },
                onFailure = { failure ->
                    val classified = failure.toShelvesFailure()
                    mutableState.value = state.value.applyFailure(classified, phase, target)
                    if (classified == ShelvesFailure.AUTHENTICATION_REJECTED) {
                        connectionEventChannel.trySend(
                            ShelvesConnectionEvent.AuthenticationRejected
                        )
                    }
                }
            )
        }
    }
}

internal class PersonalShelvesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
) : ShelfCollectionController(ShelfScope.PERSONAL, clientProvider, scope)

internal class SharedShelvesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
) : ShelfCollectionController(ShelfScope.SHARED, clientProvider, scope)

internal class GroupShelvesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
) : ShelfCollectionController(ShelfScope.GROUP, clientProvider, scope)
