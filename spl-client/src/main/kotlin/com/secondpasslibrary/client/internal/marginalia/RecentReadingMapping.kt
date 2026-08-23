package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.internal.transport.required

private const val RECENT_READING_CONTEXT = "recent reading"

internal fun RecentReadingResponseWire.toModel(): List<RecentReadingItem> =
    results?.map(RecentReadingItemWire::toModel) ?: invalidProtocol(RECENT_READING_CONTEXT)

private fun RecentReadingItemWire.toModel(): RecentReadingItem = RecentReadingItem(
    sessionId = id.required(RECENT_READING_CONTEXT),
    sessionName = name ?: invalidProtocol(RECENT_READING_CONTEXT),
    status = status.toReadingSessionStatus(RECENT_READING_CONTEXT),
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
