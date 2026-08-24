package com.secondpasslibrary.reader.reader.cfi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubCfiTest {
    @Test
    fun `retains opaque input without normalization`() {
        val original = "  epubcfi(/6/4!/4/2:7)  "

        assertEquals(original, EpubCfi(original).value)
    }

    @Test
    fun `rejects blank and values over the SPL bound`() {
        assertThrows(IllegalArgumentException::class.java) { EpubCfi("  ") }
        assertThrows(IllegalArgumentException::class.java) { EpubCfi("x".repeat(8 * 1024 + 1)) }
    }
}
