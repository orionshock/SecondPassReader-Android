package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinates destructive cleanup across app-owned account-scoped stores. */
@Singleton
internal class AppAccountLocalDataCleaner @Inject constructor(
    private val home: HomeProjectionStore,
    private val reader: LocalReaderStateStore
) : AccountLocalDataCleaner {
    override suspend fun purge(account: AccountLocalDataKey) {
        home.purgeAccount(HomeAccountScopeKey.from(account.serverOrigin, account.profileId))
        reader.purgeAccount(LocalReaderAccountKey.from(account.serverOrigin, account.profileId))
    }
}
