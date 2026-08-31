package com.secondpasslibrary.reader.library.books

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.BookAuthorSummary
import com.secondpasslibrary.client.BookSeriesSummary
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.SeriesIndex
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.design.book.COMPACT_BOOK_ROW_TAG
import com.secondpasslibrary.reader.design.book.WIDE_BOOK_ROW_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBooksResultsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wideListUsesCoverMainMetadataFactsAndTrailingActionColumns() {
        setResults(widthDp = 1100, book = richBook())

        compose.onNodeWithTag(WIDE_BOOK_ROW_TAG).assertHasClickAction()
        val cover =
            compose
                .onNodeWithContentDescription(
                    "No cover available for $LONG_TITLE",
                    useUnmergedTree = true
                )
                .fetchSemanticsNode()
        val title =
            compose.onNodeWithText(LONG_TITLE, useUnmergedTree = true).fetchSemanticsNode()
        val series =
            compose.onNodeWithText("Tablet Series · 12.50", useUnmergedTree = true)
                .fetchSemanticsNode()
        val actions = compose.onNodeWithContentDescription("Book actions").fetchSemanticsNode()

        assertTrue(title.boundsInRoot.left > cover.boundsInRoot.right)
        assertTrue(title.boundsInRoot.right <= series.boundsInRoot.left)
        assertTrue(actions.boundsInRoot.left > series.boundsInRoot.left)
        compose.onNodeWithText(LONG_AUTHOR).assertIsDisplayed()
        compose.onNodeWithText("eng · EPUB").assertIsDisplayed()
    }

    @Test
    fun narrowListKeepsCompactRowForSparseMetadata() {
        setResults(widthDp = 500, book = sparseBook())

        compose.onNodeWithTag(COMPACT_BOOK_ROW_TAG).assertHasClickAction()
        assertTrue(compose.onAllNodesWithTag(WIDE_BOOK_ROW_TAG).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Sparse Book").assertIsDisplayed()
    }

    @Test
    fun offlineDownloadedListUsesSameWideCompositionWithoutServerActions() {
        setResults(widthDp = 1100, book = richBook(), offlineDownloadedOnly = true)

        compose.onNodeWithTag(WIDE_BOOK_ROW_TAG).assertHasClickAction()
        assertTrue(
            compose
                .onAllNodesWithContentDescription("Book actions")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        compose.onNodeWithText("eng · EPUB").assertIsDisplayed()
    }

    @Test
    fun gridModeDoesNotUseEitherListRowComposition() {
        setResults(widthDp = 1100, book = richBook(), layout = LibraryBooksLayout.GRID)

        assertTrue(compose.onAllNodesWithTag(WIDE_BOOK_ROW_TAG).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag(COMPACT_BOOK_ROW_TAG).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(LONG_TITLE).assertIsDisplayed()
    }

    private fun setResults(
        widthDp: Int,
        book: CompactBook,
        offlineDownloadedOnly: Boolean = false,
        layout: LibraryBooksLayout = LibraryBooksLayout.LIST
    ) {
        compose.setContent {
            SecondPassTheme {
                Box(Modifier.width(widthDp.dp).height(500.dp)) {
                    LibraryBooksResults(
                        state =
                            LibraryBooksState(
                                offlineDownloadedOnly = offlineDownloadedOnly,
                                layout = layout,
                                books = listOf(book),
                                currentPage = 1,
                                initialLoading = false
                            ),
                        onLoadNextPage = {},
                        onRetry = {},
                        onBookSelected = {},
                        onBookAction = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun richBook() = sparseBook().copy(
    title = LONG_TITLE,
    subtitle = "A subtitle that remains subordinate",
    authors = listOf(BookAuthorSummary("author", LONG_AUTHOR)),
    series =
        BookSeriesSummary(
            "series",
            "Tablet Series",
            "Tablet Series",
            SeriesIndex.fromExactValue("12.50")
        ),
    language = "eng",
    publisher = "Second Pass Test Press"
)

private const val LONG_TITLE =
    "A very long title that remains bounded and cannot collide with trailing book facts"
private const val LONG_AUTHOR =
    "A Very Long Author Name Shared With Another Deliberately Long Contributor Name"

private fun sparseBook() = CompactBook(
    id = "book",
    title = "Sparse Book",
    sortTitle = "Sparse Book",
    subtitle = "",
    authors = emptyList(),
    series = null,
    catalogTags = emptyList(),
    language = null,
    publisher = null,
    publishedYear = null,
    publishedMonth = null,
    publishedDay = null,
    publicationDatePrecision = PublicationDatePrecision.UNSPECIFIED,
    cover = null,
    fileFormat = "epub"
)
