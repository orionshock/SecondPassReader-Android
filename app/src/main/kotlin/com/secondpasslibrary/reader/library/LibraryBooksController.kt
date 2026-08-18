package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.GroupBookListOptions
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySearchOrdering
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

@Suppress("TooManyFunctions") // Public methods are the Books child's bounded intents.
internal class LibraryBooksController(
    private val clientProvider: AuthenticatedClientProvider,
    private val displayPreferenceStore: LibraryDisplayPreferenceStore,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(LibraryBooksState())
    val state: StateFlow<LibraryBooksState> = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<LibraryConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var selectedScope: LibraryScope = LibraryScope.AllLibrary
    private var entryIdentity: String? = null
    private var requestGeneration = 0L
    private var loadJob: Job? = null
    private var preferenceJob: Job? = null

    fun initialize(
        profile: ConnectionProfile,
        mode: LibraryBooksMode,
        query: String,
        scope: LibraryScope
    ) {
        val identity =
            "${profile.apiBaseUrl}\u0000${profile.clientSessionId}\u0000$mode\u0000$query\u0000$scope"
        if (identity == entryIdentity) return
        entryIdentity = identity
        this.profile = profile
        selectedScope = scope
        loadDisplayPreference()
        resetAndLoad(mode, query, defaultOrdering(mode))
    }

    fun selectScope(scope: LibraryScope) {
        if (selectedScope == scope) return
        selectedScope = scope
        entryIdentity = null
        resetCurrentAndLoad()
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
                books = current.books,
                totalCount = current.totalCount,
                initialLoading = true
            )
        launchPage(1, LibraryBooksLoadPhase.INITIAL, requestGeneration)
    }

    private fun launchPage(
        page: Int,
        phase: LibraryBooksLoadPhase,
        generation: Long = requestGeneration
    ) {
        val activeProfile = profile ?: return
        val request = LibraryBooksRequest.from(mutableState.value, selectedScope, page)
        markLoading(phase)
        loadJob = scope.launch {
            val result = runCatching {
                request.load(clientProvider.forProfile(activeProfile).library.books)
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
            if (phase ==
                LibraryBooksLoadPhase.NEXT_PAGE
            ) {
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
        val classified = failure.toLibraryFailure()
        mutableState.value =
            mutableState.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                refreshing = false,
                error = LibraryBooksLoadError(classified, phase)
            )
        if (classified == LibraryFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(LibraryConnectionEvent.AuthenticationRejected)
        }
    }

    private fun defaultOrdering(mode: LibraryBooksMode): LibraryBooksOrdering = when (mode) {
        LibraryBooksMode.BROWSE -> LibraryBooksOrdering.Browse(BookOrdering.TITLE)

        LibraryBooksMode.BROAD_SEARCH ->
            LibraryBooksOrdering.BroadSearch(LibrarySearchOrdering.TITLE)
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
    suspend fun load(client: AuthenticatedLibraryBooksClient): LibraryPage<CompactBook> =
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
        client: AuthenticatedLibraryBooksClient
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
        fun from(state: LibraryBooksState, scope: LibraryScope, page: Int) = LibraryBooksRequest(
            state.mode,
            state.committedQuery,
            state.ordering,
            scope,
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
