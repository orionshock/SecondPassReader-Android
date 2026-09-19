package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.reader.connection.storage.DataStoreConnectionProfileStore
import com.secondpasslibrary.reader.connection.storage.DataStorePersistedAccountContextStore
import com.secondpasslibrary.reader.connection.storage.DataStoreWorkOfflineStore
import com.secondpasslibrary.reader.connection.storage.KeystoreBearerCredentialStore
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import com.secondpasslibrary.reader.connection.storage.WorkOfflineStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectionStorageModule {
    @Binds
    @Singleton
    abstract fun bindConnectionProfileStore(
        store: DataStoreConnectionProfileStore
    ): ConnectionProfileStore

    @Binds
    @Singleton
    abstract fun bindBearerCredentialStore(
        store: KeystoreBearerCredentialStore
    ): BearerCredentialStore

    @Binds
    @Singleton
    internal abstract fun bindPersistedAccountContextStore(
        store: DataStorePersistedAccountContextStore
    ): PersistedAccountContextStore

    @Binds
    @Singleton
    internal abstract fun bindWorkOfflineStore(store: DataStoreWorkOfflineStore): WorkOfflineStore

    @Binds
    @Singleton
    abstract fun bindAuthenticatedClientProvider(
        provider: StoredAuthenticatedClientProvider
    ): AuthenticatedClientProvider
}
