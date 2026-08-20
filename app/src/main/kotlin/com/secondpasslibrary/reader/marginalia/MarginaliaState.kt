package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionListItem

internal sealed interface MarginaliaHistoryContext {
    data object Global : MarginaliaHistoryContext

    data class Book(val bookId: String) : MarginaliaHistoryContext
}

internal sealed interface MarginaliaDestination {
    data class History(val context: MarginaliaHistoryContext) : MarginaliaDestination

    data class SessionDetail(val sessionId: String, val returnContext: MarginaliaHistoryContext) :
        MarginaliaDestination
}

internal enum class ReadingSessionStatusFilter {
    ALL,
    ACTIVE,
    CLOSED
}

internal data class ReadingSessionsState(
    val context: MarginaliaHistoryContext = MarginaliaHistoryContext.Global,
    val book: ReadingSessionBook? = null,
    val statusFilter: ReadingSessionStatusFilter = ReadingSessionStatusFilter.ALL,
    val committedQuery: String = "",
    val sessions: List<ReadingSessionListItem> = emptyList(),
    val totalCount: Int = 0,
    val pageSize: Int = READING_SESSIONS_PAGE_SIZE,
    val currentPage: Int = 0,
    val hasNext: Boolean = false,
    val initialLoading: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: MarginaliaLoadError? = null
)

internal data class ReadingSessionDetailState(
    val sessionId: String? = null,
    val detail: ReadingSessionDetailResult? = null,
    val loading: Boolean = false,
    val failure: MarginaliaFailure? = null
)

internal data class ReadingSessionAnnotationsState(
    val sessionId: String? = null,
    val annotations: List<MarginaliaAnnotation> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failure: MarginaliaFailure? = null
)

internal data class MarginaliaState(
    val destination: MarginaliaDestination =
        MarginaliaDestination.History(MarginaliaHistoryContext.Global)
)

internal sealed interface MarginaliaExternalNavigationIntent {
    data class BookDetail(val bookId: String) : MarginaliaExternalNavigationIntent

    data class Reader(val bookId: String, val sessionId: String) :
        MarginaliaExternalNavigationIntent
}

internal data class MarginaliaLoadError(
    val failure: MarginaliaFailure,
    val phase: MarginaliaLoadPhase
)

internal enum class MarginaliaLoadPhase {
    INITIAL,
    NEXT_PAGE
}

internal enum class MarginaliaFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    OTHER
}

internal sealed interface MarginaliaConnectionEvent {
    data object AuthenticationRejected : MarginaliaConnectionEvent
}

internal const val READING_SESSIONS_PAGE_SIZE = 50
