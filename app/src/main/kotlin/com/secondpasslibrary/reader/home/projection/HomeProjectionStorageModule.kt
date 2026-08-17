package com.secondpasslibrary.reader.home.projection

import android.content.Context
import androidx.room3.Room
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
    }
}
