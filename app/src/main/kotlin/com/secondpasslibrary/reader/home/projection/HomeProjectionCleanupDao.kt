package com.secondpasslibrary.reader.home.projection

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
internal abstract class HomeProjectionCleanupDao {
    @Transaction
    open suspend fun purgeAccount(accountKey: String) {
        deleteShelfPreviews(accountKey)
        deleteShelves(accountKey)
        deleteRecentReading(accountKey)
        deleteSnapshots(accountKey)
    }

    @Query("DELETE FROM home_shelf_preview_books WHERE accountKey = :accountKey")
    protected abstract suspend fun deleteShelfPreviews(accountKey: String)

    @Query("DELETE FROM home_shelf_items WHERE accountKey = :accountKey")
    protected abstract suspend fun deleteShelves(accountKey: String)

    @Query("DELETE FROM home_recent_reading_items WHERE accountKey = :accountKey")
    protected abstract suspend fun deleteRecentReading(accountKey: String)

    @Query("DELETE FROM home_projection_snapshots WHERE accountKey = :accountKey")
    protected abstract suspend fun deleteSnapshots(accountKey: String)
}
