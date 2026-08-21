package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // Bounded list paging and selected-detail state-machine intents.
internal class LibraryEntityController<T, O>(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope,
    defaultOrdering: O,
    private val pageLoader: suspend (AuthenticatedSecondPassClient, LibraryEntityRequest<O>) ->
    LibraryPage<T>,
    private val detailLoader: suspend (AuthenticatedSecondPassClient, String) -> T
) {
    private val mutableState =
        MutableStateFlow<LibraryEntityState<T, O>>(
            LibraryEntityState<T, O>(ordering = defaultOrdering)
        )
    val state: StateFlow<LibraryEntityState<T, O>> = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<LibraryConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var selectedScope: LibraryScope = LibraryScope.Global
    private var selectedTagSlug: String? = null
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var generation = 0L
    private var detailGeneration = 0L
    private var loadJob: Job? = null
    private var detailJob: Job? = null

    fun prepare(profile: ConnectionProfile, scope: LibraryScope, tagSlug: String? = null) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        val changed =
            nextConnectionIdentity != connectionIdentity ||
                scope != selectedScope ||
                tagSlug != selectedTagSlug
        this.profile = profile
        selectedScope = scope
        selectedTagSlug = tagSlug
        if (!changed) return
        connectionIdentity = nextConnectionIdentity
        reset(cancelDetail = true)
    }

    fun activate() {
        val current = mutableState.value
        if (profile == null || loadJob?.isActive == true || current.currentPage > 0) return
        resetAndLoad(current.committedQuery, current.ordering)
    }

    fun selectScope(scope: LibraryScope, activate: Boolean, tagSlug: String? = selectedTagSlug) {
        if (selectedScope != scope || selectedTagSlug != tagSlug) {
            selectedScope = scope
            selectedTagSlug = tagSlug
            reset(cancelDetail = true)
        }
        if (activate) activate()
    }

    fun commitSearch(query: String) {
        val current = mutableState.value
        resetAndLoad(query, current.ordering)
    }

    fun changeOrdering(ordering: O) {
        val current = mutableState.value
        if (current.ordering == ordering) return
        resetAndLoad(current.committedQuery, ordering)
    }

    fun loadNextPage() {
        val current = mutableState.value
        if (loadJob?.isActive == true || current.currentPage == 0 || !current.hasNext) return
        launchPage(current.currentPage + 1, LibraryEntityLoadPhase.NEXT_PAGE)
    }

    fun retry() {
        when (mutableState.value.error?.phase) {
            LibraryEntityLoadPhase.INITIAL -> {
                val current = mutableState.value
                resetAndLoad(current.committedQuery, current.ordering)
            }

            LibraryEntityLoadPhase.NEXT_PAGE -> loadNextPage()

            null -> Unit
        }
    }

    fun select(id: String) {
        require(id.isNotBlank()) { "Library entity ID must not be blank." }
        val activeProfile = profile ?: return
        detailJob?.cancel()
        detailGeneration += 1
        val activeGeneration = detailGeneration
        mutableState.value =
            mutableState.value.copy(
                selected = LibraryEntityDetailState(id = id, loading = true)
            )
        detailJob = coroutineScope.launch {
            val result = runCatching {
                detailLoader(clientProvider.forProfile(activeProfile), id)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != detailGeneration) return@launch
            result.fold(
                onSuccess = { detail ->
                    mutableState.value =
                        mutableState.value.copy(
                            selected = LibraryEntityDetailState(id = id, detail = detail)
                        )
                },
                onFailure = { failure ->
                    val classified = failure.toLibraryFailure()
                    mutableState.value =
                        mutableState.value.copy(
                            selected = LibraryEntityDetailState(id = id, failure = classified)
                        )
                    reportAuthenticationRejection(classified)
                }
            )
        }
    }

    fun retryDetail() {
        val selected = mutableState.value.selected ?: return
        if (selected.failure != null) select(selected.id)
    }

    fun selectTag(tagSlug: String?, activate: Boolean) {
        if (selectedTagSlug == tagSlug) return
        selectedTagSlug = tagSlug
        resetPreservingSelection()
        if (activate) activate()
    }

    fun clearSelection() {
        detailJob?.cancel()
        detailGeneration += 1
        mutableState.value = mutableState.value.copy(selected = null)
    }

    fun close() {
        loadJob?.cancel()
        detailJob?.cancel()
    }

    private fun reset(cancelDetail: Boolean) {
        loadJob?.cancel()
        generation += 1
        if (cancelDetail) {
            detailJob?.cancel()
            detailGeneration += 1
        }
        val current = mutableState.value
        mutableState.value =
            LibraryEntityState(
                ordering = current.ordering,
                pageSize = current.pageSize,
                selected = current.selected.takeUnless { cancelDetail }
            )
    }

    private fun resetAndLoad(query: String, ordering: O) {
        if (profile == null) return
        loadJob?.cancel()
        generation += 1
        val current = mutableState.value
        mutableState.value =
            LibraryEntityState(
                committedQuery = query,
                ordering = ordering,
                pageSize = current.pageSize,
                items = current.items,
                totalCount = current.totalCount,
                initialLoading = true,
                selected = current.selected
            )
        launchPage(1, LibraryEntityLoadPhase.INITIAL, generation)
    }

    private fun launchPage(
        page: Int,
        phase: LibraryEntityLoadPhase,
        activeGeneration: Long = generation
    ) {
        val activeProfile = profile ?: return
        val current = mutableState.value
        val request =
            LibraryEntityRequest(
                current.committedQuery,
                current.ordering,
                selectedScope,
                page,
                current.pageSize,
                selectedTagSlug
            )
        mutableState.value =
            current.copy(
                initialLoading = phase == LibraryEntityLoadPhase.INITIAL,
                nextPageLoading = phase == LibraryEntityLoadPhase.NEXT_PAGE,
                error = null
            )
        loadJob = coroutineScope.launch {
            val result = runCatching {
                pageLoader(clientProvider.forProfile(activeProfile), request)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { applyPage(it, phase) },
                onFailure = { applyFailure(it, phase) }
            )
        }
    }

    private fun applyPage(page: LibraryPage<T>, phase: LibraryEntityLoadPhase) {
        val current = mutableState.value
        val items =
            if (phase == LibraryEntityLoadPhase.NEXT_PAGE) {
                current.items + page.results
            } else {
                page.results
            }
        mutableState.value =
            current.copy(
                items = items,
                totalCount = page.totalCount,
                initialLoading = false,
                nextPageLoading = false,
                error = null,
                hasNext = page.hasNext,
                currentPage = page.page
            )
    }

    private fun applyFailure(failure: Throwable, phase: LibraryEntityLoadPhase) {
        val classified = failure.toLibraryFailure()
        mutableState.value =
            mutableState.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                error = LibraryEntityLoadError(classified, phase)
            )
        reportAuthenticationRejection(classified)
    }

    private fun reportAuthenticationRejection(failure: LibraryFailure) {
        if (failure == LibraryFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(LibraryConnectionEvent.AuthenticationRejected)
        }
    }

    private fun resetPreservingSelection() {
        loadJob?.cancel()
        generation += 1
        val current = mutableState.value
        mutableState.value =
            LibraryEntityState(
                committedQuery = current.committedQuery,
                ordering = current.ordering,
                pageSize = current.pageSize,
                selected = current.selected
            )
    }
}

internal data class LibraryEntityRequest<O>(
    val query: String,
    val ordering: O,
    val scope: LibraryScope,
    val page: Int,
    val pageSize: Int,
    val tagSlug: String? = null
)
