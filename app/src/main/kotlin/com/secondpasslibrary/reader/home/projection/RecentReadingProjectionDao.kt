package com.secondpasslibrary.reader.home.projection

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
internal abstract class RecentReadingProjectionDao {
    @Query(
        "SELECT * FROM home_projection_snapshots " +
            "WHERE accountKey = :accountKey AND projectionKind = 'recent_reading' " +
            "AND variantKey = :variantKey"
    )
    abstract suspend fun snapshot(
        accountKey: String,
        variantKey: String
    ): HomeProjectionSnapshotEntity?

    @Query(
        "SELECT * FROM home_recent_reading_items " +
            "WHERE accountKey = :accountKey AND variantKey = :variantKey " +
            "ORDER BY serverPosition"
    )
    abstract suspend fun items(
        accountKey: String,
        variantKey: String
    ): List<HomeRecentReadingEntity>

    @Transaction
    open suspend fun replace(
        snapshot: HomeProjectionSnapshotEntity,
        items: List<HomeRecentReadingEntity>
    ) {
        deleteItems(snapshot.accountKey, snapshot.variantKey)
        insertItems(items)
        insertSnapshot(snapshot)
    }

    @Query(
        "DELETE FROM home_recent_reading_items " +
            "WHERE accountKey = :accountKey AND variantKey = :variantKey"
    )
    protected abstract suspend fun deleteItems(accountKey: String, variantKey: String)

    @Insert
    protected abstract suspend fun insertItems(items: List<HomeRecentReadingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSnapshot(snapshot: HomeProjectionSnapshotEntity)
}
