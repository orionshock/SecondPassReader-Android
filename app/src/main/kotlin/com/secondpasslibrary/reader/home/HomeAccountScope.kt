package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey

internal data class HomeAccountScope(val serverOrigin: String, val profileId: String) {
    internal val storageKey = HomeAccountScopeKey.from(serverOrigin, profileId)
}
