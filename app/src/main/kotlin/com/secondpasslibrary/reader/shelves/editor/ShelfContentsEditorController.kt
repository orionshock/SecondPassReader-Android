package com.secondpasslibrary.reader.shelves.editor

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfDetailOptions
import com.secondpasslibrary.client.ShelfEditorItem
import com.secondpasslibrary.client.ShelfEditorListOptions
import com.secondpasslibrary.client.ShelfEditorPage
import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
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

@Suppress("TooManyFunctions") // One editor boundary owns paging, mutation, and canonical reconcile.
internal class ShelfContentsEditorController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope,
    private val onShelfReconciled: (Shelf) -> Unit
) {
    private val mutableState = MutableStateFlow(ShelfContentsEditorState())
    val state = mutableState.asStateFlow()
    private val connectionEventChannel = Channel<ShelvesConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()
    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedSessionIdentity? = null
    private var generation = 0L
    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedSessionIdentity
        this.profile = profile
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        clear()
    }

    fun open(shelfId: String) {
        require(shelfId.isNotBlank()) { "Shelf ID must not be blank." }
        if (profile == null) return
        clear()
        generation += 1
        mutableState.value = ShelfContentsEditorState(shelfId = shelfId, initialLoading = true)
        loadPage(1, ShelvesLoadPhase.INITIAL, generation)
    }

    fun retry() {
        when (state.value.loadError?.phase) {
            ShelvesLoadPhase.INITIAL -> reload()
            ShelvesLoadPhase.NEXT_PAGE -> loadNextPage()
            null -> reload()
        }
    }

    fun loadNextPage() {
        val current = state.value
        if (loadJob?.isActive == true || current.currentPage == 0 || !current.hasNext) return
        loadPage(current.currentPage + 1, ShelvesLoadPhase.NEXT_PAGE, generation)
    }

    fun move(itemId: String, direction: ShelfItemMove) {
        if (state.value.entries.none { it.id == itemId && it is ShelfEditorItem.Available }) return
        mutate(itemId, direction.toMutation()) { client, shelfId ->
            client.shelves.moveItem(shelfId, itemId, direction)
        }
    }

    fun openPosition(itemId: String) {
        val current = state.value
        val entry = current.entries.find { it.id == itemId } as? ShelfEditorItem.Available ?: return
        if (!current.directPositionAvailable) return
        mutableState.value =
            current.copy(
                positionDialog =
                    ShelfPositionDialogState(
                        itemId,
                        entry.book.title,
                        (entry.position + 1).toString()
                    ),
                mutation = current.mutation.copy(failure = null)
            )
    }

    fun updatePosition(value: String) {
        val dialog = state.value.positionDialog ?: return
        if (value.any { !it.isDigit() }) return
        mutableState.value =
            state.value.copy(positionDialog = dialog.copy(value = value, invalid = false))
    }

    fun submitPosition() {
        val current = state.value
        val dialog = current.positionDialog ?: return
        if (!current.directPositionAvailable) {
            mutableState.value =
                current.copy(
                    positionDialog = null,
                    mutation =
                        ShelfContentsMutationState(
                            failure = ShelfContentsMutationFailure.DIRECT_POSITION_UNAVAILABLE
                        )
                )
        } else {
            val userPosition = dialog.value.toIntOrNull()
            if (userPosition == null || userPosition !in 1..current.totalCount) {
                mutableState.value = current.copy(positionDialog = dialog.copy(invalid = true))
            } else {
                mutableState.value = current.copy(positionDialog = null)
                mutate(dialog.itemId, ShelfContentsMutation.SET_POSITION) { client, shelfId ->
                    client.shelves.setItemPosition(shelfId, dialog.itemId, userPosition - 1)
                }
            }
        }
    }

    fun dismissPosition() {
        mutableState.value = state.value.copy(positionDialog = null)
    }

    fun requestRemoval(itemId: String) {
        val entry = state.value.entries.find { it.id == itemId } ?: return
        val label = (entry as? ShelfEditorItem.Available)?.book?.title
        mutableState.value =
            state.value.copy(
                removalDialog = ShelfRemovalDialogState(itemId, label),
                mutation = state.value.mutation.copy(failure = null)
            )
    }

    fun confirmRemoval() {
        val removal = state.value.removalDialog ?: return
        mutableState.value = state.value.copy(removalDialog = null)
        mutate(removal.itemId, ShelfContentsMutation.REMOVE) { client, shelfId ->
            client.shelves.removeItem(shelfId, removal.itemId)
        }
    }

    fun dismissRemoval() {
        mutableState.value = state.value.copy(removalDialog = null)
    }

    fun dismissMutationFailure() {
        mutableState.value = state.value.copy(mutation = ShelfContentsMutationState())
    }

    fun clear() {
        loadJob?.cancel()
        mutationJob?.cancel()
        generation += 1
        mutableState.value = ShelfContentsEditorState()
    }

    fun close() = clear()

    private fun reload() {
        if (state.value.shelfId == null || profile == null) return
        loadJob?.cancel()
        generation += 1
        mutableState.value =
            state.value.copy(initialLoading = true, nextPageLoading = false, loadError = null)
        loadPage(1, ShelvesLoadPhase.INITIAL, generation)
    }

    private fun loadPage(page: Int, phase: ShelvesLoadPhase, activeGeneration: Long) {
        val activeProfile = profile ?: return
        val shelfId = state.value.shelfId ?: return
        mutableState.value =
            state.value.copy(
                initialLoading = phase == ShelvesLoadPhase.INITIAL,
                nextPageLoading = phase == ShelvesLoadPhase.NEXT_PAGE,
                loadError = null
            )
        loadJob = coroutineScope.launch {
            val result = runSuspendCatching {
                clientProvider.forProfile(activeProfile).shelves.listEditorItems(
                    shelfId,
                    ShelfEditorListOptions(page, state.value.pageSize)
                )
            }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { applyPage(it, phase) },
                onFailure = { applyLoadFailure(it, phase) }
            )
        }
    }

    private fun applyPage(page: ShelfEditorPage, phase: ShelvesLoadPhase) {
        val current = state.value
        val entries = if (phase ==
            ShelvesLoadPhase.NEXT_PAGE
        ) {
            current.entries + page.results
        } else {
            page.results
        }
        mutableState.value =
            current.copy(
                entries = entries,
                totalCount = page.totalCount,
                visibleItemCount = page.visibleItemCount,
                unavailableItemCount = page.unavailableItemCount,
                initialLoading = false,
                nextPageLoading = false,
                loadError = null,
                hasNext = page.hasNext,
                currentPage = page.page
            )
    }

    private fun applyLoadFailure(failure: Throwable, phase: ShelvesLoadPhase) {
        val classified = failure.toShelvesFailure()
        mutableState.value =
            state.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                loadError = ShelvesLoadError(classified, phase)
            )
        reportAuthenticationRejection(classified)
    }

    private fun mutate(
        itemId: String,
        operation: ShelfContentsMutation,
        action: suspend (com.secondpasslibrary.client.AuthenticatedSecondPassClient, String) -> Unit
    ) {
        val context = profile?.let { active -> state.value.shelfId?.let { active to it } } ?: return
        if (mutationJob?.isActive == true) return
        val (activeProfile, shelfId) = context
        mutableState.value =
            state.value.copy(mutation = ShelfContentsMutationState(itemId, operation))
        mutationJob = coroutineScope.launch {
            var mutationSucceeded = false
            val result = runSuspendCatching {
                val client = clientProvider.forProfile(activeProfile)
                action(client, shelfId)
                mutationSucceeded = true
                reconcile(client, shelfId)
            }
            result.fold(
                onSuccess = { (page, shelf) ->
                    applyReconciledPage(page)
                    onShelfReconciled(shelf)
                },
                onFailure = { applyMutationFailure(it, mutationSucceeded) }
            )
        }
    }

    private suspend fun reconcile(
        client: com.secondpasslibrary.client.AuthenticatedSecondPassClient,
        shelfId: String
    ): Pair<ShelfEditorPage, Shelf> {
        val requestedPages = state.value.currentPage.coerceAtLeast(1)
        val pages = mutableListOf<ShelfEditorPage>()
        var pageNumber = 1
        do {
            val page = client.shelves.listEditorItems(
                shelfId,
                ShelfEditorListOptions(pageNumber, state.value.pageSize)
            )
            pages += page
            pageNumber += 1
        } while (pageNumber <= requestedPages && page.hasNext)
        val first = pages.first()
        val last = pages.last()
        val combined =
            first.copy(
                results = pages.flatMap { it.results },
                hasNext = last.hasNext,
                hasPrevious = last.hasPrevious,
                page = last.page
            )
        val shelf = client.shelves.get(shelfId, ShelfDetailOptions(SHELF_CARD_PREVIEW_LIMIT))
        return combined to shelf
    }

    private fun applyReconciledPage(page: ShelfEditorPage) {
        mutableState.value =
            state.value.copy(
                entries = page.results,
                totalCount = page.totalCount,
                visibleItemCount = page.visibleItemCount,
                unavailableItemCount = page.unavailableItemCount,
                initialLoading = false,
                nextPageLoading = false,
                loadError = null,
                hasNext = page.hasNext,
                currentPage = page.page,
                mutation = ShelfContentsMutationState()
            )
    }

    private fun applyMutationFailure(failure: Throwable, mutationSucceeded: Boolean) {
        val mapped = failure.toShelfContentsMutationFailure()
        val classified =
            if (mutationSucceeded &&
                mapped != ShelfContentsMutationFailure.AUTHENTICATION_REJECTED
            ) {
                ShelfContentsMutationFailure.RECONCILE_FAILED
            } else {
                mapped
            }
        mutableState.value =
            state.value.copy(mutation = ShelfContentsMutationState(failure = classified))
        if (classified == ShelfContentsMutationFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(ShelvesConnectionEvent.AuthenticationRejected)
        }
    }

    private fun reportAuthenticationRejection(failure: ShelvesFailure) {
        if (failure == ShelvesFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(ShelvesConnectionEvent.AuthenticationRejected)
        }
    }
}

private fun ShelfItemMove.toMutation() = when (this) {
    ShelfItemMove.UP -> ShelfContentsMutation.MOVE_UP
    ShelfItemMove.DOWN -> ShelfContentsMutation.MOVE_DOWN
}
