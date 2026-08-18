package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.ReadingProgressWire
import com.secondpasslibrary.client.internal.RecentReadingBookWire
import com.secondpasslibrary.client.internal.RecentReadingItemWire
import com.secondpasslibrary.client.internal.RecentReadingResponseWire

private const val RECENT_READING_CONTEXT = "recent reading"

internal fun RecentReadingResponseWire.toModel(): List<RecentReadingItem> =
    results?.map(RecentReadingItemWire::toModel) ?: invalidProtocol(RECENT_READING_CONTEXT)

private fun RecentReadingItemWire.toModel(): RecentReadingItem = RecentReadingItem(
    sessionId = id.required(RECENT_READING_CONTEXT),
    sessionName = name ?: invalidProtocol(RECENT_READING_CONTEXT),
    status =
        when (status) {
            "active" -> ReadingSessionStatus.ACTIVE
            "closed" -> ReadingSessionStatus.CLOSED
            else -> invalidProtocol(RECENT_READING_CONTEXT)
        },
    lastActivityAt = lastActivityAt.required(RECENT_READING_CONTEXT),
    book = book?.toModel() ?: invalidProtocol(RECENT_READING_CONTEXT),
    progress = progress?.toModel()
)

private fun RecentReadingBookWire.toModel(): RecentReadingBook = RecentReadingBook(
    id = id.required(RECENT_READING_CONTEXT),
    title = title.required(RECENT_READING_CONTEXT),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer),
    canOpen = canOpen ?: invalidProtocol(RECENT_READING_CONTEXT)
)

private fun ReadingProgressWire.toModel(): ReadingProgress = ReadingProgress(
    cfi = cfi.required(RECENT_READING_CONTEXT),
    locationLabel = locationLabel.required(RECENT_READING_CONTEXT),
    updatedAt = updatedAt.required(RECENT_READING_CONTEXT)
)
