package com.secondpasslibrary.client

data class MarginaliaBookListOptions(
    val page: Int = DEFAULT_MARGINALIA_PAGE,
    val pageSize: Int = DEFAULT_MARGINALIA_PAGE_SIZE
) {
    init {
        validateMarginaliaPage(page, pageSize)
    }
}

data class ReadingSessionListOptions(
    val status: ReadingSessionStatus? = null,
    val q: String? = null,
    val hasAnnotations: Boolean? = null,
    val page: Int = DEFAULT_MARGINALIA_PAGE,
    val pageSize: Int = DEFAULT_MARGINALIA_PAGE_SIZE
) {
    init {
        validateMarginaliaPage(page, pageSize)
    }
}

data class BookReadingSessionListOptions(
    val status: ReadingSessionStatus? = null,
    val q: String? = null,
    val page: Int = DEFAULT_MARGINALIA_PAGE,
    val pageSize: Int = DEFAULT_MARGINALIA_PAGE_SIZE
) {
    init {
        validateMarginaliaPage(page, pageSize)
    }
}

data class MarginaliaPage<T>(
    val totalCount: Int,
    val results: List<T>,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val page: Int,
    val pageSize: Int
)

data class MarginaliaBookSummary(
    val id: String,
    val title: String,
    val authors: List<BookAuthorSummary>,
    val series: MarginaliaBookSeries?,
    val cover: PublicBookCoverReference?,
    val canOpen: Boolean,
    val sessionCount: Int,
    val activeSessionCount: Int,
    val lastActivityAt: String?
)

data class MarginaliaBookSeries(val id: String, val name: String, val seriesIndex: SeriesIndex?)

data class ReadingSessionSummary(
    val id: String,
    val name: String,
    val notes: String,
    val status: ReadingSessionStatus,
    val startedAt: String,
    val closedAt: String?,
    val updatedAt: String,
    val lastActivityAt: String,
    val annotationCount: Int
)

data class ReadingSessionDetail(val summary: ReadingSessionSummary, val progress: ReadingProgress?)

data class ReadingSessionListItem(val session: ReadingSessionSummary, val book: ReadingSessionBook)

data class ReadingSessionDetailResult(
    val book: ReadingSessionBook,
    val session: ReadingSessionDetail
)

data class BookReadingSessionHistory(
    val book: ReadingSessionBook,
    val sessions: MarginaliaPage<ReadingSessionSummary>
)

internal const val DEFAULT_MARGINALIA_PAGE = 1
internal const val DEFAULT_MARGINALIA_PAGE_SIZE = 20
private const val MAX_MARGINALIA_PAGE_SIZE = 200

private fun validateMarginaliaPage(page: Int, pageSize: Int) {
    require(page > 0) { "Marginalia page must be positive." }
    require(pageSize in 1..MAX_MARGINALIA_PAGE_SIZE) {
        "Marginalia page size must be between 1 and $MAX_MARGINALIA_PAGE_SIZE."
    }
}
