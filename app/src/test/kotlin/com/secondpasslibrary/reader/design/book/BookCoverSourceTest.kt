package com.secondpasslibrary.reader.design.book

import com.secondpasslibrary.client.PublicBookCoverReference
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class BookCoverSourceTest {
    @Test
    fun `durable local cover wins over remote URL and missing file falls back`() {
        val remote = PublicBookCoverReference.fromAbsoluteUrl("https://library.example/cover.png")
        val local = Files.createTempFile("offline-cover", ".png").toFile()
        local.writeBytes(byteArrayOf(1))

        assertEquals(local, bookCoverSource(local, remote))
        local.delete()
        assertEquals(remote.url, bookCoverSource(local, remote))
        assertEquals(null, bookCoverSource(local, null))
    }
}
