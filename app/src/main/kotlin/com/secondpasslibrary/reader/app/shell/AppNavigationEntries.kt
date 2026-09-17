package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

@Composable
internal fun retainedActiveEntries(
    navigation: AppNavigationState,
    entryProvider: (AppRoute) -> NavEntry<AppRoute>
): List<NavEntry<AppRoute>> {
    // Decorate every stack while inactive so Navigation 3 retains entry state until an actual pop.
    val home = retainedEntries(navigation.backStack(AppDestination.Home), entryProvider)
    val library = retainedEntries(navigation.backStack(AppDestination.Library), entryProvider)
    val shelves = retainedEntries(navigation.backStack(AppDestination.Shelves), entryProvider)
    val marginalia = retainedEntries(navigation.backStack(AppDestination.Marginalia), entryProvider)
    val settings = retainedEntries(navigation.backStack(AppDestination.Settings), entryProvider)
    return when (navigation.selectedDestination) {
        AppDestination.Home -> home
        AppDestination.Library -> library
        AppDestination.Shelves -> shelves
        AppDestination.Marginalia -> marginalia
        AppDestination.Settings -> settings
    }
}

@Composable
private fun retainedEntries(
    backStack: List<AppRoute>,
    entryProvider: (AppRoute) -> NavEntry<AppRoute>
): List<NavEntry<AppRoute>> = rememberDecoratedNavEntries(
    backStack = backStack,
    entryDecorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
    entryProvider = entryProvider
)
