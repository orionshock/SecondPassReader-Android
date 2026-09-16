package com.secondpasslibrary.reader.library.chrome

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.library.LibraryState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogTagFilterSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedTagUsesNativeStateWithoutRedundantCheckmarkDescription() {
        val fiction = LibraryCatalogTag("fiction", "Fiction", "fiction", 12)
        compose.setContent {
            SecondPassTheme {
                CatalogTagFilterSheet(
                    state =
                        LibraryState(
                            selectedTag = fiction,
                            tagSelector =
                                LibraryTagSelectorState(
                                    loaded = true,
                                    tags = listOf(fiction)
                                )
                        ),
                    onDismiss = {},
                    onTagSelected = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithText("Fiction").assertIsSelected()
        compose.onNodeWithText("All tags").assertIsNotSelected()
        assertTrue(
            compose.onAllNodesWithContentDescription("Selected").fetchSemanticsNodes().isEmpty()
        )
    }
}
