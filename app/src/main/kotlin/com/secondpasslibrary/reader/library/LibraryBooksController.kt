package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
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
    private var requestGeneration = 0L
    private var loadJob: Job? = null
    private var preferenceJob: Job? = null

    fun initializeBrowse(profile: ConnectionProfile) {
        initialize(profile, "browse", LibraryBooksMode.BROWSE, "")
    }

    fun initializeBroadSearch(profile: ConnectionProfile, query: String) {
        initialize(profile, "broad\u0000$query", LibraryBooksMode.BROAD_SEARCH, query)
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

    private fun initialize(
        profile: ConnectionProfile,
        entry: String,
        mode: LibraryBooksMode,
        query: String
    ) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}\u0000$entry"
        if (identity == entryIdentity) return
        this.profile = profile
        entryIdentity = identity
        loadDisplayPreference()
        val ordering =
            when (mode) {
                LibraryBooksMode.BROWSE -> LibraryBooksOrdering.Browse(BookOrdering.TITLE)

                LibraryBooksMode.BROAD_SEARCH ->
                    LibraryBooksOrdering.BroadSearch(LibrarySearchOrdering.TITLE)
            }
        resetAndLoad(mode, query, ordering)
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
        if (classified == LibraryBooksFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(LibraryBooksConnectionEvent.AuthenticationRejected)
        }
    }
}

private data class LibraryBooksRequest(
    val mode: LibraryBooksMode,
    val query: String,
    val ordering: LibraryBooksOrdering,
    val page: Int,
    val pageSize: Int
) {
    suspend fun load(client: AuthenticatedSecondPassClient): LibraryPage<CompactBook> =
        when (mode) {
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
            page,
            state.pageSize
        )
    }
}

private fun Throwable.toLibraryBooksFailure(): LibraryBooksFailure = when (this) {
    is SplClientException.ServerUnreachable -> LibraryBooksFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> LibraryBooksFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> LibraryBooksFailure.PROTOCOL_INVALID

    else -> LibraryBooksFailure.OTHER
}
