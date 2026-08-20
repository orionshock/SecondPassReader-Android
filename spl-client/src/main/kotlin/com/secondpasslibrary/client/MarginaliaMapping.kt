package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.BookReadingSessionPageWire
import com.secondpasslibrary.client.internal.MarginaliaBookPageWire
import com.secondpasslibrary.client.internal.MarginaliaBookWire
import com.secondpasslibrary.client.internal.MarginaliaSeriesWire
import com.secondpasslibrary.client.internal.ReadingProgressWire
import com.secondpasslibrary.client.internal.ReadingSessionDetailWire
import com.secondpasslibrary.client.internal.ReadingSessionPageWire
import com.secondpasslibrary.client.internal.ReadingSessionWire
import com.secondpasslibrary.client.internal.RecentReadingBookWire

private const val BOOK_CONTEXT = "marginalia book"
private const val BOOK_PAGE_CONTEXT = "marginalia book page"
private const val SESSION_CONTEXT = "reading session"
private const val SESSION_PAGE_CONTEXT = "reading session page"
private const val SESSION_DETAIL_CONTEXT = "reading session detail"

internal fun MarginaliaBookPageWire.toModel(
    page: Int,
    pageSize: Int
): MarginaliaPage<MarginaliaBookSummary> = marginaliaPage(
    count,
    next,
    previous,
    results,
    page,
    pageSize,
    BOOK_PAGE_CONTEXT,
    MarginaliaBookWire::toModel
)

internal fun MarginaliaBookWire.toModel(): MarginaliaBookSummary = MarginaliaBookSummary(
    id = id.required(BOOK_CONTEXT),
    title = title.required(BOOK_CONTEXT),
    authors = authors?.map { it.toModel(BOOK_CONTEXT) } ?: invalidProtocol(BOOK_CONTEXT),
    series = series?.toModel(),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer),
    canOpen = canOpen ?: invalidProtocol(BOOK_CONTEXT),
    sessionCount = sessionCount.nonNegative(BOOK_CONTEXT),
    activeSessionCount = activeSessionCount.nonNegative(BOOK_CONTEXT),
    lastActivityAt = lastActivityAt
)

internal fun ReadingSessionPageWire.toModel(
    page: Int,
    pageSize: Int
): MarginaliaPage<ReadingSessionListItem> = marginaliaPage(
    count,
    next,
    previous,
    results,
    page,
    pageSize,
    SESSION_PAGE_CONTEXT
) { wire ->
    ReadingSessionListItem(
        session = wire.toSummary(),
        book = wire.book?.toBookModel() ?: invalidProtocol(SESSION_PAGE_CONTEXT)
    )
}

internal fun BookReadingSessionPageWire.toModel(
    page: Int,
    pageSize: Int
): BookReadingSessionHistory = BookReadingSessionHistory(
    book = context?.book?.toBookModel() ?: invalidProtocol(SESSION_PAGE_CONTEXT),
    sessions = marginaliaPage(
        count,
        next,
        previous,
        results,
        page,
        pageSize,
        SESSION_PAGE_CONTEXT,
        ReadingSessionWire::toSummary
    )
)

internal fun ReadingSessionDetailWire.toModel(): ReadingSessionDetailResult {
    val wireSession = session ?: invalidProtocol(SESSION_DETAIL_CONTEXT)
    return ReadingSessionDetailResult(
        book = context?.book?.toBookModel() ?: invalidProtocol(SESSION_DETAIL_CONTEXT),
        session = ReadingSessionDetail(wireSession.toSummary(), wireSession.progress?.toModel())
    )
}

internal fun ReadingSessionWire.toSummary(): ReadingSessionSummary = ReadingSessionSummary(
    id = id.required(SESSION_CONTEXT),
    name = name ?: invalidProtocol(SESSION_CONTEXT),
    notes = notes ?: invalidProtocol(SESSION_CONTEXT),
    status = status.toReadingSessionStatus(SESSION_CONTEXT),
    startedAt = startedAt.required(SESSION_CONTEXT),
    closedAt = closedAt,
    updatedAt = updatedAt.required(SESSION_CONTEXT),
    lastActivityAt = lastActivityAt.required(SESSION_CONTEXT),
    annotationCount = annotationCount.nonNegative(SESSION_CONTEXT)
)

internal fun RecentReadingBookWire.toBookModel(): ReadingSessionBook = ReadingSessionBook(
    id = id.required(SESSION_CONTEXT),
    title = title.required(SESSION_CONTEXT),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer),
    canOpen = canOpen ?: invalidProtocol(SESSION_CONTEXT)
)

internal fun ReadingProgressWire.toModel(): ReadingProgress = ReadingProgress(
    cfi = cfi.boundedOpaque(MAX_CFI_LENGTH, SESSION_CONTEXT),
    locationLabel = locationLabel.boundedNullable(MAX_LOCATION_LABEL_LENGTH, SESSION_CONTEXT),
    updatedAt = updatedAt.required(SESSION_CONTEXT)
)

internal fun String?.toReadingSessionStatus(context: String): ReadingSessionStatus = when (this) {
    "active" -> ReadingSessionStatus.ACTIVE
    "closed" -> ReadingSessionStatus.CLOSED
    else -> invalidProtocol(context)
}

private fun MarginaliaSeriesWire.toModel() = MarginaliaBookSeries(
    id = id.required(BOOK_CONTEXT),
    name = name.required(BOOK_CONTEXT),
    seriesIndex = seriesIndex?.let { SeriesIndex.fromServer(it, BOOK_CONTEXT) }
)
