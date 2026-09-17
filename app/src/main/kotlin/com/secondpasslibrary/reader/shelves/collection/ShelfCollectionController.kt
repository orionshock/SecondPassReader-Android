package com.secondpasslibrary.reader.shelves.collection

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.ShelfScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.shelves.SHELF_CARD_PREVIEW_LIMIT
import com.secondpasslibrary.reader.shelves.ShelvesConnectionEvent
import com.secondpasslibrary.reader.shelves.ShelvesFailure
import com.secondpasslibrary.reader.shelves.ShelvesLoadError
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
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        this.profile = profile
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        loadJob?.cancel()
        generation += 1
        mutableState.value = ShelfCollectionState(ordering = state.value.ordering)
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

    fun applyAuthoritativeChange(change: ShelfCollectionChange, refresh: Boolean = false) {
        val current = state.value
        val changed = current.apply(change)
        mutableState.value =
            current.copy(
                shelves = changed.first.sortedFor(current.ordering),
                totalCount = changed.second
            )
        if (refresh) resetAndLoad(current.ordering)
    }

    fun close() = loadJob?.cancel()

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
            val result = runSuspendCatching {
                clientProvider.forProfile(activeProfile).shelves.list(options)
            }
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

private fun ShelfCollectionState.apply(change: ShelfCollectionChange): Pair<List<Shelf>, Int> =
    when (change) {
        is ShelfCollectionChange.Added -> {
            val exists = shelves.any { it.id == change.shelf.id }
            shelves.filterNot { it.id == change.shelf.id } + change.shelf to
                totalCount + if (exists) 0 else 1
        }

        is ShelfCollectionChange.Updated ->
            shelves.map { if (it.id == change.shelf.id) change.shelf else it } to totalCount

        is ShelfCollectionChange.Removed -> {
            val retained = shelves.filterNot { it.id == change.shelfId }
            retained to (totalCount - (shelves.size - retained.size)).coerceAtLeast(0)
        }
    }

private fun List<Shelf>.sortedFor(ordering: ShelfOrdering): List<Shelf> = when (ordering) {
    ShelfOrdering.NAME -> sortedBy { it.name.lowercase() }
    ShelfOrdering.NAME_DESCENDING -> sortedByDescending { it.name.lowercase() }
    ShelfOrdering.ITEM_COUNT -> sortedBy { it.itemCount }
    ShelfOrdering.ITEM_COUNT_DESCENDING -> sortedByDescending { it.itemCount }
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
