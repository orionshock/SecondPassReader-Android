package com.secondpasslibrary.reader.bookdetail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.BookAuthorSummary
import com.secondpasslibrary.client.BookSeriesSummary
import com.secondpasslibrary.client.CatalogTagSummary
import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.SeriesIndex
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDetailScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wideRichBookUsesBoundedTabletCompositionWithAllActions() {
        setBookDetail(widthDp = 1000, book = richBook())

        compose.onNodeWithTag(BOOK_DETAIL_WIDE_TAG).assertExists()
        compose
            .onAllNodesWithText("A Long and Deliberately Distinctive Book Title")
            .assertCountEquals(2)
        compose.onNodeWithText("Read Book").assertExists()
        compose.onNodeWithText("Reading Sessions").assertIsEnabled()
        compose.onNodeWithText("Add to Shelf").assertIsEnabled()
        compose.onNodeWithText("Description").assertExists()
        compose.onNodeWithText("Reference").assertExists()
    }

    @Test
    fun narrowSparseBookKeepsStackedCompositionAndCoverSemantics() {
        setBookDetail(widthDp = 420, book = sparseBook())

        compose.onNodeWithTag(BOOK_DETAIL_NARROW_TAG).assertExists()
        compose.onAllNodesWithText("Sparse Book").assertCountEquals(2)
        compose.onNodeWithContentDescription("No cover available for Sparse Book").assertExists()
        compose.onNodeWithText("Read Book").assertExists()
    }

    @Test
    fun offlineWideBookRetainsPrimaryAndSecondaryActionsWithExistingCapabilities() {
        setBookDetail(
            widthDp = 1000,
            book = richBook(),
            readAvailable = false,
            serverActionsAvailable = false
        )

        compose.onNodeWithTag(BOOK_DETAIL_WIDE_TAG).assertExists()
        compose.onNodeWithText("Read Book").assertIsNotEnabled()
        compose.onNodeWithText("Reading Sessions").assertIsNotEnabled()
        compose.onNodeWithText("Add to Shelf").assertIsNotEnabled()
    }

    @Test
    fun richHtmlDescriptionRendersAsOneReadableAccessibleTextNode() {
        val description = "<p>A first paragraph with <em>emphasis</em> &amp; meaning.</p>" +
            "<p>A second paragraph.</p>" +
            "<ul><li>One item</li><li>Another item</li></ul>"
        setBookDetail(widthDp = 1000, book = richBook().copy(description = description))

        compose.onNodeWithText(
            "A first paragraph with emphasis & meaning.\n" +
                "A second paragraph.\n\u2022 One item\n\u2022 Another item"
        ).assertExists()
        compose.onAllNodesWithText("<p>", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("<li>", substring = true).assertCountEquals(0)
    }

    @Test
    fun longStructuredDescriptionExpandsFromActualRenderedOverflow() {
        val description = (1..12).joinToString("") { "<p>Paragraph $it has readable text.</p>" }
        setBookDetail(widthDp = 1000, book = richBook().copy(description = description))

        compose.onNodeWithText("Show more").performClick()
        compose.onNodeWithText("Show less").assertExists()
        compose.onNodeWithText("Paragraph 12", substring = true).assertExists()
    }

    private fun setBookDetail(
        widthDp: Int,
        book: LibraryBookDetail,
        readAvailable: Boolean = true,
        serverActionsAvailable: Boolean = true
    ) {
        compose.setContent {
            SecondPassTheme {
                Box(Modifier.width(widthDp.dp).height(900.dp)) {
                    BookDetailScreen(
                        state = BookDetailState(detail = book),
                        appBarContext = "Library",
                        onBack = {},
                        onRetry = {},
                        onAuthorSelected = {},
                        onSeriesSelected = {},
                        onTagSelected = { _, _ -> },
                        onReadBook = {},
                        onReadingSessions = {},
                        onAddToShelf = {},
                        readAvailable = readAvailable,
                        serverActionsAvailable = serverActionsAvailable
                    )
                }
            }
        }
    }
}

private fun richBook() = sparseBook().copy(
    title = "A Long and Deliberately Distinctive Book Title",
    sortTitle = "Long and Deliberately Distinctive Book Title, A",
    subtitle = "An intentionally descriptive subtitle",
    authors = listOf(BookAuthorSummary("author", "A Very Long Author Name")),
    series = BookSeriesSummary(
        "series",
        "The Example Cycle",
        "Example Cycle, The",
        SeriesIndex.fromExactValue("2.00")
    ),
    language = "eng",
    publisher = "Second Pass Test Press",
    publishedYear = 2026,
    publicationDatePrecision = PublicationDatePrecision.YEAR,
    description = "A substantial description that proves supporting content stays connected " +
        "to the metadata column on a wide screen.",
    catalogTags = listOf(CatalogTagSummary("tag", "Reference", "reference"))
)

private fun sparseBook() = LibraryBookDetail(
    id = "book",
    title = "Sparse Book",
    sortTitle = "Sparse Book",
    subtitle = "",
    authors = emptyList(),
    series = null,
    language = null,
    publisher = null,
    publishedYear = null,
    publishedMonth = null,
    publishedDay = null,
    publicationDatePrecision = PublicationDatePrecision.UNSPECIFIED,
    cover = null,
    description = "",
    identifiers = emptyList(),
    catalogTags = emptyList(),
    file = null,
    groups = emptyList()
)
