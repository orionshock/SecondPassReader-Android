package com.secondpasslibrary.reader.marginalia.history

import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext

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

internal data class MarginaliaLoadError(
    val failure: MarginaliaFailure,
    val phase: MarginaliaLoadPhase
)

internal enum class MarginaliaLoadPhase {
    INITIAL,
    NEXT_PAGE
}

internal const val READING_SESSIONS_PAGE_SIZE = 50
