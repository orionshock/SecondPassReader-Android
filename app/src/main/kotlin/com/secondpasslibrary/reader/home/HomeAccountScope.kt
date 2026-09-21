package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey

internal data class HomeAccountScope(val account: AccountLocalScope) {
    constructor(serverId: String, profileId: String) :
        this(AccountLocalScope.from(serverId, profileId))

    val serverId: String
        get() = account.serverId
    val profileId: String
        get() = account.profileId
    internal val storageKey = HomeAccountScopeKey.from(account)
}
