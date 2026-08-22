package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.reader.connection.storage.DataStoreConnectionProfileStore
import com.secondpasslibrary.reader.connection.storage.KeystoreBearerCredentialStore
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
    abstract fun bindAuthenticatedClientProvider(
        provider: StoredAuthenticatedClientProvider
    ): AuthenticatedClientProvider
}
