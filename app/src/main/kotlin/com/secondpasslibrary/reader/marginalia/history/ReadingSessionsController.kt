package com.secondpasslibrary.reader.marginalia.history

import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import com.secondpasslibrary.reader.marginalia.toMarginaliaFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // Explicit history intents and parent reconciliation stay named.
internal class ReadingSessionsController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope,
    private val bookHistoryLoader: BookScopedReadingSessionHistoryLoader =
        BookScopedReadingSessionHistoryLoader()
) {
    private val mutableState = MutableStateFlow(ReadingSessionsState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<MarginaliaConnectionEvent>(Channel.BUFFERED)
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
        mutableState.value = ReadingSessionsState()
    }

    fun enter(context: MarginaliaHistoryContext, resetFilters: Boolean = false) {
        require(context !is MarginaliaHistoryContext.Book || context.bookId.isNotBlank()) {
            "Book ID must not be blank."
        }
        if (!resetFilters && state.value.context == context && state.value.currentPage > 0) return
        resetAndLoad(
            context,
            if (resetFilters) ReadingSessionStatusFilter.ALL else state.value.statusFilter,
            if (resetFilters) "" else state.value.committedQuery
        )
    }

    fun changeStatus(filter: ReadingSessionStatusFilter) {
        if (state.value.statusFilter == filter) return
        resetAndLoad(state.value.context, filter, state.value.committedQuery)
    }

    fun commitSearch(query: String) {
        val committed = query.trim()
        if (state.value.committedQuery == committed) return
        resetAndLoad(state.value.context, state.value.statusFilter, committed)
    }

    fun loadNextPage() {
        val current = state.value
        if (loadJob?.isActive == true || current.currentPage == 0 || !current.hasNext) return
        launchPage(current.currentPage + 1, MarginaliaLoadPhase.NEXT_PAGE, generation)
    }

    fun retry() {
        when (state.value.error?.phase) {
            MarginaliaLoadPhase.INITIAL ->
                resetAndLoad(
                    state.value.context,
                    state.value.statusFilter,
                    state.value.committedQuery
                )

            MarginaliaLoadPhase.NEXT_PAGE -> loadNextPage()

            null -> Unit
        }
    }

    fun close() = loadJob?.cancel()

    fun reconcileAuthoritativeUpdate(detail: ReadingSessionDetailResult) {
        val current = state.value
        val index = current.sessions.indexOfFirst { it.session.id == detail.session.summary.id }
        if (index >= 0) {
            val matchesFilter = when (current.statusFilter) {
                ReadingSessionStatusFilter.ALL -> true

                ReadingSessionStatusFilter.ACTIVE ->
                    detail.session.summary.status == ReadingSessionStatus.ACTIVE

                ReadingSessionStatusFilter.CLOSED ->
                    detail.session.summary.status == ReadingSessionStatus.CLOSED
            }
            val sessions = current.sessions.toMutableList()
            if (matchesFilter) {
                sessions[index] = ReadingSessionListItem(detail.session.summary, detail.book)
            } else {
                sessions.removeAt(index)
            }
            mutableState.value = current.copy(
                sessions = sessions,
                totalCount = if (matchesFilter) {
                    current.totalCount
                } else {
                    (current.totalCount - 1).coerceAtLeast(0)
                }
            )
        }
    }

    private fun resetAndLoad(
        context: MarginaliaHistoryContext,
        statusFilter: ReadingSessionStatusFilter,
        query: String
    ) {
        if (profile == null) return
        loadJob?.cancel()
        generation += 1
        mutableState.value =
            ReadingSessionsState(
                context = context,
                statusFilter = statusFilter,
                committedQuery = query,
                initialLoading = true
            )
        launchPage(1, MarginaliaLoadPhase.INITIAL, generation)
    }

    private fun launchPage(page: Int, phase: MarginaliaLoadPhase, activeGeneration: Long) {
        val activeProfile = profile ?: return
        val request = ReadingSessionsRequest.from(state.value, page)
        mutableState.value =
            state.value.copy(
                initialLoading = phase == MarginaliaLoadPhase.INITIAL,
                nextPageLoading = phase == MarginaliaLoadPhase.NEXT_PAGE,
                error = null
            )
        loadJob = coroutineScope.launch {
            val result = runCatching {
                request.load(
                    clientProvider.forProfile(activeProfile),
                    bookHistoryLoader,
                    phase
                )
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { applyPage(it, phase) },
                onFailure = { applyFailure(it, phase) }
            )
        }
    }

    private fun applyPage(result: ReadingSessionsPage, phase: MarginaliaLoadPhase) {
        val current = state.value
        mutableState.value =
            current.copy(
                book = result.book ?: current.book,
                sessions =
                    if (phase == MarginaliaLoadPhase.NEXT_PAGE) {
                        current.sessions + result.page.results
                    } else {
                        result.page.results
                    },
                totalCount = result.page.totalCount,
                currentPage = result.page.page,
                hasNext = result.page.hasNext,
                initialLoading = false,
                nextPageLoading = false,
                error = null
            )
    }

    private fun applyFailure(failure: Throwable, phase: MarginaliaLoadPhase) {
        val classified = failure.toMarginaliaFailure()
        mutableState.value =
            state.value.copy(
                initialLoading = false,
                nextPageLoading = false,
                error = MarginaliaLoadError(classified, phase)
            )
        if (classified == MarginaliaFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(MarginaliaConnectionEvent.AuthenticationRejected)
        }
    }
}

private data class ReadingSessionsPage(
    val book: ReadingSessionBook?,
    val page: MarginaliaPage<ReadingSessionListItem>
)

private sealed interface ReadingSessionsRequest {
    suspend fun load(
        client: com.secondpasslibrary.client.AuthenticatedSecondPassClient,
        bookHistoryLoader: BookScopedReadingSessionHistoryLoader,
        phase: MarginaliaLoadPhase
    ): ReadingSessionsPage

    data class Global(val options: ReadingSessionListOptions) : ReadingSessionsRequest {
        override suspend fun load(
            client: com.secondpasslibrary.client.AuthenticatedSecondPassClient,
            bookHistoryLoader: BookScopedReadingSessionHistoryLoader,
            phase: MarginaliaLoadPhase
        ) = ReadingSessionsPage(null, client.marginalia.sessions.list(options))
    }

    data class Book(val bookId: String, val options: BookReadingSessionListOptions) :
        ReadingSessionsRequest {
        override suspend fun load(
            client: com.secondpasslibrary.client.AuthenticatedSecondPassClient,
            bookHistoryLoader: BookScopedReadingSessionHistoryLoader,
            phase: MarginaliaLoadPhase
        ): ReadingSessionsPage {
            if (phase == MarginaliaLoadPhase.INITIAL) {
                return when (
                    val result = bookHistoryLoader.loadInitial(
                        client.marginalia.books,
                        bookId,
                        options
                    )
                ) {
                    is BookScopedReadingSessionHistoryLoadResult.LinkedHistory ->
                        result.history.toReadingSessionsPage()

                    is BookScopedReadingSessionHistoryLoadResult.VisibleBookWithoutHistory ->
                        ReadingSessionsPage(
                            result.book,
                            MarginaliaPage(
                                totalCount = 0,
                                results = emptyList(),
                                hasNext = false,
                                hasPrevious = false,
                                page = options.page,
                                pageSize = options.pageSize
                            )
                        )
                }
            }
            return client.marginalia.books.listSessions(bookId, options).toReadingSessionsPage()
        }
    }

    companion object {
        fun from(state: ReadingSessionsState, page: Int): ReadingSessionsRequest {
            val status = state.statusFilter.toSdkStatus()
            val query = state.committedQuery.ifBlank { null }
            return when (val context = state.context) {
                MarginaliaHistoryContext.Global ->
                    Global(ReadingSessionListOptions(status, query, null, page, state.pageSize))

                is MarginaliaHistoryContext.Book ->
                    Book(
                        context.bookId,
                        BookReadingSessionListOptions(status, query, page, state.pageSize)
                    )
            }
        }
    }
}

private fun BookReadingSessionHistory.toReadingSessionsPage() = ReadingSessionsPage(
    book,
    MarginaliaPage(
        totalCount = sessions.totalCount,
        results = sessions.results.map { ReadingSessionListItem(it, book) },
        hasNext = sessions.hasNext,
        hasPrevious = sessions.hasPrevious,
        page = sessions.page,
        pageSize = sessions.pageSize
    )
)

private fun ReadingSessionStatusFilter.toSdkStatus(): ReadingSessionStatus? = when (this) {
    ReadingSessionStatusFilter.ALL -> null
    ReadingSessionStatusFilter.ACTIVE -> ReadingSessionStatus.ACTIVE
    ReadingSessionStatusFilter.CLOSED -> ReadingSessionStatus.CLOSED
}
