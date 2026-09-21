package com.secondpasslibrary.reader.shelves.collection

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPreviewBook
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.shelves.toCardPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelfCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wholeCardHasOneOpenActionAndDecorativePreviewSemantics() {
        val shelf = shelf("shared", previews = previewBooks(10), itemCount = 10)
        compose.setContent {
            SecondPassTheme {
                ShelfCard(shelf.toCardPresentation(), onClick = {})
            }
        }

        compose.onNodeWithContentDescription("Open shelf Shared shelf").assertHasClickAction()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("shelf-preview-book-0", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun fixedCardHeightDoesNotDependOnPreviewCount() {
        val sparse = shelf("sparse", previews = previewBooks(1), itemCount = 1)
        val dense = shelf("dense", previews = previewBooks(24), itemCount = 40)
        compose.setContent {
            SecondPassTheme {
                Column(Modifier.width(900.dp)) {
                    ShelfCard(sparse.toCardPresentation(), onClick = {})
                    ShelfCard(dense.toCardPresentation(), onClick = {})
                }
            }
        }

        val sparseBounds = compose.onNodeWithTag(sparse.toCardPresentation().cardTestTag)
            .fetchSemanticsNode().boundsInRoot
        val denseBounds = compose.onNodeWithTag(dense.toCardPresentation().cardTestTag)
            .fetchSemanticsNode().boundsInRoot

        assertEquals(sparseBounds.height, denseBounds.height, 0.5f)
    }

    @Test
    fun compactCardClipsPreviewsAndUsesSharedNullCoverPlaceholderBounds() {
        val shelf = shelf("compact", previews = previewBooks(10), itemCount = 10)
        compose.setContent {
            SecondPassTheme {
                Column(Modifier.width(360.dp)) {
                    ShelfCard(
                        shelf.toCardPresentation(),
                        onClick = {}
                    )
                }
            }
        }

        val first = compose.onNodeWithTag("shelf-preview-book-0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("shelf-preview-book-1", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText("+8", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(first.width > 0f)
        assertTrue(first.height > first.width)
    }

    private fun shelf(id: String, previews: List<ShelfPreviewBook>, itemCount: Int) = Shelf(
        id = id,
        name = "${id.replaceFirstChar(Char::uppercase)} shelf",
        description = null,
        owner = ShelfOwner.User("owner", "alex"),
        visibility = ShelfVisibility.LISTED,
        itemCount = itemCount,
        canEdit = false,
        createdBy = null,
        createdAt = "2026-09-21T00:00:00Z",
        updatedAt = "2026-09-21T00:00:00Z",
        matchedItemId = null,
        previewBooks = previews
    )

    private fun previewBooks(count: Int) = List(count) { index ->
        ShelfPreviewBook("book-$index", "Book $index", cover = null)
    }
}
