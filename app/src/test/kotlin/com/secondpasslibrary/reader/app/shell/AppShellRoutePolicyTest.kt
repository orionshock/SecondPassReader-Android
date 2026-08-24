package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellRoutePolicyTest {
    @Test
    fun `normal shell routes allow the edge drawer gesture`() {
        val routes: List<NavKey> =
            AppDestination.entries +
                BookDetailRoute("book-1", BookDetailReturnTarget.Home) +
                BookMarginaliaRoute(
                    "book-1",
                    MarginaliaReturnTarget.BookDetail(
                        BookDetailRoute("book-1", BookDetailReturnTarget.Home)
                    )
                )

        routes.forEach { assertTrue(it.drawerGestureEnabled) }
    }

    @Test
    fun `Reader route reserves horizontal gestures`() {
        val source = BookDetailRoute("book-1", BookDetailReturnTarget.Library)

        assertFalse(ReaderRoute("book-1", source).drawerGestureEnabled)
    }

    @Test
    fun `route can reserve horizontal gestures`() {
        assertFalse(HorizontalGestureRoute.drawerGestureEnabled)
    }
}

@Serializable
private data object HorizontalGestureRoute : NavKey, AppShellDrawerGesturePolicy {
    override val drawerGestureEnabled = false
}
