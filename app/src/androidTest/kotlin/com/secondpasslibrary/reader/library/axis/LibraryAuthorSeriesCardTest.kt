package com.secondpasslibrary.reader.library.axis

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryAuthorSeriesCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun previewAreaUsesTheEntityRowsSingleAction() {
        val selections = mutableListOf<String>()
        compose.setContent {
            SecondPassTheme {
                Column {
                    LibraryAuthorSeriesCard(card("author-1", "Author"), "author", selections::add)
                    LibraryAuthorSeriesCard(
                        card("series-1", "Dresden Files", previewCount = 12),
                        "series",
                        selections::add
                    )
                }
            }
        }

        compose
            .onNodeWithContentDescription("Open author Author")
            .assertHasClickAction()
            .performClick()
        compose
            .onNodeWithContentDescription("Open series Dresden Files")
            .assertHasClickAction()
            .performTouchInput {
                val previewArea = Offset(width * 0.9f, height * 0.5f)
                down(previewArea)
                up()
            }

        compose.runOnIdle { assertEquals(listOf("author-1", "series-1"), selections) }
        assertTrue(
            compose
                .onAllNodesWithContentDescription("Cover of Preview 1")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun selectedAuthorOrSeriesDescriptionRendersRichTextWithoutMarkup() {
        compose.setContent {
            SecondPassTheme {
                SelectedAuthorSeriesHeader(
                    LibraryAuthorSeriesDetailPresentation.Content(
                        "author-1",
                        "<p>Writer &amp; <em>historian</em>.</p>" +
                            "<ul><li>First work</li><li>Second work</li></ul>"
                    ),
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithText(
            "Writer & historian.\n\u2022 First work\n\u2022 Second work"
        ).assertExists()
    }

    private fun card(id: String, name: String, previewCount: Int = 1) =
        LibraryAuthorSeriesCardPresentation(
            id = id,
            name = name,
            bookCountLabel = "$previewCount books",
            previews =
                LibraryAuthorSeriesPreviewBooksPresentation.Returned(
                    (1..previewCount).map { index ->
                        LibraryAuthorSeriesPreviewBookPresentation(
                            id = "book-$index",
                            title = "Preview $index",
                            cover = null
                        )
                    }
                )
        )
}
