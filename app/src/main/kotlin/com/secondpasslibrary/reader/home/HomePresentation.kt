package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.design.icons.AppIcon
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class ReadingStatusIndicator {
    Active,
    Closed
}

internal sealed interface BookCoverPresentation {
    data class Public(val reference: PublicBookCoverReference) : BookCoverPresentation

    data object Missing : BookCoverPresentation
}

internal data class ReadingHistoryCardModel(
    val bookId: String,
    val sessionId: String,
    val title: String,
    val sessionIdentityLabel: String,
    val locationLabel: String?,
    val statusLabel: String,
    val statusIndicator: ReadingStatusIndicator,
    val accessibilityDescription: String,
    val cover: BookCoverPresentation,
    val primaryIntent: OpenReaderIntent?,
    val unavailableOffline: Boolean = false,
    val contextActions: List<HomeNavigationIntent>
)

internal data class ShelfCardModel(
    val id: String,
    val origin: HomeShelfOrigin,
    val name: String,
    val ownerLabel: String,
    val ownerIcon: AppIcon,
    val ownerIconDescription: String,
    val itemCountLabel: String,
    val previewBooks: List<ShelfPreviewCardModel>?
)

internal data class ShelfPreviewCardModel(val title: String, val cover: BookCoverPresentation)

internal object HomePresenter {
    fun readingHistory(
        item: RecentReadingItem,
        offlineReadable: Boolean = true,
        offline: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): ReadingHistoryCardModel {
        val sessionIdentity = item.sessionIdentityLabel(zoneId, locale)
        val location = item.progress?.locationLabel?.trim()?.ifEmpty { null }
        return ReadingHistoryCardModel(
            bookId = item.book.id,
            sessionId = item.sessionId,
            title = item.book.title,
            sessionIdentityLabel = sessionIdentity,
            locationLabel = location,
            statusLabel = item.status.label,
            statusIndicator = item.status.indicator,
            accessibilityDescription =
                listOfNotNull(item.book.title, sessionIdentity, location, item.status.label)
                    .joinToString(", "),
            cover = item.book.cover.toPresentation(),
            primaryIntent =
                item.book.takeIf { (offline && offlineReadable) || (!offline && it.canOpen) }?.let {
                    OpenReaderIntent(it.id, item.sessionId, it.title)
                },
            unavailableOffline = offline && !offlineReadable,
            contextActions = item.contextActions(serverMutationsAvailable = !offline)
        )
    }

    fun shelf(shelf: ShelfSummary): ShelfCardModel {
        val owner = shelf.owner.toPresentation(shelf.canEdit)
        return ShelfCardModel(
            id = shelf.id,
            origin = shelf.owner.toHomeShelfOrigin(shelf.canEdit),
            name = shelf.name,
            ownerLabel = owner.label,
            ownerIcon = owner.icon,
            ownerIconDescription = owner.iconDescription,
            itemCountLabel = shelf.itemCount.bookCountLabel,
            previewBooks =
                shelf.previewBooks?.map { preview ->
                    ShelfPreviewCardModel(preview.title, preview.cover.toPresentation())
                }
        )
    }

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

    private fun ShelfOwner.toPresentation(canEdit: Boolean): ShelfOwnerPresentation = when (this) {
        is ShelfOwner.Group ->
            ShelfOwnerPresentation(name, AppIcon.GroupShelf, "Group shelf owner")

        is ShelfOwner.User -> {
            val name = username?.trim().orEmpty()
            if (canEdit) {
                ShelfOwnerPresentation(
                    name.ifEmpty { "Personal shelf" },
                    AppIcon.User,
                    "Personal shelf owner"
                )
            } else {
                ShelfOwnerPresentation(
                    name.ifEmpty { "Shared shelf" },
                    AppIcon.SharedShelf,
                    "Shared shelf owner"
                )
            }
        }
    }

    private fun ShelfOwner.toHomeShelfOrigin(canEdit: Boolean): HomeShelfOrigin = when (this) {
        is ShelfOwner.Group -> HomeShelfOrigin.GROUP
        is ShelfOwner.User -> if (canEdit) HomeShelfOrigin.PERSONAL else HomeShelfOrigin.SHARED
    }

    private val Int.bookCountLabel: String
        get() = "$this ${if (this == 1) "book" else "books"}"
}

private fun RecentReadingItem.sessionIdentityLabel(zoneId: ZoneId, locale: Locale): String =
    sessionName
        .trim()
        .takeIf { it.isNotEmpty() && !it.equals(book.title.trim(), ignoreCase = true) }
        ?: lastActivityAt.toReadingDateLabel(zoneId, locale)

private fun String.toReadingDateLabel(zoneId: ZoneId, locale: Locale): String {
    val instant = runCatching { Instant.parse(this) }
        .recoverCatching { OffsetDateTime.parse(this).toInstant() }
        .getOrNull() ?: return "Reading Session"
    val date = DateTimeFormatter.ofPattern("MMM d, uuuu", locale).withZone(zoneId).format(instant)
    return "Read $date"
}

private data class ShelfOwnerPresentation(
    val label: String,
    val icon: AppIcon,
    val iconDescription: String
)

private fun RecentReadingItem.contextActions(
    serverMutationsAvailable: Boolean
): List<HomeNavigationIntent> = buildList {
    add(HomeNavigationIntent.BookAction(BookCardAction.BookDetails(book.id)))
    add(HomeNavigationIntent.OpenReadingSessionDetail(sessionId))
    if (serverMutationsAvailable && status == ReadingSessionStatus.ACTIVE) {
        add(
            HomeNavigationIntent.OpenReadingSessionDetail(
                sessionId,
                ReadingSessionDetailAction.EDIT
            )
        )
        add(
            HomeNavigationIntent.OpenReadingSessionDetail(
                sessionId,
                ReadingSessionDetailAction.CLOSE
            )
        )
    }
}
