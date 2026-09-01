package com.secondpasslibrary.reader.reader.ui.hud

import com.secondpasslibrary.reader.design.icons.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderBookmarkHudPresentationTest {
    @Test
    fun `writable zero state offers an outlined add bookmark action`() {
        val presentation = bookmarkHudPresentation(count = 0, writable = true)

        assertEquals(AppIcon.AddBookmark, presentation.icon)
        assertEquals("Add bookmark", presentation.contentDescription)
        assertNull(presentation.badgeText)
    }

    @Test
    fun `one bookmark uses solid state without a badge`() {
        val presentation = bookmarkHudPresentation(count = 1, writable = true)

        assertEquals(AppIcon.BookmarkFilled, presentation.icon)
        assertEquals("1 bookmark on this page", presentation.contentDescription)
        assertNull(presentation.badgeText)
    }

    @Test
    fun `multiple bookmarks expose exact semantics and a bounded visual count`() {
        val three = bookmarkHudPresentation(count = 3, writable = true)
        val many = bookmarkHudPresentation(count = 14, writable = true)

        assertEquals(AppIcon.BookmarkFilled, three.icon)
        assertEquals("3 bookmarks on this page", three.contentDescription)
        assertEquals("3", three.badgeText)
        assertEquals("14 bookmarks on this page", many.contentDescription)
        assertEquals("9+", many.badgeText)
    }

    @Test
    fun `closed Session states are truthful and read only`() {
        val empty = bookmarkHudPresentation(count = 0, writable = false)
        val one = bookmarkHudPresentation(count = 1, writable = false)

        assertEquals(AppIcon.Bookmark, empty.icon)
        assertEquals("No bookmarks on this page, read only", empty.contentDescription)
        assertEquals(AppIcon.BookmarkFilled, one.icon)
        assertEquals("1 bookmark on this page, read only", one.contentDescription)
    }
}
