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

    @Test
    fun `accepts the exact SPL CFI length bound`() {
        val boundary = "x".repeat(8 * 1024)

        assertEquals(boundary, EpubCfi(boundary).value)
    }

    @Test
    fun `does not interpret renderer syntax at the domain boundary`() {
        val opaqueValue = "not-yet-parsed-by-the-renderer"

        assertEquals(opaqueValue, EpubCfi(opaqueValue).value)
        assertEquals(opaqueValue, EpubCfi(opaqueValue).toString())
    }

    @Test
    fun `preserves historical Web range with structural element ID assertion`() {
        val historical =
            "epubcfi(/6/34!/4[x9780451492128_EPUB-15]/2,/310/1:0,/314/1:17)"

        assertEquals(historical, EpubCfi(historical).value)
    }
}
