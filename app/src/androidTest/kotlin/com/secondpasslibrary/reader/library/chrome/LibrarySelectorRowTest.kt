package com.secondpasslibrary.reader.library.chrome

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.library.LibraryResultState
import com.secondpasslibrary.reader.library.LibraryState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySelectorRowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onlySelectedEntityAxisShowsReturnIndicator() {
        compose.setContent {
            SecondPassTheme {
                LibrarySelectorRow(
                    state = selectedAuthorState(),
                    onScopeSelected = {},
                    onAxisSelected = {},
                    onRetryGroups = {},
                    tagControl = {},
                    orderingControl = {},
                    layoutControl = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Return to authors index").assertIsDisplayed()
        assertTrue(
            compose
                .onAllNodesWithContentDescription("Return to series index")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            compose
                .onAllNodesWithContentDescription("Return to books index")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    private fun selectedAuthorState() = LibraryState(
        result =
            LibraryResultState.AuthorBooks(
                PagedLibraryAxisDetailState(
                    "author-1",
                    LibraryAuthor("author-1", "Author", "Author", "", 1, null)
                ),
                books = LibraryBooksState()
            )
    )
}
