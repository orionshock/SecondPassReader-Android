package com.secondpasslibrary.reader.home.projection

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        HomeProjectionSnapshotEntity::class,
        HomeRecentReadingEntity::class,
        HomeShelfEntity::class,
        HomeShelfPreviewBookEntity::class
    ],
    version = 1,
    exportSchema = true
)
internal abstract class SecondPassReaderDatabase : RoomDatabase() {
    abstract fun recentReadingProjectionDao(): RecentReadingProjectionDao

    abstract fun shelfProjectionDao(): ShelfProjectionDao
}
