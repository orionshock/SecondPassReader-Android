package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOrdering
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
    private var selectedScope: LibraryScope = LibraryScope.Global
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var entryKey: LibraryBooksEntryKey? = null
    private var requestGeneration = 0L
    private var loadJob: Job? = null
    private var preferenceJob: Job? = null
    private var unfilteredState: LibraryBooksState? = null

    fun initialize(
        profile: ConnectionProfile,
        mode: LibraryBooksMode,
        query: String,
        scope: LibraryScope,
        tagSlug: String? = null
    ) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        val nextEntryKey = LibraryBooksEntryKey(mode, query, scope, tagSlug)
        if (nextConnectionIdentity == connectionIdentity && nextEntryKey == entryKey) return
        connectionIdentity = nextConnectionIdentity
        entryKey = nextEntryKey
        this.profile = profile
        selectedScope = scope
        unfilteredState = null
        loadDisplayPreference()
        mutableState.value = mutableState.value.copy(tagSlug = tagSlug)
        resetAndLoad(mode, query, defaultOrdering(mode), filter = null)
    }

    fun showAuthorBooks(authorId: String, scope: LibraryScope) {
        showFilteredBooks(LibraryBooksFilter.Author(authorId), scope, BookOrdering.TITLE)
    }

    fun showSeriesBooks(seriesId: String, scope: LibraryScope) {
        showFilteredBooks(LibraryBooksFilter.Series(seriesId), scope, BookOrdering.SERIES_INDEX)
    }

    fun clearEntityFilter() {
        if (mutableState.value.filter == null) return
        loadJob?.cancel()
        requestGeneration += 1
        val currentLayout = mutableState.value.layout
        mutableState.value =
            unfilteredState?.copy(layout = currentLayout)
                ?: LibraryBooksState(layout = currentLayout)
        unfilteredState = null
    }

    fun activate() {
        if (profile == null || loadJob?.isActive == true ||
            mutableState.value.currentPage > 0
        ) {
            return
        }
        resetCurrentAndLoad()
    }

    fun selectScope(
        scope: LibraryScope,
        tagSlug: String? = mutableState.value.tagSlug,
        activate: Boolean = true
    ) {
        if (selectedScope == scope && mutableState.value.tagSlug == tagSlug) return
        selectedScope = scope
        entryKey = null
        mutableState.value = mutableState.value.copy(tagSlug = tagSlug)
        if (activate) {
            resetCurrentAndLoad()
        } else {
            mutableState.value = mutableState.value.invalidatedForTag(tagSlug)
        }
    }

    fun selectTag(tagSlug: String?, activate: Boolean) {
        val current = mutableState.value
        if (current.tagSlug == tagSlug) return
        entryKey = null
        unfilteredState = unfilteredState?.invalidatedForTag(tagSlug)
        mutableState.value = current.copy(tagSlug = tagSlug)
        if (activate) {
            resetCurrentAndLoad()
        } else {
            mutableState.value = mutableState.value.invalidatedForTag(tagSlug)
        }
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
        resetAndLoad(current.mode, current.committedQuery, current.ordering, current.filter)
    }

    private fun resetAndLoad(
        mode: LibraryBooksMode,
        query: String,
        ordering: LibraryBooksOrdering,
        filter: LibraryBooksFilter? = mutableState.value.filter,
        retainContent: Boolean = true
    ) {
        if (profile == null) return
        val current = mutableState.value
        loadJob?.cancel()
        requestGeneration += 1
        mutableState.value =
            LibraryBooksState(
                mode = mode,
                filter = filter,
                tagSlug = current.tagSlug,
                committedQuery = query,
                ordering = ordering,
                pageSize = current.pageSize,
                layout = current.layout,
                books = current.books.takeIf { retainContent }.orEmpty(),
                totalCount = current.totalCount.takeIf { retainContent } ?: 0,
                initialLoading = true
            )
        launchPage(1, LibraryBooksLoadPhase.INITIAL, requestGeneration)
    }

    private fun showFilteredBooks(
        filter: LibraryBooksFilter,
        scope: LibraryScope,
        ordering: BookOrdering
    ) {
        val filterId = when (filter) {
            is LibraryBooksFilter.Author -> filter.id
            is LibraryBooksFilter.Series -> filter.id
        }
        require(filterId.isNotBlank()) { "Library Books filter ID must not be blank." }
        if (mutableState.value.filter == null) unfilteredState = mutableState.value
        selectedScope = scope
        entryKey = null
        resetAndLoad(
            LibraryBooksMode.BROWSE,
            query = "",
            ordering = LibraryBooksOrdering.Browse(ordering),
            filter = filter,
            retainContent = false
        )
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

private fun LibraryBooksState.invalidatedForTag(tagSlug: String?): LibraryBooksState = copy(
    tagSlug = tagSlug,
    books = emptyList(),
    totalCount = 0,
    initialLoading = true,
    nextPageLoading = false,
    refreshing = false,
    error = null,
    hasNext = false,
    currentPage = 0
)

private data class LibraryBooksEntryKey(
    val mode: LibraryBooksMode,
    val query: String,
    val scope: LibraryScope,
    val tagSlug: String?
)
