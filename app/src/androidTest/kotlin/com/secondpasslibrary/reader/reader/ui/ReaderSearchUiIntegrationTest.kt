package com.secondpasslibrary.reader.reader.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.search.ReaderBookSearch
import com.secondpasslibrary.reader.reader.search.ReaderSearchResult
import com.secondpasslibrary.reader.reader.search.ReaderSearchTarget
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReaderSearchUiIntegrationTest : ReaderUiIntegrationTestSupport() {
    @Test
    fun searchResultsNavigateAndStayOpenUntilClosed() {
        val search = FakeBookSearch()
        compose.setContent {
            SecondPassTheme { ReaderTestScreen(readerReadyState(search = search)) }
        }
        screenshot("reader-search-normal")
        compose.onNodeWithContentDescription("Search book").assertIsDisplayed().performClick()
        compose.onNodeWithTag("reader_search_field").assertIsDisplayed().assertIsFocused()
            .performTextReplacement("passage")
        compose.waitUntil(5_000) { search.queries.isNotEmpty() }
        compose.onNodeWithText("2 matches shown").assertIsDisplayed()
        screenshot("reader-search-results")
        compose.onNodeWithText("before passage after").performClick()
        compose.waitUntil(5_000) { search.destinations.size == 1 }
        compose.onNodeWithText("later passage again").performClick()
        compose.waitUntil(5_000) { search.destinations.size == 2 }
        compose.onNodeWithTag("reader_search_panel").assertIsDisplayed()
        screenshot("reader-search-selected")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithTag("reader_search_panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search book").assertIsDisplayed()
        assertEquals(listOf("passage"), search.queries)
    }

    @Test
    fun noMatchesRemainInSearchPanel() {
        val search = FakeBookSearch()
        compose.setContent {
            SecondPassTheme { ReaderTestScreen(readerReadyState(search = search)) }
        }
        compose.onNodeWithContentDescription("Search book").performClick()
        compose.onNodeWithTag("reader_search_field").performTextReplacement("missing")
        compose.waitUntil(5_000) { search.queries.contains("missing") }
        compose.onNodeWithText("No matches").assertIsDisplayed()
        screenshot("reader-search-no-results")
    }

    @Test
    fun backClosesSearchBeforeLeavingReader() {
        var exits = 0
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(readerReadyState(search = FakeBookSearch()), onBack = { exits++ })
            }
        }
        compose.onNodeWithContentDescription("Search book").performClick()
        compose.onNodeWithTag("reader_search_field").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("reader_search_panel").assertDoesNotExist()
        assertEquals(0, exits)
    }

    @Test
    fun changingPublicationClosesSearchAndDiscardsItsResults() {
        val first = mutableStateOf(readerReadyState(search = FakeBookSearch()))
        compose.setContent {
            SecondPassTheme { ReaderTestScreen(first.value) }
        }
        compose.onNodeWithContentDescription("Search book").performClick()
        compose.onNodeWithTag("reader_search_field").performTextReplacement("passage")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("2 matches shown").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("2 matches shown").assertIsDisplayed()
        compose.runOnUiThread { first.value = readerReadyState(search = FakeBookSearch()) }
        compose.onNodeWithTag("reader_search_panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search book").performClick()
        compose.onNodeWithTag("reader_search_field").assertIsDisplayed()
        compose.onNodeWithText("2 matches shown").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("search-proof") ?: return
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private class FakeBookSearch : ReaderBookSearch {
        val queries = mutableListOf<String>()
        val destinations = mutableListOf<ReaderSearchTarget>()
        private val first = object : ReaderSearchTarget {}
        private val second = object : ReaderSearchTarget {}

        override fun search(query: String): Flow<List<ReaderSearchResult>> {
            queries += query
            return flowOf(
                if (query == "passage") {
                    listOf(
                        ReaderSearchResult(first, "before", "passage", "after", null),
                        ReaderSearchResult(second, "later", "passage", "again", null)
                    )
                } else {
                    emptyList()
                }
            )
        }

        override suspend fun goTo(target: ReaderSearchTarget): Boolean {
            destinations += target
            return true
        }
    }
}
