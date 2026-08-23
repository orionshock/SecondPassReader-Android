package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.connection.AccountLocalDataCleaner
import com.secondpasslibrary.reader.connection.AccountLocalDataKey
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class HomeAccountLocalDataCleaner
@Inject
constructor(
    private val store: HomeProjectionStore
) : AccountLocalDataCleaner {
    override suspend fun purge(account: AccountLocalDataKey) {
        store.purgeAccount(HomeAccountScopeKey.from(account.serverOrigin, account.profileId))
    }
}
