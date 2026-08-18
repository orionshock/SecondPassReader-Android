package com.secondpasslibrary.reader.home.projection

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPreviewBook
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.client.ShelfVisibility

internal fun RecentReadingItem.toEntity(
    account: HomeAccountScopeKey,
    variant: HomeRecentReadingVariant,
    position: Int
) = HomeRecentReadingEntity(
    accountKey = account.value,
    variantKey = variant.storageKey,
    serverPosition = position,
    sessionId = sessionId,
    sessionName = sessionName,
    status = status.storageValue,
    lastActivityAt = lastActivityAt,
    bookId = book.id,
    bookTitle = book.title,
    coverUrl = book.cover?.url,
    canOpen = book.canOpen,
    progressCfi = progress?.cfi,
    progressLocationLabel = progress?.locationLabel,
    progressUpdatedAt = progress?.updatedAt
)

internal fun HomeRecentReadingEntity.toModel() = RecentReadingItem(
    sessionId = sessionId,
    sessionName = sessionName,
    status = status.toReadingStatus(),
    lastActivityAt = lastActivityAt,
    book =
        RecentReadingBook(
            id = bookId,
            title = bookTitle,
            cover = coverUrl?.let(PublicBookCoverReference::fromAbsoluteUrl),
            canOpen = canOpen
        ),
    progress =
        progressCfi?.let { cfi ->
            ReadingProgress(
                cfi = cfi,
                locationLabel = requireNotNull(progressLocationLabel),
                updatedAt = requireNotNull(progressUpdatedAt)
            )
        }
)

internal fun ShelfSummary.toEntity(
    account: HomeAccountScopeKey,
    variant: HomeShelfVariant,
    position: Int
): HomeShelfEntity {
    val ownerFields = owner.toEntityFields()
    return HomeShelfEntity(
        accountKey = account.value,
        variantKey = variant.storageKey,
        serverPosition = position,
        shelfId = id,
        name = name,
        description = description,
        ownerKind = ownerFields.kind,
        ownerId = ownerFields.id,
        ownerName = ownerFields.name,
        ownerUsername = ownerFields.username,
        ownerFirstName = ownerFields.firstName,
        ownerLastName = ownerFields.lastName,
        ownerIsPublicGroup = ownerFields.isPublicGroup,
        ownerOtherType = ownerFields.otherType,
        visibility = visibility.storageValue,
        itemCount = itemCount,
        canEdit = canEdit,
        previewBooksPresent = previewBooks != null
    )
}

internal fun ShelfSummary.previewEntities(
    account: HomeAccountScopeKey,
    variant: HomeShelfVariant
): List<HomeShelfPreviewBookEntity> = previewBooks.orEmpty().mapIndexed { position, book ->
    HomeShelfPreviewBookEntity(
        accountKey = account.value,
        variantKey = variant.storageKey,
        shelfId = id,
        previewPosition = position,
        bookId = book.id,
        title = book.title,
        coverUrl = book.cover?.url
    )
}

internal fun HomeShelfEntity.toModel(previews: List<HomeShelfPreviewBookEntity>) = ShelfSummary(
    id = shelfId,
    name = name,
    description = description,
    owner = toShelfOwner(),
    visibility = visibility.toShelfVisibility(),
    itemCount = itemCount,
    canEdit = canEdit,
    previewBooks =
        previews.takeIf { previewBooksPresent }?.map { preview ->
            ShelfPreviewBook(
                id = preview.bookId,
                title = preview.title,
                cover = preview.coverUrl?.let(PublicBookCoverReference::fromAbsoluteUrl)
            )
        }
)

private val ReadingSessionStatus.storageValue: String
    get() = when (this) {
        ReadingSessionStatus.ACTIVE -> "active"
        ReadingSessionStatus.CLOSED -> "closed"
    }

private fun String.toReadingStatus(): ReadingSessionStatus = when (this) {
    "active" -> ReadingSessionStatus.ACTIVE
    "closed" -> ReadingSessionStatus.CLOSED
    else -> error("Invalid cached reading-session status.")
}

private data class OwnerEntityFields(
    val kind: String,
    val id: String? = null,
    val name: String? = null,
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val isPublicGroup: Boolean? = null,
    val otherType: String? = null
)

private fun ShelfOwner.toEntityFields(): OwnerEntityFields = when (this) {
    is ShelfOwner.User ->
        OwnerEntityFields(
            kind = "user",
            id = profileId,
            username = username
        )

    is ShelfOwner.Group ->
        OwnerEntityFields(
            kind = "group",
            id = id,
            name = name,
            isPublicGroup = isPublicGroup
        )
}

private fun HomeShelfEntity.toShelfOwner(): ShelfOwner = when (ownerKind) {
    "user" ->
        ShelfOwner.User(
            profileId = requireNotNull(ownerId),
            username = ownerUsername
        )

    "group" ->
        ShelfOwner.Group(
            id = requireNotNull(ownerId),
            name = requireNotNull(ownerName),
            isPublicGroup = requireNotNull(ownerIsPublicGroup)
        )

    else -> error("Invalid cached shelf-owner kind.")
}

private val ShelfVisibility.storageValue: String
    get() = when (this) {
        ShelfVisibility.PRIVATE -> "private"
        ShelfVisibility.LISTED -> "listed"
    }

private fun String.toShelfVisibility(): ShelfVisibility = when (this) {
    "private" -> ShelfVisibility.PRIVATE
    "listed" -> ShelfVisibility.LISTED
    else -> error("Invalid cached shelf visibility.")
}
