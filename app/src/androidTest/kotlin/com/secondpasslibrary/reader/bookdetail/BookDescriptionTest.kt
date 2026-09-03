package com.secondpasslibrary.reader.bookdetail

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDescriptionTest {
    @Test
    fun plainTextParagraphsBreaksAndEntitiesRenderAsReadableText() {
        assertEquals(
            "Just an ordinary plain-text description.",
            render("Just an ordinary plain-text description.").text
        )
        assertEquals(
            "First paragraph.\nSecond paragraph.",
            render(
                "<p>First paragraph.</p><p>Second paragraph.</p>"
            ).text
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
    fun emptyAndMalformedDescriptionsFailReadableWithoutRawTags() {
        assertNull(renderBookDescription(null))
        assertNull(renderBookDescription(""))
        assertNull(renderBookDescription("<p>&#x20;</p>"))

        val malformed = render("<unexpected><p>Still <strong>readable")

        assertTrue(malformed.text.contains("Still readable"))
        assertFalse(malformed.text.contains("<"))
        assertFalse(malformed.text.contains(">"))
    }

    private fun render(source: String) = checkNotNull(renderBookDescription(source))

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
