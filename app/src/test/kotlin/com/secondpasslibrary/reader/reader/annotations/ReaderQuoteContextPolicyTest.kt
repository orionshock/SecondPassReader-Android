package com.secondpasslibrary.reader.reader.annotations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderQuoteContextPolicyTest {
    @Test
    fun `flattens JavaScript whitespace without changing other Unicode or punctuation`() {
        val result = ReaderQuoteContextPolicy.prepare(
            exact = "  One\n\n Apocalypses\t always   kick off...  ",
            prefix = "\u00A0before\r\n\t context\u3000",
            suffix = "\u202Fafter\u2028context\uFEFF"
        )

        requireNotNull(result)
        assertEquals("One Apocalypses always kick off...", result.exact)
        assertEquals("before context", result.prefix)
        assertEquals("after context", result.suffix)
        assertEquals(
            "\u4E16\u754C!",
            ReaderQuoteContextPolicy.prepare(
                " \u4E16\u754C! ",
                null,
                null
            )?.exact
        )
    }

    @Test
    fun `rejects normalized blank exact and empties whitespace-only context`() {
        assertNull(ReaderQuoteContextPolicy.prepare(" \t\n\u00A0\uFEFF", "before", "after"))

        val result = ReaderQuoteContextPolicy.prepare("quote", " \t\u00A0", "\r\n\u3000")

        requireNotNull(result)
        assertEquals("", result.prefix)
        assertEquals("", result.suffix)
    }

    @Test
    fun `short exact splits remaining five hundred character budget deterministically`() {
        val exact = "x".repeat(100)
        val result = ReaderQuoteContextPolicy.prepare(
            exact,
            "p".repeat(300),
            "s".repeat(300)
        )

        requireNotNull(result)
        assertEquals(exact, result.exact)
        assertEquals(200, result.prefix.length)
        assertEquals(200, result.suffix.length)
        assertEquals(500, result.exact.length + result.prefix.length + result.suffix.length)

        val boundary = ReaderQuoteContextPolicy.prepare("q".repeat(496), "abc", "xyz")
        requireNotNull(boundary)
        assertEquals("bc", boundary.prefix)
        assertEquals("xy", boundary.suffix)
    }

    @Test
    fun `long exact is preserved and context is ten percent capped at five hundred`() {
        val thousand = ReaderQuoteContextPolicy.prepare(
            "q".repeat(1_000),
            "p".repeat(1_000),
            "s".repeat(1_000)
        )
        val sixThousand = ReaderQuoteContextPolicy.prepare(
            "q".repeat(6_000),
            "p".repeat(1_000),
            "s".repeat(1_000)
        )

        requireNotNull(thousand)
        requireNotNull(sixThousand)
        assertEquals(1_000, thousand.exact.length)
        assertEquals(50, thousand.prefix.length)
        assertEquals(50, thousand.suffix.length)
        assertEquals(6_000, sixThousand.exact.length)
        assertEquals(250, sixThousand.prefix.length)
        assertEquals(250, sixThousand.suffix.length)
        assertTrue(sixThousand.prefix.length <= 500)
        assertTrue(sixThousand.suffix.length <= 500)
    }
}
