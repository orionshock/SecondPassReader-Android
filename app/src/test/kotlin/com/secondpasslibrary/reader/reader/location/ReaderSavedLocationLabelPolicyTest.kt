package com.secondpasslibrary.reader.reader.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSavedLocationLabelPolicyTest {
    @Test
    fun `percentage is clamped rounded and zero padded`() {
        assertEquals("000% - Start", label(-1.0))
        assertEquals("001% - Chapter 08", label(0.01))
        assertEquals("014% - Chapter 08", label(0.14))
        assertEquals("099% - End", label(0.99))
        assertEquals("100% - End", label(2.0))
    }

    @Test
    fun `suffix precedence is section then boundary then spine then generic`() {
        assertEquals("000% - Dedication", label(0.0, "  Dedication  ", 8))
        assertEquals("000% - Start", label(0.0, null, 8))
        assertEquals("099% - End", label(0.99, null, 8))
        assertEquals("042% - Chapter 08", label(0.42, null, 8))
        assertEquals("042% - Location", label(0.42, null, null))
        assertEquals("000% - Start", label(null, null, null))
    }

    @Test
    fun `unsafe machine labels and transient renderer details never survive`() {
        val candidates = listOf(
            "text/chapter08.xhtml",
            "chapter08.xhtml#part",
            "epubcfi(/6/8!/4/2:7)",
            "Dedication p1/2 1%"
        )
        candidates.forEach { candidate ->
            val result = label(0.42, candidate, 8)
            assertEquals("042% - Chapter 08", result)
            assertFalse(result.contains(candidate))
        }
    }

    @Test
    fun `percentage prefix is lexically sortable`() {
        val labels = listOf(
            label(0.99, "End matter"),
            label(0.03, "PROLOGUE"),
            label(0.14, "Chapter One")
        )
        assertEquals(listOf("003%", "014%", "099%"), labels.sorted().map { it.take(4) })
    }

    @Test
    fun `generated label stays within server bound`() {
        val result = label(0.42, "A".repeat(300))
        assertEquals(255, result.length)
        assertTrue(result.startsWith("042% - "))
    }

    private fun label(progression: Double?, section: String? = null, ordinal: Int? = 8): String =
        ReaderSavedLocationLabelPolicy.create(progression, section, ordinal)
}
