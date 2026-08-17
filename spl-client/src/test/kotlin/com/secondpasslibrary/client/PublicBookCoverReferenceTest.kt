package com.secondpasslibrary.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PublicBookCoverReferenceTest {
    @Test
    fun `persisted absolute cover reference can be rehydrated`() {
        val reference =
            PublicBookCoverReference.fromAbsoluteUrl("https://assets.example/books/cover.webp")

        assertEquals("https://assets.example/books/cover.webp", reference.url)
    }

    @Test
    fun `persisted cover reference still rejects relative and non-http URLs`() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicBookCoverReference.fromAbsoluteUrl("/media/cover.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PublicBookCoverReference.fromAbsoluteUrl("file:///private/cover.jpg")
        }
    }
}
