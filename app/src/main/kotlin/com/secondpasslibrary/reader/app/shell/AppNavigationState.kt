package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/** Owns the five account-scoped stacks independently of connection authority. */
internal class AppNavigationState(
    private val backStacks: Map<AppDestination, MutableList<AppRoute>>,
    initialDestination: AppDestination = AppDestination.Home,
    private val onDestinationSelected: (AppDestination) -> Unit = {}
) {
    var selectedDestination by mutableStateOf(initialDestination)
        private set

    val activeBackStack: MutableList<AppRoute>
        get() = backStack(selectedDestination)

    val currentRoute: AppRoute
        get() = activeBackStack.last()

    fun select(destination: AppDestination) {
        selectedDestination = destination
        onDestinationSelected(destination)
    }

    fun backStack(destination: AppDestination): MutableList<AppRoute> =
        checkNotNull(backStacks[destination]) { "No back stack exists for $destination." }

    fun push(route: AppRoute) {
        val stack = activeBackStack
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun replace(destination: AppDestination, route: AppRoute? = null) {
        val stack = backStack(destination)
        stack.clear()
        stack.add(destination)
        route?.let(stack::add)
        select(destination)
    }

    fun pop(): Boolean {
        val stack = activeBackStack
        if (stack.size == 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun removeTop(route: AppRoute) {
        val stack = activeBackStack
        if (stack.size > 1 && stack.lastOrNull() == route) {
            stack.removeAt(stack.lastIndex)
        }
    }
}

@Composable
internal fun rememberAppNavigationState(): AppNavigationState {
    val home = rememberNavBackStack(AppDestination.Home)
    val library = rememberNavBackStack(AppDestination.Library)
    val shelves = rememberNavBackStack(AppDestination.Shelves)
    val marginalia = rememberNavBackStack(AppDestination.Marginalia)
    val settings = rememberNavBackStack(AppDestination.Settings)
    var selected by rememberSaveable { mutableStateOf(AppDestination.Home) }
    return remember(home, library, shelves, marginalia, settings) {
        AppNavigationState(
            backStacks =
                mapOf(
                    AppDestination.Home to AppRouteBackStack(home),
                    AppDestination.Library to AppRouteBackStack(library),
                    AppDestination.Shelves to AppRouteBackStack(shelves),
                    AppDestination.Marginalia to AppRouteBackStack(marginalia),
                    AppDestination.Settings to AppRouteBackStack(settings)
                ),
            initialDestination = selected,
            onDestinationSelected = { selected = it }
        )
    }
}

internal fun appNavigationStateForTest(
    initialDestination: AppDestination = AppDestination.Home
): AppNavigationState = AppNavigationState(
    AppDestination.entries.associateWith { mutableListOf<AppRoute>(it) },
    initialDestination
)

/** Keeps Navigation 3's NavKey-only storage behind the app-owned closed route interface. */
private class AppRouteBackStack(private val delegate: MutableList<NavKey>) :
    AbstractMutableList<AppRoute>() {
    override val size: Int
        get() = delegate.size

    override fun get(index: Int): AppRoute = delegate[index] as AppRoute

    override fun add(index: Int, element: AppRoute) {
        delegate.add(index, element)
    }

    override fun removeAt(index: Int): AppRoute = delegate.removeAt(index) as AppRoute

    override fun set(index: Int, element: AppRoute): AppRoute =
        delegate.set(index, element) as AppRoute
}
