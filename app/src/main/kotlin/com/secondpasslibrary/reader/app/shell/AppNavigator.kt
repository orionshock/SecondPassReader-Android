package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey

internal class AppNavigator(private val backStack: MutableList<NavKey>) {
    fun select(destination: AppDestination) {
        if (backStack.lastOrNull() == destination) return
        backStack.clear()
        backStack.add(destination)
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}
