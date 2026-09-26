package com.secondpasslibrary.reader.home.projection

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "home_projection_snapshots",
    primaryKeys = ["accountKey", "projectionKind", "variantKey"]
)
internal data class HomeProjectionSnapshotEntity(
    val accountKey: String,
    val projectionKind: String,
    val variantKey: String,
    val fetchedAtEpochMillis: Long
)

@Entity(
    tableName = "home_recent_reading_items",
    primaryKeys = ["accountKey", "variantKey", "serverPosition"]
)
internal data class HomeRecentReadingEntity(
    val accountKey: String,
    val variantKey: String,
    val serverPosition: Int,
    val sessionId: String,
    val sessionName: String,
    val status: String,
    val lastActivityAt: String,
    val bookId: String,
    val bookTitle: String,
    val coverUrl: String?,
    val canOpen: Boolean,
    @ColumnInfo(name = "progressCfi") val progressLocation: String?,
    val progressLocationLabel: String?,
    val progressUpdatedAt: String?
)

@Entity(
    tableName = "home_shelf_items",
    primaryKeys = ["accountKey", "variantKey", "serverPosition"]
)
internal data class HomeShelfEntity(
    val accountKey: String,
    val variantKey: String,
    val serverPosition: Int,
    val shelfId: String,
    val name: String,
    val description: String?,
    val ownerKind: String,
    val ownerId: String?,
    val ownerName: String?,
    val ownerUsername: String?,
    val ownerFirstName: String?,
    val ownerLastName: String?,
    val ownerIsPublicGroup: Boolean?,
    val ownerOtherType: String?,
    val visibility: String,
    val itemCount: Int,
    val canEdit: Boolean,
    val previewBooksPresent: Boolean
)

@Entity(
    tableName = "home_shelf_preview_books",
    primaryKeys = ["accountKey", "variantKey", "shelfId", "previewPosition"]
)
internal data class HomeShelfPreviewBookEntity(
    val accountKey: String,
    val variantKey: String,
    val shelfId: String,
    val previewPosition: Int,
    val bookId: String,
    val title: String,
    val coverUrl: String?
)
