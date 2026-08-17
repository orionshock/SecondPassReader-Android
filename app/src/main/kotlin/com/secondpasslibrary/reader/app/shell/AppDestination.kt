package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.design.icons.AppIcon
import kotlinx.serialization.Serializable

@Serializable
enum class AppDestination(val label: String, val icon: AppIcon) : NavKey {
    Home("Home", AppIcon.Home),
    Library("Library", AppIcon.Library),
    Shelves("Shelves", AppIcon.Shelf),
    Sessions("Sessions", AppIcon.Sessions),
    Settings("Settings", AppIcon.Settings)
}

@Serializable
data class LibrarySearchRoute(val query: String) : NavKey

internal fun NavKey.topLevelDestination(): AppDestination = when (this) {
    is LibrarySearchRoute -> AppDestination.Library
    is AppDestination -> this
    else -> AppDestination.Home
}
