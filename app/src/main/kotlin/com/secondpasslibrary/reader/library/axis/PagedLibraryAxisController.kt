package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.CatalogResultPage
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.library.LibraryConnectionEvent
import com.secondpasslibrary.reader.library.LibraryFailure
import com.secondpasslibrary.reader.library.toLibraryFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // Bounded list paging and selected-detail state-machine intents.
internal class PagedLibraryAxisController<T, O>(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope,
    private val defaultOrdering: O,
    private val pageLoader: suspend (AuthenticatedSecondPassClient, PagedLibraryAxisRequest<O>) ->
    CatalogResultPage<T>,
    private val detailLoader: suspend (AuthenticatedSecondPassClient, String) -> T
) {
    private val mutableState =
        MutableStateFlow<PagedLibraryAxisState<T, O>>(
            PagedLibraryAxisState<T, O>(ordering = defaultOrdering)
        )
    val state: StateFlow<PagedLibraryAxisState<T, O>> = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<LibraryConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var selectedScope: LibraryScope = LibraryScope.Global
    private var selectedTagSlug: String? = null
    private var connectionIdentity: AuthenticatedSessionIdentity? = null
    private var generation = 0L
    private var detailGeneration = 0L
    private var loadJob: Job? = null
    private var detailJob: Job? = null

    fun prepare(profile: ConnectionProfile, scope: LibraryScope, tagSlug: String? = null) {
        val nextConnectionIdentity = profile.authenticatedSessionIdentity
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
        launchPage(current.currentPage + 1, PagedLibraryAxisLoadPhase.NEXT_PAGE)
    }

    fun retry() {
        when (mutableState.value.error?.phase) {
            PagedLibraryAxisLoadPhase.INITIAL -> {
                val current = mutableState.value
                resetAndLoad(current.committedQuery, current.ordering)
            }

            PagedLibraryAxisLoadPhase.NEXT_PAGE -> loadNextPage()

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
                selected = PagedLibraryAxisDetailState(id = id, loading = true)
            )
        detailJob = coroutineScope.launch {
            val result = runSuspendCatching {
                detailLoader(clientProvider.forProfile(activeProfile), id)
            }
            if (activeGeneration != detailGeneration) return@launch
            result.fold(
                onSuccess = { detail ->
                    mutableState.value =
                        mutableState.value.copy(
                            selected = PagedLibraryAxisDetailState(id = id, detail = detail)
                        )
                },
                onFailure = { failure ->
                    val classified = failure.toLibraryFailure()
                    mutableState.value =
                        mutableState.value.copy(
                            selected = PagedLibraryAxisDetailState(id = id, failure = classified)
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
        detailJob = null
        detailGeneration += 1
        mutableState.value = mutableState.value.copy(selected = null)
    }

    fun deactivate() {
        profile = null
        connectionIdentity = null
        selectedScope = LibraryScope.Global
        selectedTagSlug = null
        reset(cancelDetail = true)
        mutableState.value = mutableState.value.copy(ordering = defaultOrdering)
        discardConnectionEvents()
    }

    fun close() {
        loadJob?.cancel()
        detailJob?.cancel()
    }

    private fun reset(cancelDetail: Boolean) {
        loadJob?.cancel()
        loadJob = null
        generation += 1
        if (cancelDetail) {
            detailJob?.cancel()
            detailJob = null
            detailGeneration += 1
        }
        val current = mutableState.value
        mutableState.value =
            PagedLibraryAxisState(
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
            PagedLibraryAxisState(
                committedQuery = query,
                ordering = ordering,
                pageSize = current.pageSize,
                items = current.items,
                totalCount = current.totalCount,
                initialLoading = true,
                selected = current.selected
            )
        launchPage(1, PagedLibraryAxisLoadPhase.INITIAL, generation)
    }

    private fun launchPage(
        page: Int,
        phase: PagedLibraryAxisLoadPhase,
        activeGeneration: Long = generation
    ) {
        val activeProfile = profile ?: return
        val current = mutableState.value
        val request =
            PagedLibraryAxisRequest(
                current.committedQuery,
                current.ordering,
                selectedScope,
                page,
                current.pageSize,
                selectedTagSlug
            )
        mutableState.value =
            current.copy(
                initialLoading = phase == PagedLibraryAxisLoadPhase.INITIAL,
                nextPageLoading = phase == PagedLibraryAxisLoadPhase.NEXT_PAGE,
                error = null
            )
        loadJob = coroutineScope.launch {
            val result = runSuspendCatching {
                pageLoader(clientProvider.forProfile(activeProfile), request)
            }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { applyPage(it, phase) },
                onFailure = { applyFailure(it, phase) }
            )
        }
    }

    private fun applyPage(page: CatalogResultPage<T>, phase: PagedLibraryAxisLoadPhase) {
        val current = mutableState.value
        val items =
            if (phase == PagedLibraryAxisLoadPhase.NEXT_PAGE) {
                current.items + page.results
            } else {
                page.results
            }
        mutableState.value =
            current.copy(
                items = items,
                contextualCatalogTags = page.catalogTags,
                hasContextualCatalogTagsResponse = true,
                totalCount = page.totalCount,
                initialLoading = false,
                nextPageLoading = false,
                error = null,
                hasNext = page.hasNext,
                currentPage = page.page
            )
    }

    private fun applyFailure(failure: Throwable, phase: PagedLibraryAxisLoadPhase) {
        val classified = failure.toLibraryFailure()
        mutableState.value =
            mutableState.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                error = PagedLibraryAxisLoadError(classified, phase)
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
        loadJob = null
        generation += 1
        val current = mutableState.value
        mutableState.value =
            PagedLibraryAxisState(
                committedQuery = current.committedQuery,
                ordering = current.ordering,
                pageSize = current.pageSize,
                selected = current.selected
            )
    }

    private fun discardConnectionEvents() {
        while (connectionEventChannel.tryReceive().isSuccess) {
            // Events from the ended authority lifetime must not survive a later reconnect.
        }
    }
}

internal data class PagedLibraryAxisRequest<O>(
    val query: String,
    val ordering: O,
    val scope: LibraryScope,
    val page: Int,
    val pageSize: Int,
    val tagSlug: String? = null
)

internal fun libraryAuthorsAxisController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
): PagedLibraryAxisController<LibraryAuthor, AuthorOrdering> = PagedLibraryAxisController(
    clientProvider = clientProvider,
    coroutineScope = scope,
    defaultOrdering = AuthorOrdering.NAME,
    pageLoader = { client, request ->
        client.library.authors.list(
            request.scope,
            AuthorListOptions(
                q = request.query.takeIf(String::isNotBlank),
                tagSlug = request.tagSlug,
                ordering = request.ordering,
                page = request.page,
                pageSize = request.pageSize,
                previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT
            )
        )
    },
    detailLoader = { client, id ->
        client.library.authors.getAuthor(
            id,
            LibraryEntityDetailOptions(previewLimit = LIBRARY_ENTITY_DETAIL_PREVIEW_LIMIT)
        )
    }
)

internal fun librarySeriesAxisController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
): PagedLibraryAxisController<LibrarySeries, SeriesOrdering> = PagedLibraryAxisController(
    clientProvider = clientProvider,
    coroutineScope = scope,
    defaultOrdering = SeriesOrdering.NAME,
    pageLoader = { client, request ->
        client.library.series.list(
            request.scope,
            SeriesListOptions(
                q = request.query.takeIf(String::isNotBlank),
                tagSlug = request.tagSlug,
                ordering = request.ordering,
                page = request.page,
                pageSize = request.pageSize,
                previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT
            )
        )
    },
    detailLoader = { client, id ->
        client.library.series.getSeries(
            id,
            LibraryEntityDetailOptions(previewLimit = LIBRARY_ENTITY_DETAIL_PREVIEW_LIMIT)
        )
    }
)
