package com.secondpasslibrary.reader.connection

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
}

@Module
@InstallIn(SingletonComponent::class)
object SplClientModule {
    @Provides
    @Singleton
    fun provideSecondPassClient(): SecondPassClient = KtorSecondPassClient()
}
