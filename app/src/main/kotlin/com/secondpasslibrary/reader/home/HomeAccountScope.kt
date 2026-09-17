package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey

internal data class HomeAccountScope(val account: AccountLocalScope) {
    constructor(serverOrigin: String, profileId: String) :
        this(AccountLocalScope.from(serverOrigin, profileId))

    val serverOrigin: String
        get() = account.serverOrigin
    val profileId: String
        get() = account.profileId
    internal val storageKey = HomeAccountScopeKey.from(account)
}
