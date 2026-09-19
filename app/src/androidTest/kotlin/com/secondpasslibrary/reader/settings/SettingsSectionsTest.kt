package com.secondpasslibrary.reader.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSectionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wideLayoutUsesVerticalSectionRailAndUpdatesSelectedContent() {
        var selected by mutableStateOf(SettingsSection.LIBRARY_ACCOUNT)
        compose.setContent {
            SecondPassTheme {
                SettingsSectionLayout(selected, onSelect = { selected = it }) { section ->
                    androidx.compose.material3.Text("Content ${section.name}")
                }
            }
        }
        val library = compose.onNodeWithTag("settings-section-library_account")
        val reader = compose.onNodeWithTag("settings-section-reader")
        library.assertIsSelected()
        val libraryBounds = library.fetchSemanticsNode().boundsInRoot
        val readerBounds = reader.fetchSemanticsNode().boundsInRoot
        assertTrue(readerBounds.top > libraryBounds.bottom)
        reader.performClick()
        reader.assertIsSelected()
        compose.runOnIdle { assertEquals(SettingsSection.READER, selected) }
    }

    @Test
    fun compactLayoutKeepsSectionsAvailableInHorizontalStrip() {
        var selected by mutableStateOf(SettingsSection.LIBRARY_ACCOUNT)
        compose.setContent {
            SecondPassTheme {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    SettingsSectionLayout(selected, onSelect = { selected = it }) { section ->
                        androidx.compose.material3.Text("Content ${section.name}")
                    }
                }
            }
        }
        val library = compose.onNodeWithTag("settings-section-library_account")
        val reader = compose.onNodeWithTag("settings-section-reader")
        val libraryBounds = library.fetchSemanticsNode().boundsInRoot
        val readerBounds = reader.fetchSemanticsNode().boundsInRoot
        assertTrue(readerBounds.left >= libraryBounds.right)
        reader.performClick()
        reader.assertIsSelected()
        compose.runOnIdle { assertEquals(SettingsSection.READER, selected) }
    }
}
