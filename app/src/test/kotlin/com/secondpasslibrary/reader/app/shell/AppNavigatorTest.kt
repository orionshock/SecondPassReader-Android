package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun `top-level selection replaces the current destination`() {
        val backStack = mutableListOf<NavKey>(AppDestination.Home)
        val navigator = AppNavigator(backStack)

        navigator.select(AppDestination.Library)
        navigator.select(AppDestination.Settings)

        assertEquals(listOf(AppDestination.Settings), backStack)
    }

    @Test
    fun `selecting the current destination does not duplicate it`() {
        val backStack = mutableListOf<NavKey>(AppDestination.Home)
        val navigator = AppNavigator(backStack)

        navigator.select(AppDestination.Home)

        assertEquals(listOf(AppDestination.Home), backStack)
    }
}
