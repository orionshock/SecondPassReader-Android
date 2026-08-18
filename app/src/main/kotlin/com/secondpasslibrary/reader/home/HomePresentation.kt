package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfSummary

internal enum class ReadingStatusIndicator {
    Active,
    Closed
}

internal sealed interface BookCoverPresentation {
    data class Public(val reference: PublicBookCoverReference) : BookCoverPresentation

    data object Missing : BookCoverPresentation
}

internal data class ReadingHistoryCardModel(
    val title: String,
    val sessionName: String?,
    val locationLabel: String?,
    val statusLabel: String,
    val statusIndicator: ReadingStatusIndicator,
    val cover: BookCoverPresentation
)

internal data class ShelfCardModel(
    val name: String,
    val ownerLabel: String,
    val itemCountLabel: String,
    val previewBooks: List<ShelfPreviewCardModel>?
)

internal data class ShelfPreviewCardModel(val title: String, val cover: BookCoverPresentation)

internal object HomePresenter {
    fun readingHistory(item: RecentReadingItem) = ReadingHistoryCardModel(
        title = item.book.title,
        sessionName = item.sessionName.useIfDistinctFrom(item.book.title),
        locationLabel = item.progress?.locationLabel?.trim()?.ifEmpty { null },
        statusLabel = item.status.label,
        statusIndicator = item.status.indicator,
        cover = item.book.cover.toPresentation()
    )

    fun shelf(shelf: ShelfSummary) = ShelfCardModel(
        name = shelf.name,
        ownerLabel = shelf.owner.displayName,
        itemCountLabel = shelf.itemCount.bookCountLabel,
        previewBooks =
            shelf.previewBooks?.map { preview ->
                ShelfPreviewCardModel(preview.title, preview.cover.toPresentation())
            }
    )

    private fun String.useIfDistinctFrom(other: String): String? =
        trim().takeIf { it.isNotEmpty() && !it.equals(other.trim(), ignoreCase = true) }

    private fun PublicBookCoverReference?.toPresentation(): BookCoverPresentation =
        this?.let(BookCoverPresentation::Public) ?: BookCoverPresentation.Missing

    private val ReadingSessionStatus.label: String
        get() = when (this) {
            ReadingSessionStatus.ACTIVE -> "Active"
            ReadingSessionStatus.CLOSED -> "Closed"
        }

    private val ReadingSessionStatus.indicator: ReadingStatusIndicator
        get() = when (this) {
            ReadingSessionStatus.ACTIVE -> ReadingStatusIndicator.Active
            ReadingSessionStatus.CLOSED -> ReadingStatusIndicator.Closed
        }

    private val ShelfOwner.displayName: String
        get() = when (this) {
            is ShelfOwner.Group -> name
            is ShelfOwner.User -> username?.trim().orEmpty().ifEmpty { "Personal shelf" }
        }

    private val Int.bookCountLabel: String
        get() = "$this ${if (this == 1) "book" else "books"}"
}
