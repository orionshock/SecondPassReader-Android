package com.secondpasslibrary.reader.home.projection

import android.content.Context
import androidx.room3.Room
import com.secondpasslibrary.reader.connection.AccountLocalDataCleaner
import com.secondpasslibrary.reader.connection.AppAccountLocalDataCleaner
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.annotations.SplReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.persistence.LocalReaderDao
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.ReaderClosedSessionContinuationStore
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.ReaderSessionBindingStore
import com.secondpasslibrary.reader.reader.persistence.RoomLocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.RoomReaderClosedSessionContinuationStore
import com.secondpasslibrary.reader.reader.persistence.RoomReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.RoomReaderSessionBindingStore
import com.secondpasslibrary.reader.reader.sync.ReaderSyncScheduler
import com.secondpasslibrary.reader.reader.sync.WorkManagerReaderSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HomeProjectionStorageModule {
    @Binds
    @Singleton
    abstract fun bindHomeProjectionStore(store: RoomHomeProjectionStore): HomeProjectionStore

    @Binds
    @Singleton
    abstract fun bindAccountLocalDataCleaner(
        cleaner: AppAccountLocalDataCleaner
    ): AccountLocalDataCleaner

    @Binds
    @Singleton
    abstract fun bindLocalReaderStateStore(store: RoomLocalReaderStateStore): LocalReaderStateStore

    @Binds
    abstract fun bindReaderOutboxStore(store: RoomReaderOutboxStore): ReaderOutboxStore

    @Binds
    abstract fun bindReaderSessionBindingStore(
        store: RoomReaderSessionBindingStore
    ): ReaderSessionBindingStore

    @Binds
    abstract fun bindReaderClosedSessionContinuationStore(
        store: RoomReaderClosedSessionContinuationStore
    ): ReaderClosedSessionContinuationStore

    @Binds
    abstract fun bindReaderAnnotationsLoader(
        loader: SplReaderAnnotationsLoader
    ): ReaderAnnotationsLoader

    @Binds
    @Singleton
    abstract fun bindReaderSyncScheduler(
        scheduler: WorkManagerReaderSyncScheduler
    ): ReaderSyncScheduler

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SecondPassReaderDatabase =
            Room.databaseBuilder(
                context,
                SecondPassReaderDatabase::class.java,
                "second_pass_reader.db"
            ).build()

        @Provides
        fun provideRecentReadingProjectionDao(
            database: SecondPassReaderDatabase
        ): RecentReadingProjectionDao = database.recentReadingProjectionDao()

        @Provides
        fun provideShelfProjectionDao(database: SecondPassReaderDatabase): ShelfProjectionDao =
            database.shelfProjectionDao()

        @Provides
        fun provideHomeProjectionCleanupDao(
            database: SecondPassReaderDatabase
        ): HomeProjectionCleanupDao = database.homeProjectionCleanupDao()

        @Provides
        fun provideLocalReaderDao(database: SecondPassReaderDatabase): LocalReaderDao =
            database.localReaderDao()
    }
}
