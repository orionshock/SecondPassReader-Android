package com.secondpasslibrary.reader.home.projection

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPreviewBook
import com.secondpasslibrary.client.ShelfSummary

internal fun recentItem(
    id: String,
    status: ReadingSessionStatus = ReadingSessionStatus.ACTIVE,
    name: String = "Session $id",
    progress: ReadingProgress? = null
) = RecentReadingItem(
    sessionId = id,
    sessionName = name,
    status = status,
    lastActivityAt = "2026-08-16T12:00:00Z",
    book =
        RecentReadingBook(
            id = "book-$id",
            title = "Book $id",
            cover =
                PublicBookCoverReference.fromAbsoluteUrl(
                    "https://assets.example/$id.webp"
                ),
            canOpen = true
        ),
    progress = progress
)

internal fun shelf(id: String, count: Int, previews: List<ShelfPreviewBook>? = emptyList()) =
    ShelfSummary(
        id = id,
        name = "Shelf $id",
        description = "Description $id",
        owner = ShelfOwner.User("profile-1", "reader", "Ada", "Reader"),
        visibility = "private",
        itemCount = count,
        canEdit = true,
        previewBooks = previews
    )

internal fun preview(id: String, withCover: Boolean = true) = ShelfPreviewBook(
    id = id,
    title = "Preview $id",
    cover =
        if (withCover) {
            PublicBookCoverReference.fromAbsoluteUrl("https://assets.example/$id.png")
        } else {
            null
        }
)
