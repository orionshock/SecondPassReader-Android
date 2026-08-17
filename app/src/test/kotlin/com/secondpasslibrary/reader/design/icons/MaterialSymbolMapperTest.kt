package com.secondpasslibrary.reader.design.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MaterialSymbolMapperTest {
    @Test
    fun `every semantic icon resolves to a bundled vector`() {
        AppIcon.entries.forEach { icon ->
            val symbol = MaterialSymbolMapper.resolve(icon)
            assertNotEquals("", symbol.token)
            assertNotEquals(0, symbol.drawableResource)
        }
    }

    @Test
    fun `semantic distinctions retain their canonical symbols`() {
        assertEquals("book_2", MaterialSymbolMapper.resolve(AppIcon.Library).token)
        assertEquals("menu_book", MaterialSymbolMapper.resolve(AppIcon.Book).token)
        assertEquals("local_library", MaterialSymbolMapper.resolve(AppIcon.ConnectedLibrary).token)
        assertNotEquals(
            MaterialSymbolMapper.resolve(AppIcon.Library).token,
            MaterialSymbolMapper.resolve(AppIcon.Book).token
        )
    }

    @Test
    fun `intentional aliases share symbols without sharing semantic keys`() {
        assertEquals(
            MaterialSymbolMapper.resolve(AppIcon.User).token,
            MaterialSymbolMapper.resolve(AppIcon.Author).token
        )
        assertEquals(
            MaterialSymbolMapper.resolve(AppIcon.NavigationMenu).token,
            MaterialSymbolMapper.resolve(AppIcon.TableOfContents).token
        )
    }
}
