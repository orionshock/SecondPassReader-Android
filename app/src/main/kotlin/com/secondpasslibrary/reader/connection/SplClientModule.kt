package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedSecondPassClientFactory
import com.secondpasslibrary.client.KtorSecondPassClient
import com.secondpasslibrary.client.SecondPassClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
