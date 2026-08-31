package com.secondpasslibrary.reader.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsSectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readerSectionEditsAppearanceAndMarginaliaDefaults() {
        var state by mutableStateOf(ReaderSettingsState())
        compose.setContent {
            SecondPassTheme {
                ReaderSettingsSection(
                    state,
                    onAppearanceChanged = { state = state.copy(appearance = it) },
                    onAutoShowPreviousChanged = {
                        state = state.copy(autoShowPreviousMarginalia = it)
                    }
                )
            }
        }

        compose.onNodeWithText("Reader").assertIsDisplayed()
        compose.onNodeWithText("Sepia").assertIsSelected()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Two-column").performClick()
        compose.onNodeWithContentDescription("Increase Font size").performClick()
        compose.onNodeWithContentDescription("Increase Line height").performClick()
        compose.onNodeWithContentDescription("Publisher styles").performClick()
        compose.onNodeWithContentDescription("Show previous marginalia automatically")
            .performClick()

        compose.runOnIdle {
            assertTrue(state.appearance.theme == ReaderTheme.DARK)
            assertTrue(state.appearance.layoutMode == ReaderLayoutMode.TWO_COLUMN)
            assertTrue(state.appearance.fontScale > 1.0)
            assertTrue(state.appearance.lineHeight > 1.4)
            assertTrue(state.appearance.publisherStylesEnabled)
            assertFalse(state.autoShowPreviousMarginalia)
        }
    }
}
