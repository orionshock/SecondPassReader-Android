package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.sync.ReaderSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinates destructive cleanup across app-owned account-scoped stores. */
@Singleton
internal class AppAccountLocalDataCleaner @Inject constructor(
    private val home: HomeProjectionStore,
    private val reader: LocalReaderStateStore,
    private val readerSync: ReaderSyncScheduler
) : AccountLocalDataCleaner {
    override suspend fun purge(account: AccountLocalDataKey) {
        val readerAccount = LocalReaderAccountKey.from(account.serverOrigin, account.profileId)
        readerSync.cancel(readerAccount)
        home.purgeAccount(HomeAccountScopeKey.from(account.serverOrigin, account.profileId))
        reader.purgeAccount(readerAccount)
    }
}
