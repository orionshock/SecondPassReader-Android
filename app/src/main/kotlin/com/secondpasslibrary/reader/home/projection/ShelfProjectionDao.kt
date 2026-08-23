package com.secondpasslibrary.reader.home.projection

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
internal abstract class ShelfProjectionDao {
    @Query(
        "SELECT EXISTS(SELECT 1 FROM home_projection_snapshots " +
            "WHERE accountKey = :accountKey AND projectionKind = 'shelves')"
    )
    abstract suspend fun hasSnapshot(accountKey: String): Boolean

    @Query(
        "SELECT * FROM home_projection_snapshots " +
            "WHERE accountKey = :accountKey AND projectionKind = 'shelves' " +
            "AND variantKey = :variantKey"
    )
    abstract suspend fun snapshot(
        accountKey: String,
        variantKey: String
    ): HomeProjectionSnapshotEntity?

    @Query(
        "SELECT * FROM home_shelf_items " +
            "WHERE accountKey = :accountKey AND variantKey = :variantKey " +
            "ORDER BY serverPosition"
    )
    abstract suspend fun shelves(accountKey: String, variantKey: String): List<HomeShelfEntity>

    @Query(
        "SELECT * FROM home_shelf_preview_books " +
            "WHERE accountKey = :accountKey AND variantKey = :variantKey " +
            "ORDER BY shelfId, previewPosition"
    )
    abstract suspend fun previews(
        accountKey: String,
        variantKey: String
    ): List<HomeShelfPreviewBookEntity>

    @Transaction
    open suspend fun replace(
        snapshot: HomeProjectionSnapshotEntity,
        shelves: List<HomeShelfEntity>,
        previews: List<HomeShelfPreviewBookEntity>
    ) {
        deletePreviews(snapshot.accountKey, snapshot.variantKey)
        deleteShelves(snapshot.accountKey, snapshot.variantKey)
        insertShelves(shelves)
        insertPreviews(previews)
        insertSnapshot(snapshot)
    }

    @Query(
        "DELETE FROM home_shelf_preview_books " +
            "WHERE accountKey = :accountKey AND variantKey = :variantKey"
    )
    protected abstract suspend fun deletePreviews(accountKey: String, variantKey: String)

    @Query(
        "DELETE FROM home_shelf_items " +
            "WHERE accountKey = :accountKey AND variantKey = :variantKey"
    )
    protected abstract suspend fun deleteShelves(accountKey: String, variantKey: String)

    @Insert
    protected abstract suspend fun insertShelves(items: List<HomeShelfEntity>)

    @Insert
    protected abstract suspend fun insertPreviews(items: List<HomeShelfPreviewBookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSnapshot(snapshot: HomeProjectionSnapshotEntity)
}
