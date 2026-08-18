package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.ShelfScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CancellationException
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
    private var connectionIdentity: String? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        this.profile = profile
        if (identity == connectionIdentity) return
        connectionIdentity = identity
        reset()
    }

    fun activate() {
        if (profile == null || loadJob?.isActive == true || state.value.currentPage > 0) return
        resetAndLoad(state.value.ordering)
    }

    fun changeOrdering(ordering: ShelfOrdering) {
        if (state.value.ordering == ordering) return
        resetAndLoad(ordering)
    }

    fun loadNextPage() {
        val current = state.value
        if (loadJob?.isActive == true || current.currentPage == 0 || !current.hasNext) return
        launchPage(current.currentPage + 1, ShelvesLoadPhase.NEXT_PAGE)
    }

    fun retry() {
        when (state.value.error?.phase) {
            ShelvesLoadPhase.INITIAL -> resetAndLoad(state.value.ordering)
            ShelvesLoadPhase.NEXT_PAGE -> loadNextPage()
            null -> Unit
        }
    }

    fun close() = loadJob?.cancel()

    private fun reset() {
        loadJob?.cancel()
        generation += 1
        mutableState.value = ShelfCollectionState(ordering = state.value.ordering)
    }

    private fun resetAndLoad(ordering: ShelfOrdering) {
        if (profile == null) return
        loadJob?.cancel()
        generation += 1
        val current = state.value
        mutableState.value =
            ShelfCollectionState(
                ordering = ordering,
                pageSize = current.pageSize,
                shelves = current.shelves,
                totalCount = current.totalCount,
                initialLoading = true
            )
        launchPage(1, ShelvesLoadPhase.INITIAL, generation)
    }

    private fun launchPage(
        page: Int,
        phase: ShelvesLoadPhase,
        activeGeneration: Long = generation
    ) {
        val activeProfile = profile ?: return
        val current = state.value
        val options =
            ShelfListOptions(
                scope = shelfScope,
                ordering = current.ordering,
                page = page,
                pageSize = current.pageSize,
                previewLimit = SHELF_CARD_PREVIEW_LIMIT
            )
        mutableState.value =
            current.copy(
                initialLoading = phase == ShelvesLoadPhase.INITIAL,
                nextPageLoading = phase == ShelvesLoadPhase.NEXT_PAGE,
                error = null
            )
        loadJob = coroutineScope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).shelves.list(options)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { applyPage(it, phase) },
                onFailure = { applyFailure(it, phase) }
            )
        }
    }

    private fun applyPage(page: ShelfPage, phase: ShelvesLoadPhase) {
        val current = state.value
        val shelves =
            if (phase == ShelvesLoadPhase.NEXT_PAGE) {
                current.shelves + page.shelves
            } else {
                page.shelves
            }
        mutableState.value =
            current.copy(
                shelves = shelves,
                totalCount = page.totalCount,
                initialLoading = false,
                nextPageLoading = false,
                error = null,
                hasNext = page.hasNextPage,
                currentPage = page.page
            )
    }

    private fun applyFailure(failure: Throwable, phase: ShelvesLoadPhase) {
        val classified = failure.toShelvesFailure()
        mutableState.value =
            state.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                error = ShelvesLoadError(classified, phase)
            )
        if (classified == ShelvesFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(ShelvesConnectionEvent.AuthenticationRejected)
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
