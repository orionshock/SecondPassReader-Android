package com.secondpasslibrary.reader.reader

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChromeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readerMenuShowsTitleAndDelegatesReturnToBook() {
        var returnRequests = 0
        compose.setContent {
            SecondPassTheme {
                ReaderChrome(
                    title = "A deliberately long Reader title that remains one line",
                    onReturnToBook = { returnRequests += 1 }
                )
            }
        }

        compose.onNodeWithText("A deliberately long Reader title that remains one line")
            .assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Open navigation drawer")
                .fetchSemanticsNodes().size
        )
        compose.onNodeWithContentDescription("Reader menu").assertIsDisplayed().performClick()
        compose.onNodeWithText("Return to Book").assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals(1, returnRequests) }
    }
}
