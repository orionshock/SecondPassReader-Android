package com.secondpasslibrary.reader.home.projection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HomeProjectionModule {
    @Binds
    @Singleton
    abstract fun bindHomeProjectionStore(store: RoomHomeProjectionStore): HomeProjectionStore
}
