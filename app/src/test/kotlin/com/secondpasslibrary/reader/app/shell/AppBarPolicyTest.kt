package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.design.components.AppBarNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppBarPolicyTest {
    @Test
    fun `root destinations use menu and no context`() {
        listOf(AppDestination.Home, AppDestination.Library).forEach { destination ->
            val presentation = destination.rootAppBarPresentation()

            assertEquals(AppBarNavigation.MENU, presentation.navigation)
            assertEquals(destination.label, presentation.title)
            assertNull(presentation.context)
        }
    }

    @Test
    fun `Book Detail origin maps to one truthful context`() {
        assertEquals("Library", BookDetailReturnTarget.Library.appBarContextLabel())
        assertEquals("Home", BookDetailReturnTarget.Home.appBarContextLabel())
        assertEquals(
            "Shelves",
            BookDetailReturnTarget.ShelfDetail(
                "shelf-1",
                ShelfCollectionOrigin.PERSONAL
            ).appBarContextLabel()
        )
    }
}
