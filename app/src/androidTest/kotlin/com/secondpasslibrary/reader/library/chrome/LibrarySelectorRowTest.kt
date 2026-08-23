package com.secondpasslibrary.reader.library.chrome

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.library.LibraryAxis
import com.secondpasslibrary.reader.library.LibraryResultKind
import com.secondpasslibrary.reader.library.LibraryState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisState
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
        axis = LibraryAxis.AUTHORS,
        resultKind = LibraryResultKind.BOOKS,
        authors =
            PagedLibraryAxisState(
                ordering = AuthorOrdering.NAME,
                selected =
                    PagedLibraryAxisDetailState(
                        "author-1",
                        LibraryAuthor("author-1", "Author", "Author", "", 1, null)
                    )
            ),
        series =
            PagedLibraryAxisState(
                ordering = SeriesOrdering.NAME,
                selected =
                    PagedLibraryAxisDetailState(
                        "series-1",
                        LibrarySeries("series-1", "Series", "Series", "", 1, null)
                    )
            )
    )
}
