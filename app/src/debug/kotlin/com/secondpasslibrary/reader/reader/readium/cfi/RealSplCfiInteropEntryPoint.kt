package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfileStore
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import com.secondpasslibrary.reader.reader.asset.SplReaderBookAssetResolver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access to production owners used by the opt-in real-SPL CFI proof. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RealSplCfiInteropEntryPoint {
    fun connectionProfileStore(): ConnectionProfileStore

    fun persistedAccountContextStore(): PersistedAccountContextStore

    fun authenticatedClientProvider(): AuthenticatedClientProvider

    fun readerBookAssetResolver(): SplReaderBookAssetResolver
}
