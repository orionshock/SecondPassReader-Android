package com.secondpasslibrary.reader.reader.persistence

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderPersistenceModule {
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
}
