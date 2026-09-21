package com.secondpasslibrary.reader.app.storage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccountLocalStorageModule {
    @Binds
    abstract fun bindLegacyReset(reset: LegacyAccountStateResetImpl): LegacyAccountStateReset

    @Binds
    abstract fun bindOfflineBookCoverSource(
        adapter: CoilOfflineBookCoverAdapter
    ): OfflineBookCoverSource

    @Binds
    @Singleton
    abstract fun bindAccountLocalDataLifecycle(
        repository: AccountLocalDataRepository
    ): AccountLocalDataLifecycle

    @Binds
    @Singleton
    abstract fun bindAccountLocalBookCatalog(
        repository: AccountLocalDataRepository
    ): AccountLocalBookCatalog

    @Binds
    @Singleton
    abstract fun bindAccountLocalDownloadRepository(
        repository: AccountLocalDataRepository
    ): AccountLocalDownloadRepository
}
