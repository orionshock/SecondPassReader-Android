package com.secondpasslibrary.reader.design.richtext

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServerRichTextSanitizerTest {
    @Test
    fun keepsExactlySupportedStructureWithoutAttributes() {
        val sanitized = sanitize(
            "<p class=foo onclick=x()>Hello <strong style='color:red'>world</strong>." +
                "<br><b>B</b><i>I</i><em data-x=y>E</em></p>" +
                "<ul><li onclick=x()>U</li></ul><ol><li>O</li></ol>"
        )

        assertEquals(
            "<p>Hello <strong>world</strong>.<br><b>B</b><i>I</i><em>E</em></p>" +
                "<ul><li>U</li></ul><ol><li>O</li></ol>",
            sanitized
        )
    }

    @Test
    fun unwrapsUnsupportedElementsAndRemovesActiveMarkup() {
        assertEquals("Example", sanitize("<a href='https://example.com'>Example</a>"))
        assertEquals("beforeafter", sanitize("before<img src=x>after"))
        assertEquals("Hello", sanitize("<div><span>Hello</span></div>"))
        assertEquals("<strong>Hello</strong>", sanitize("<strong onmouseover=x()>Hello</strong>"))
    }

    @Test
    fun removesScriptAndStyleElementsWithTheirContents() {
        assertEquals("beforeafter", sanitize("before<script>alert(1)</script>after"))
        assertEquals("beforeafter", sanitize("before<style>body{display:none}</style>after"))
    }

    @Test
    fun preservesEntitySemanticsWithoutDoubleEscaping() {
        val sanitized = sanitize("Tom &amp; Jerry&#x20;")

        assertEquals("Tom &amp; Jerry ", sanitized)
        assertEquals("Tom & Jerry", Jsoup.parseBodyFragment(sanitized).body().text())
    }

    @Test
    fun malformedInputProducesSafeReadableMarkup() {
        val sanitized = sanitize("<p>Hello <strong>world<script>bad()</script>")

        assertNotNull(sanitized)
        assertFalse(sanitized.contains("script", ignoreCase = true))
        assertFalse(sanitized.contains("bad()"))
        assertEquals("Hello world", Jsoup.parseBodyFragment(sanitized).body().text())
    }

    private fun sanitize(value: String): String =
        checkNotNull(ServerRichTextSanitizer.sanitize(value))
}
