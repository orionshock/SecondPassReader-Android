package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfItemListOptions
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfItemPage
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

@Suppress("TooManyFunctions") // Detail metadata and item paging are one selected-Shelf boundary.
internal class ShelfDetailController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ShelfDetailState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<ShelvesConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: String? = null
    private var generation = 0L
    private var itemsGeneration = 0L
    private var detailJob: Job? = null
    private var itemsJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        this.profile = profile
        if (identity == connectionIdentity) return
        connectionIdentity = identity
        clear()
    }

    fun select(shelfId: String) {
        require(shelfId.isNotBlank()) { "Shelf ID must not be blank." }
        if (profile == null) return
        cancelRequests()
        generation += 1
        itemsGeneration += 1
        val layout = state.value.items.layout
        mutableState.value =
            ShelfDetailState(
                shelfId = shelfId,
                detail = ShelfDetailResourceState(loading = true),
                items = ShelfItemsState(layout = layout, initialLoading = true)
            )
        loadDetail(generation)
        launchItems(1, ShelvesLoadPhase.INITIAL, itemsGeneration)
    }

    fun retryDetail() {
        if (state.value.shelfId == null || state.value.detail.failure == null) return
        loadDetail(generation)
    }

    fun retryItems() {
        when (state.value.items.error?.phase) {
            ShelvesLoadPhase.INITIAL -> resetItemsAndLoad(state.value.items.ordering)
            ShelvesLoadPhase.NEXT_PAGE -> loadNextPage()
            null -> Unit
        }
    }

    fun changeItemOrdering(ordering: ShelfItemOrdering) {
        if (state.value.items.ordering == ordering) return
        resetItemsAndLoad(ordering)
    }

    fun setLayout(layout: ShelfBooksLayout) {
        if (state.value.items.layout == layout) return
        mutableState.value = state.value.copy(items = state.value.items.copy(layout = layout))
    }

    fun loadNextPage() {
        val current = state.value.items
        if (itemsJob?.isActive == true || current.currentPage == 0 || !current.hasNext) return
        launchItems(current.currentPage + 1, ShelvesLoadPhase.NEXT_PAGE, itemsGeneration)
    }

    fun applyAuthoritativeShelf(shelf: Shelf) {
        if (state.value.shelfId != shelf.id) return
        mutableState.value =
            state.value.copy(detail = ShelfDetailResourceState(shelf = shelf))
    }

    fun clear() {
        cancelRequests()
        generation += 1
        itemsGeneration += 1
        mutableState.value =
            ShelfDetailState(items = ShelfItemsState(layout = state.value.items.layout))
    }

    fun close() = cancelRequests()

    private fun loadDetail(activeGeneration: Long) {
        val activeProfile = profile ?: return
        val shelfId = state.value.shelfId ?: return
        detailJob?.cancel()
        mutableState.value =
            state.value.copy(
                detail = state.value.detail.copy(loading = true, failure = null)
            )
        detailJob = coroutineScope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).shelves.get(shelfId)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { shelf ->
                    mutableState.value =
                        state.value.copy(detail = ShelfDetailResourceState(shelf = shelf))
                },
                onFailure = { failure ->
                    val classified = failure.toShelvesFailure()
                    mutableState.value =
                        state.value.copy(
                            detail = ShelfDetailResourceState(failure = classified)
                        )
                    reportAuthenticationRejection(classified)
                }
            )
        }
    }

    private fun resetItemsAndLoad(ordering: ShelfItemOrdering) {
        if (state.value.shelfId == null || profile == null) return
        itemsJob?.cancel()
        itemsGeneration += 1
        val current = state.value.items
        mutableState.value =
            state.value.copy(
                items =
                    ShelfItemsState(
                        ordering = ordering,
                        layout = current.layout,
                        pageSize = current.pageSize,
                        items = current.items,
                        totalCount = current.totalCount,
                        initialLoading = true
                    )
            )
        launchItems(1, ShelvesLoadPhase.INITIAL, itemsGeneration)
    }

    private fun launchItems(page: Int, phase: ShelvesLoadPhase, activeGeneration: Long) {
        val activeProfile = profile ?: return
        val shelfId = state.value.shelfId ?: return
        val current = state.value.items
        val options = ShelfItemListOptions(current.ordering, page, current.pageSize)
        mutableState.value =
            state.value.copy(
                items =
                    current.copy(
                        initialLoading = phase == ShelvesLoadPhase.INITIAL,
                        nextPageLoading = phase == ShelvesLoadPhase.NEXT_PAGE,
                        error = null
                    )
            )
        itemsJob = coroutineScope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).shelves.listItems(shelfId, options)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != itemsGeneration) return@launch
            result.fold(
                onSuccess = { applyItems(it, phase) },
                onFailure = { applyItemsFailure(it, phase) }
            )
        }
    }

    private fun applyItems(page: ShelfItemPage, phase: ShelvesLoadPhase) {
        val current = state.value.items
        val items =
            if (phase == ShelvesLoadPhase.NEXT_PAGE) current.items + page.results else page.results
        mutableState.value =
            state.value.copy(
                items =
                    current.copy(
                        items = items,
                        totalCount = page.totalCount,
                        initialLoading = false,
                        nextPageLoading = false,
                        error = null,
                        hasNext = page.hasNext,
                        currentPage = page.page
                    )
            )
    }

    private fun applyItemsFailure(failure: Throwable, phase: ShelvesLoadPhase) {
        val classified = failure.toShelvesFailure()
        mutableState.value =
            state.value.copy(
                items =
                    state.value.items.copy(
                        initialLoading = false,
                        nextPageLoading = false,
                        error = ShelvesLoadError(classified, phase)
                    )
            )
        reportAuthenticationRejection(classified)
    }

    private fun reportAuthenticationRejection(failure: ShelvesFailure) {
        if (failure == ShelvesFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(ShelvesConnectionEvent.AuthenticationRejected)
        }
    }

    private fun cancelRequests() {
        detailJob?.cancel()
        itemsJob?.cancel()
    }
}
