package com.secondpasslibrary.reader.reader.session

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderSessionModule {
    @Binds
    @Singleton
    abstract fun bindReaderSessionCoordinator(
        coordinator: SplReaderSessionCoordinator
    ): ReaderSessionCoordinator

    @Binds
    @Singleton
    abstract fun bindReaderSessionReconciliation(
        reconciler: ReaderSessionReconciler
    ): ReaderSessionReconciliation
}
