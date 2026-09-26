package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.MAX_LOCATION_LABEL_LENGTH
import com.secondpasslibrary.client.MAX_MARGINALIA_LOCATION_LENGTH
import com.secondpasslibrary.client.MarginaliaBookSeries
import com.secondpasslibrary.client.MarginaliaBookSummary
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.ReadingSessionDetail
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.ReadingSessionSummary
import com.secondpasslibrary.client.SeriesIndex
import com.secondpasslibrary.client.internal.library.BookAuthorWire
import com.secondpasslibrary.client.internal.library.toModel
import com.secondpasslibrary.client.internal.transport.boundedNullable
import com.secondpasslibrary.client.internal.transport.boundedOpaque
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.internal.transport.required

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
    location = location.boundedOpaque(MAX_MARGINALIA_LOCATION_LENGTH, SESSION_CONTEXT),
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
