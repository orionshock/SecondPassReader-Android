package com.secondpasslibrary.reader.connection

internal data class AccountLocalDataKey(val serverOrigin: String, val profileId: String)

/** Removes durable, account-scoped app data without owning connection persistence. */
internal fun interface AccountLocalDataCleaner {
    suspend fun purge(account: AccountLocalDataKey)
}

internal fun LocalAccountContext.localDataKey() =
    AccountLocalDataKey(profile.serverOrigin, persistedAccount.profileId)
