package com.secondpasslibrary.reader.about

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutInfoTest {
    @Test
    fun `connected support text includes release and server identity without secrets`() {
        val info = fixture().copy(
            libraryName = "Library",
            serverVersion = "2.0",
            serverId = "server-1",
            libraryUrl = "https://library.example"
        )
        val text = info.copyText()
        assertTrue(text.contains("Second Pass Reader 0.1.0-alpha.1 (1)"))
        assertTrue(text.contains("com.secondpasslibrary.reader"))
        assertTrue(text.contains("Device: Samsung SM-T710"))
        assertTrue(text.contains("Android: 7.0 / API 24"))
        assertTrue(text.contains("Library: Library"))
        assertTrue(text.contains("Server version: 2.0"))
        assertTrue(text.contains("Server ID: server-1"))
        assertTrue(text.contains("Library URL: https://library.example"))
        assertFalse(text.contains("token"))
        assertFalse(text.contains("profile"))
    }

    @Test
    fun `disconnected support text omits unavailable server fields`() {
        val text = fixture().copyText()
        assertFalse(text.contains("Library:"))
        assertFalse(text.contains("Server version:"))
        assertFalse(text.contains("Server ID:"))
        assertFalse(text.contains("Library URL:"))
    }

    private fun fixture() = AboutInfo(
        "0.1.0-alpha.1", 1, "com.secondpasslibrary.reader", "Samsung SM-T710", "7.0", 24,
        null, null, null, null
    )
}
