package com.secondpasslibrary.reader.storage.database

import android.content.Context
import androidx.room3.Room
import com.secondpasslibrary.reader.home.projection.HomeProjectionCleanupDao
import com.secondpasslibrary.reader.home.projection.RecentReadingProjectionDao
import com.secondpasslibrary.reader.home.projection.ShelfProjectionDao
import com.secondpasslibrary.reader.reader.persistence.LocalReaderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("MagicNumber")
private val OBSOLETE_DEVELOPMENT_SCHEMA_VERSIONS = intArrayOf(1, 2, 3, 4, 5)

@Module
@InstallIn(SingletonComponent::class)
internal object AppDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecondPassLocalDatabase =
        Room.databaseBuilder(context, SecondPassLocalDatabase::class.java, "second_pass_reader.db")
            .fallbackToDestructiveMigrationFrom(true, *OBSOLETE_DEVELOPMENT_SCHEMA_VERSIONS)
            .build()

    @Provides
    fun provideRecentReadingProjectionDao(
        database: SecondPassLocalDatabase
    ): RecentReadingProjectionDao = database.recentReadingProjectionDao()

    @Provides
    fun provideShelfProjectionDao(database: SecondPassLocalDatabase): ShelfProjectionDao =
        database.shelfProjectionDao()

    @Provides
    fun provideHomeProjectionCleanupDao(
        database: SecondPassLocalDatabase
    ): HomeProjectionCleanupDao = database.homeProjectionCleanupDao()

    @Provides
    fun provideLocalReaderDao(database: SecondPassLocalDatabase): LocalReaderDao =
        database.localReaderDao()
}
