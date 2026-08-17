package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedSecondPassClientFactory
import com.secondpasslibrary.client.KtorSecondPassClient
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.reader.connection.storage.DataStoreConnectionProfileStore
import com.secondpasslibrary.reader.connection.storage.KeystoreBearerCredentialStore
import dagger.Binds
import dagger.Module
import dagger.Provides
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

@Module
@InstallIn(SingletonComponent::class)
object SplClientModule {
    @Provides
    @Singleton
    fun provideKtorSecondPassClient(): KtorSecondPassClient = KtorSecondPassClient()

    @Provides
    fun provideSecondPassClient(client: KtorSecondPassClient): SecondPassClient = client

    @Provides
    fun provideAuthenticatedClientFactory(
        client: KtorSecondPassClient
    ): AuthenticatedSecondPassClientFactory = client
}
