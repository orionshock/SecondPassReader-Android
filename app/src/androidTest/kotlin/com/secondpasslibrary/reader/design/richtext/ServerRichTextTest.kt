package com.secondpasslibrary.reader.design.richtext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerRichTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun plainTextParagraphsBreaksAndEntitiesRenderAsReadableText() {
        assertEquals("A simple description.", render("A simple description.").text)
        assertEquals(
            "First paragraph.\nSecond paragraph.",
            render("<p>First paragraph.</p><p>Second paragraph.</p>").text
        )
        assertEquals("Before\nafter", render("Before<br>after").text)
        assertEquals("A & B C", render("A &amp; B&#x20;C").text)
    }

    @Test
    fun strongBoldEmphasisAndItalicProduceComposeStyles() {
        val rendered = render(
            "<strong>Strong</strong> <b>Bold</b> <em>Emphasis</em> <i>Italic</i>"
        )

        assertStyled(rendered, "Strong", fontWeight = FontWeight.Bold)
        assertStyled(rendered, "Bold", fontWeight = FontWeight.Bold)
        assertStyled(rendered, "Emphasis", fontStyle = FontStyle.Italic)
        assertStyled(rendered, "Italic", fontStyle = FontStyle.Italic)
    }

    @Test
    fun unorderedAndOrderedListsKeepEveryItemAndOrderedNumbers() {
        val unordered = render("<ul><li>One item</li><li>Another item</li></ul>")
        val ordered = render("<ol><li>First item</li><li>Second item</li></ol>")

        assertEquals("\u2022 One item\n\u2022 Another item", unordered.text)
        assertEquals("1. First item\n2. Second item", ordered.text)
        assertEquals(
            "\u2022 One item\n\u2022 Another item",
            serverRichTextPlainText(
                "<ul><li>One item</li><li>Another item</li></ul>"
            )
        )
    }

    @Test
    fun mixedSupportedMarkupRetainsStructureAndEmphasis() {
        val rendered = render(
            "<p>A first paragraph with <em>emphasis</em>.</p>" +
                "<p>A second paragraph.</p>" +
                "<ul><li>One item</li><li>Another item</li></ul>"
        )

        assertEquals(
            "A first paragraph with emphasis.\nA second paragraph.\n" +
                "\u2022 One item\n\u2022 Another item",
            rendered.text
        )
        assertStyled(rendered, "emphasis", fontStyle = FontStyle.Italic)
    }

    @Test
    fun emptyAndMalformedValuesFailReadableWithoutRawTags() {
        assertNull(renderServerRichText(null))
        assertNull(renderServerRichText(""))
        assertNull(renderServerRichText("<p>&#x20;</p>"))

        val malformed = render("<unexpected><p>Still <strong>readable")

        assertTrue(malformed.text.contains("Still readable"))
        assertFalse(malformed.text.contains("<"))
        assertFalse(malformed.text.contains(">"))
    }

    @Test
    fun expansionIsBasedOnRenderedOverflow() {
        compose.setContent {
            SecondPassTheme {
                Box(Modifier.width(180.dp)) {
                    ServerRichText(
                        value = (1..8).joinToString("") { "<p>Paragraph $it is visible text.</p>" },
                        style = MaterialTheme.typography.bodyMedium,
                        collapsedMaxLines = 2,
                        expandOverflow = true,
                        moreLabel = "Show more",
                        lessLabel = "Show less"
                    )
                }
            }
        }

        compose.onNodeWithText("Show more").performClick()
        compose.onNodeWithText("Show less").assertExists()
        compose.onNodeWithText("Paragraph 8", substring = true).assertExists()
    }

    private fun render(source: String) = checkNotNull(renderServerRichText(source))

    private fun assertStyled(
        rendered: androidx.compose.ui.text.AnnotatedString,
        expectedText: String,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null
    ) {
        val start = rendered.text.indexOf(expectedText)
        val end = start + expectedText.length
        assertTrue(start >= 0)
        assertTrue(
            rendered.spanStyles.any { range ->
                range.start <= start && range.end >= end &&
                    (fontWeight == null || range.item.fontWeight == fontWeight) &&
                    (fontStyle == null || range.item.fontStyle == fontStyle)
            }
        )
    }
}
