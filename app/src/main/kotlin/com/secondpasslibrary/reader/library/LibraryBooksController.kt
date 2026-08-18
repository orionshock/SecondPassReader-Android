package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.GroupBookListOptions
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupOrdering
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.SplClientException
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

@Suppress("TooManyFunctions") // Public methods are the controller's bounded Library intents.
internal class LibraryBooksController(
    private val clientProvider: AuthenticatedClientProvider,
    private val displayPreferenceStore: LibraryDisplayPreferenceStore,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(LibraryBooksState())
    val state: StateFlow<LibraryBooksState> = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<LibraryBooksConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var entryIdentity: String? = null
    private var connectionIdentity: String? = null
    private var requestGeneration = 0L
    private var loadJob: Job? = null
    private var preferenceJob: Job? = null
    private var groupsJob: Job? = null

    fun initializeBrowse(profile: ConnectionProfile, advancedGroupsEnabled: Boolean = false) {
        initialize(profile, "browse", LibraryBooksMode.BROWSE, "", advancedGroupsEnabled)
    }

    fun initializeBroadSearch(
        profile: ConnectionProfile,
        query: String,
        advancedGroupsEnabled: Boolean = false
    ) {
        initialize(
            profile,
            "broad\u0000$query",
            LibraryBooksMode.BROAD_SEARCH,
            query,
            advancedGroupsEnabled
        )
    }

    fun commitBrowseQuery(query: String) {
        val ordering =
            (mutableState.value.ordering as? LibraryBooksOrdering.Browse)?.value
                ?: BookOrdering.TITLE
        resetAndLoad(LibraryBooksMode.BROWSE, query, LibraryBooksOrdering.Browse(ordering))
    }

    fun commitBroadSearch(query: String) {
        val ordering =
            (mutableState.value.ordering as? LibraryBooksOrdering.BroadSearch)?.value
                ?: LibrarySearchOrdering.TITLE
        resetAndLoad(
            LibraryBooksMode.BROAD_SEARCH,
            query,
            LibraryBooksOrdering.BroadSearch(ordering)
        )
    }

    fun changeBrowseOrdering(ordering: BookOrdering) {
        val current = mutableState.value
        if (current.mode != LibraryBooksMode.BROWSE ||
            current.ordering == LibraryBooksOrdering.Browse(ordering)
        ) {
            return
        }
        resetAndLoad(current.mode, current.committedQuery, LibraryBooksOrdering.Browse(ordering))
    }

    fun changeBroadSearchOrdering(ordering: LibrarySearchOrdering) {
        val current = mutableState.value
        if (current.mode != LibraryBooksMode.BROAD_SEARCH ||
            current.ordering == LibraryBooksOrdering.BroadSearch(ordering)
        ) {
            return
        }
        resetAndLoad(
            current.mode,
            current.committedQuery,
            LibraryBooksOrdering.BroadSearch(ordering)
        )
    }

    fun loadNextPage() {
        val current = mutableState.value
        if (loadJob?.isActive == true || current.currentPage == 0 || !current.hasNext) return
        launchPage(current.currentPage + 1, LibraryBooksLoadPhase.NEXT_PAGE)
    }

    fun setLayout(layout: LibraryBooksLayout) {
        if (mutableState.value.layout == layout) return
        mutableState.value = mutableState.value.copy(layout = layout)
        preferenceJob?.cancel()
        preferenceJob = scope.launch { runCatching { displayPreferenceStore.write(layout) } }
    }

    fun selectScope(scope: LibraryScope) {
        val current = mutableState.value
        if (current.scope == scope) return
        if (scope is LibraryScope.Group &&
            current.groupSelector.groups.none { it.id == scope.id }
        ) {
            return
        }
        mutableState.value = current.copy(scope = scope)
        if (current.axis == LibraryAxis.BOOKS) {
            resetCurrentAndLoad()
        } else {
            clearPagingForDeferredAxis()
        }
    }

    fun selectAxis(axis: LibraryAxis) {
        val current = mutableState.value
        if (current.axis == axis) return
        mutableState.value = current.copy(axis = axis)
        if (axis == LibraryAxis.BOOKS && current.currentPage == 0) resetCurrentAndLoad()
    }

    fun retryGroups() = loadGroups()

    fun refresh() {
        if (profile == null || loadJob?.isActive == true) return
        requestGeneration += 1
        mutableState.value =
            mutableState.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                refreshing = true,
                error = null
            )
        launchPage(1, LibraryBooksLoadPhase.REFRESH, requestGeneration)
    }

    fun retry() {
        when (mutableState.value.error?.phase) {
            LibraryBooksLoadPhase.INITIAL -> resetCurrentAndLoad()
            LibraryBooksLoadPhase.NEXT_PAGE -> loadNextPage()
            LibraryBooksLoadPhase.REFRESH -> refresh()
            null -> Unit
        }
    }

    fun close() {
        loadJob?.cancel()
        preferenceJob?.cancel()
        groupsJob?.cancel()
    }

    private fun initialize(
        profile: ConnectionProfile,
        entry: String,
        mode: LibraryBooksMode,
        query: String,
        advancedGroupsEnabled: Boolean
    ) {
        val connection =
            "${profile.apiBaseUrl}\u0000${profile.clientSessionId}\u0000$advancedGroupsEnabled"
        val identity = "$connection\u0000$entry"
        if (identity == entryIdentity) return
        val previous = mutableState.value
        val sameConnection = connection == connectionIdentity
        this.profile = profile
        connectionIdentity = connection
        entryIdentity = identity
        mutableState.value =
            LibraryBooksState(
                mode = mode,
                committedQuery = query,
                ordering = defaultOrdering(mode),
                layout = previous.layout,
                scope =
                    if (sameConnection && advancedGroupsEnabled) {
                        previous.scope
                    } else {
                        LibraryScope.AllLibrary
                    },
                advancedGroupsEnabled = advancedGroupsEnabled,
                groupSelector =
                    if (sameConnection && advancedGroupsEnabled) {
                        previous.groupSelector
                    } else {
                        LibraryGroupSelectorState(loading = advancedGroupsEnabled)
                    }
            )
        loadDisplayPreference()
        resetAndLoad(mode, query, defaultOrdering(mode))
        if (advancedGroupsEnabled && !mutableState.value.groupSelector.loaded) loadGroups()
    }

    private fun defaultOrdering(mode: LibraryBooksMode): LibraryBooksOrdering = when (mode) {
        LibraryBooksMode.BROWSE -> LibraryBooksOrdering.Browse(BookOrdering.TITLE)

        LibraryBooksMode.BROAD_SEARCH ->
            LibraryBooksOrdering.BroadSearch(LibrarySearchOrdering.TITLE)
    }

    private fun loadGroups() {
        val activeProfile = profile ?: return
        if (!mutableState.value.advancedGroupsEnabled || groupsJob?.isActive == true) return
        mutableState.value =
            mutableState.value.copy(
                groupSelector = mutableState.value.groupSelector.copy(
                    loading = true,
                    failure = null
                )
            )
        groupsJob = scope.launch {
            val result = runCatching {
                val client = clientProvider.forProfile(activeProfile)
                buildList {
                    var pageNumber = 1
                    do {
                        val page =
                            client.listLibraryGroups(
                                LibraryGroupListOptions(
                                    ordering = LibraryGroupOrdering.NAME,
                                    page = pageNumber,
                                    pageSize = MAX_GROUP_SELECTOR_PAGE_SIZE
                                )
                            )
                        addAll(page.results)
                        pageNumber += 1
                    } while (page.hasNext)
                }
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = { groups ->
                    mutableState.value =
                        mutableState.value.copy(
                            groupSelector =
                                LibraryGroupSelectorState(loaded = true, groups = groups)
                        )
                },
                onFailure = { failure ->
                    val classified = failure.toLibraryBooksFailure()
                    mutableState.value =
                        mutableState.value.copy(
                            groupSelector = LibraryGroupSelectorState(failure = classified)
                        )
                    reportAuthenticationRejection(classified)
                }
            )
        }
    }

    private fun loadDisplayPreference() {
        preferenceJob?.cancel()
        preferenceJob = scope.launch {
            val layout = runCatching {
                displayPreferenceStore.read()
            }.getOrDefault(LibraryBooksLayout.GRID)
            mutableState.value = mutableState.value.copy(layout = layout)
        }
    }

    private fun resetCurrentAndLoad() {
        val current = mutableState.value
        resetAndLoad(current.mode, current.committedQuery, current.ordering)
    }

    private fun resetAndLoad(
        mode: LibraryBooksMode,
        query: String,
        ordering: LibraryBooksOrdering
    ) {
        if (profile == null) return
        val current = mutableState.value
        loadJob?.cancel()
        requestGeneration += 1
        mutableState.value =
            LibraryBooksState(
                mode = mode,
                committedQuery = query,
                ordering = ordering,
                pageSize = current.pageSize,
                layout = current.layout,
                axis = current.axis,
                scope = current.scope,
                advancedGroupsEnabled = current.advancedGroupsEnabled,
                groupSelector = current.groupSelector,
                books = current.books,
                totalCount = current.totalCount,
                initialLoading = true
            )
        launchPage(1, LibraryBooksLoadPhase.INITIAL, requestGeneration)
    }

    private fun clearPagingForDeferredAxis() {
        loadJob?.cancel()
        requestGeneration += 1
        mutableState.value =
            mutableState.value.copy(
                books = emptyList(),
                totalCount = 0,
                initialLoading = false,
                nextPageLoading = false,
                refreshing = false,
                error = null,
                hasNext = false,
                currentPage = 0
            )
    }

    private fun launchPage(
        page: Int,
        phase: LibraryBooksLoadPhase,
        generation: Long = requestGeneration
    ) {
        val activeProfile = profile ?: return
        val request = LibraryBooksRequest.from(mutableState.value, page)
        markLoading(phase)
        loadJob = scope.launch {
            val result = runCatching {
                val client = clientProvider.forProfile(activeProfile)
                request.load(client)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (generation != requestGeneration) return@launch
            result.fold(
                onSuccess = { applyPage(it, phase) },
                onFailure = { applyFailure(it, phase) }
            )
        }
    }

    private fun markLoading(phase: LibraryBooksLoadPhase) {
        mutableState.value =
            mutableState.value.copy(
                initialLoading = phase == LibraryBooksLoadPhase.INITIAL,
                nextPageLoading = phase == LibraryBooksLoadPhase.NEXT_PAGE,
                refreshing = phase == LibraryBooksLoadPhase.REFRESH,
                error = null
            )
    }

    private fun applyPage(page: LibraryPage<CompactBook>, phase: LibraryBooksLoadPhase) {
        val current = mutableState.value
        val books =
            if (phase == LibraryBooksLoadPhase.NEXT_PAGE) {
                current.books + page.results
            } else {
                page.results
            }
        mutableState.value =
            current.copy(
                books = books,
                totalCount = page.totalCount,
                initialLoading = false,
                nextPageLoading = false,
                refreshing = false,
                error = null,
                hasNext = page.hasNext,
                currentPage = page.page
            )
    }

    private fun applyFailure(failure: Throwable, phase: LibraryBooksLoadPhase) {
        val classified = failure.toLibraryBooksFailure()
        mutableState.value =
            mutableState.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                refreshing = false,
                error = LibraryBooksLoadError(classified, phase)
            )
        reportAuthenticationRejection(classified)
    }

    private fun reportAuthenticationRejection(failure: LibraryBooksFailure) {
        if (failure == LibraryBooksFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(LibraryBooksConnectionEvent.AuthenticationRejected)
        }
    }
}

private data class LibraryBooksRequest(
    val mode: LibraryBooksMode,
    val query: String,
    val ordering: LibraryBooksOrdering,
    val scope: LibraryScope,
    val page: Int,
    val pageSize: Int
) {
    suspend fun load(client: AuthenticatedSecondPassClient): LibraryPage<CompactBook> =
        when (val selectedScope = scope) {
            is LibraryScope.Group ->
                client.listGroupBooks(
                    selectedScope.id,
                    GroupBookListOptions(
                        q = query.takeIf(String::isNotBlank),
                        ordering = ordering.toBookOrdering(),
                        page = page,
                        pageSize = pageSize
                    )
                )

            LibraryScope.AllLibrary -> loadAllLibrary(client)
        }

    private suspend fun loadAllLibrary(
        client: AuthenticatedSecondPassClient
    ): LibraryPage<CompactBook> = when (mode) {
        LibraryBooksMode.BROWSE ->
            client.listBooks(
                BookListOptions(
                    q = query.takeIf(String::isNotBlank),
                    ordering = (ordering as LibraryBooksOrdering.Browse).value,
                    page = page,
                    pageSize = pageSize
                )
            )

        LibraryBooksMode.BROAD_SEARCH ->
            client.searchLibrary(
                LibrarySearchOptions(
                    q = query,
                    ordering = (ordering as LibraryBooksOrdering.BroadSearch).value,
                    page = page,
                    pageSize = pageSize
                )
            )
    }

    companion object {
        fun from(state: LibraryBooksState, page: Int) = LibraryBooksRequest(
            state.mode,
            state.committedQuery,
            state.ordering,
            state.scope,
            page,
            state.pageSize
        )
    }
}

private fun LibraryBooksOrdering.toBookOrdering(): BookOrdering = when (this) {
    is LibraryBooksOrdering.Browse -> value

    is LibraryBooksOrdering.BroadSearch ->
        when (value) {
            LibrarySearchOrdering.TITLE -> BookOrdering.TITLE
            LibrarySearchOrdering.TITLE_DESCENDING -> BookOrdering.TITLE_DESCENDING
            LibrarySearchOrdering.AUTHOR -> BookOrdering.AUTHOR
            LibrarySearchOrdering.AUTHOR_DESCENDING -> BookOrdering.AUTHOR_DESCENDING
            LibrarySearchOrdering.SERIES -> BookOrdering.SERIES
            LibrarySearchOrdering.SERIES_DESCENDING -> BookOrdering.SERIES_DESCENDING
        }
}

private const val MAX_GROUP_SELECTOR_PAGE_SIZE = 200

private fun Throwable.toLibraryBooksFailure(): LibraryBooksFailure = when (this) {
    is SplClientException.ServerUnreachable -> LibraryBooksFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> LibraryBooksFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> LibraryBooksFailure.PROTOCOL_INVALID

    else -> LibraryBooksFailure.OTHER
}
