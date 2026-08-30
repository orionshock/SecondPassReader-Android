package com.secondpasslibrary.reader.home.projection

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAnnotationEntity
import com.secondpasslibrary.reader.reader.persistence.LocalReaderContinuationOutcomeEntity
import com.secondpasslibrary.reader.reader.persistence.LocalReaderDao
import com.secondpasslibrary.reader.reader.persistence.LocalReaderOutboxEntity
import com.secondpasslibrary.reader.reader.persistence.LocalReaderProgressEntity
import com.secondpasslibrary.reader.reader.persistence.LocalReaderSessionEntity

@Database(
    entities = [
        HomeProjectionSnapshotEntity::class,
        HomeRecentReadingEntity::class,
        HomeShelfEntity::class,
        HomeShelfPreviewBookEntity::class,
        LocalReaderSessionEntity::class,
        LocalReaderProgressEntity::class,
        LocalReaderAnnotationEntity::class,
        LocalReaderOutboxEntity::class,
        LocalReaderContinuationOutcomeEntity::class
    ],
    version = 6,
    exportSchema = true
)
internal abstract class SecondPassReaderDatabase : RoomDatabase() {
    abstract fun recentReadingProjectionDao(): RecentReadingProjectionDao

    abstract fun shelfProjectionDao(): ShelfProjectionDao

    abstract fun homeProjectionCleanupDao(): HomeProjectionCleanupDao

    abstract fun localReaderDao(): LocalReaderDao
}
