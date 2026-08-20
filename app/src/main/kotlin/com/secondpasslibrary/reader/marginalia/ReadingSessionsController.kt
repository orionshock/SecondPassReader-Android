package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
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

internal class ReadingSessionsController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ReadingSessionsState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<MarginaliaConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: String? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        this.profile = profile
        if (identity == connectionIdentity) return
        connectionIdentity = identity
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

    val authoritativeDetailSink: (ReadingSessionDetailResult) -> Unit = { detail ->
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
                request.load(clientProvider.forProfile(activeProfile))
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
    val book: com.secondpasslibrary.client.ReadingSessionBook?,
    val page: MarginaliaPage<ReadingSessionListItem>
)

private sealed interface ReadingSessionsRequest {
    suspend fun load(
        client: com.secondpasslibrary.client.AuthenticatedSecondPassClient
    ): ReadingSessionsPage

    data class Global(val options: ReadingSessionListOptions) : ReadingSessionsRequest {
        override suspend fun load(
            client: com.secondpasslibrary.client.AuthenticatedSecondPassClient
        ) = ReadingSessionsPage(null, client.marginalia.sessions.list(options))
    }

    data class Book(val bookId: String, val options: BookReadingSessionListOptions) :
        ReadingSessionsRequest {
        override suspend fun load(
            client: com.secondpasslibrary.client.AuthenticatedSecondPassClient
        ): ReadingSessionsPage {
            val result = try {
                client.marginalia.books.listSessions(bookId, options)
            } catch (_: SplClientException.BookReadingSessionHistoryNotFound) {
                if (options.page != 1) {
                    throw SplClientException.BookReadingSessionHistoryNotFound()
                }
                val bootstrap = client.marginalia.books.getActiveSession(bookId)
                if (bootstrap.activeSession != null) {
                    throw SplClientException.ProtocolInvalid("Book reading sessions")
                }
                return ReadingSessionsPage(
                    bootstrap.book,
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
            return ReadingSessionsPage(
                result.book,
                MarginaliaPage(
                    totalCount = result.sessions.totalCount,
                    results =
                        result.sessions.results.map {
                            ReadingSessionListItem(it, result.book)
                        },
                    hasNext = result.sessions.hasNext,
                    hasPrevious = result.sessions.hasPrevious,
                    page = result.sessions.page,
                    pageSize = result.sessions.pageSize
                )
            )
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

private fun ReadingSessionStatusFilter.toSdkStatus(): ReadingSessionStatus? = when (this) {
    ReadingSessionStatusFilter.ALL -> null
    ReadingSessionStatusFilter.ACTIVE -> ReadingSessionStatus.ACTIVE
    ReadingSessionStatusFilter.CLOSED -> ReadingSessionStatus.CLOSED
}
