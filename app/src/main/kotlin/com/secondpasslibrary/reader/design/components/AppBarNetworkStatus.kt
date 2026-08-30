package com.secondpasslibrary.reader.design.components

import androidx.compose.runtime.staticCompositionLocalOf

internal enum class AppBarNetworkStatus {
    SETTLED,
    SYNCING,
    OFFLINE
}

internal data class AppBarNetworkPresentation(
    val status: AppBarNetworkStatus,
    val offlineContentDescription: String = "Offline",
    val onOfflineClick: (() -> Unit)? = null
)

internal val LocalAppBarNetworkStatus = staticCompositionLocalOf {
    AppBarNetworkPresentation(AppBarNetworkStatus.SETTLED)
}
