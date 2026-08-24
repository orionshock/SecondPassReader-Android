package com.secondpasslibrary.reader.reader.asset

import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookAssetStoreTest {
    @Test
    fun `completed local EPUB is reused without downloading again`() = runTest {
        val root = Files.createTempDirectory("reader-assets").toFile()
        val store = ReaderBookAssetStore.forTests(root)
        val account = ReaderAccountScope("https://library.example", "profile-1")
        var downloads = 0

        val first = store.acquire(account, "book-1", {}) { output ->
            downloads += 1
            output.write("epub".toByteArray())
        }
        val second = store.acquire(account, "book-1", {}) { downloads += 1 }

        assertFalse(first.reused)
        assertTrue(second.reused)
        assertEquals(first.file, second.file)
        assertEquals(1, downloads)
    }

    @Test
    fun `failed download removes partial file and never publishes a completed asset`() = runTest {
        val root = Files.createTempDirectory("reader-assets").toFile()
        val store = ReaderBookAssetStore.forTests(root)
        val account = ReaderAccountScope("https://library.example", "profile-1")

        runCatching {
            store.acquire(account, "book-1", {}) { output ->
                output.write("partial".toByteArray())
                throw IOException("broken download")
            }
        }

        assertFalse(store.completedFile(account, "book-1").exists())
        assertTrue(root.walkTopDown().none { it.name.endsWith(".part") })
    }

    @Test
    fun `account and Book scopes resolve to isolated opaque paths`() {
        val store =
            ReaderBookAssetStore.forTests(
                Files.createTempDirectory("reader-assets").toFile()
            )
        val firstAccount = ReaderAccountScope("https://library.example", "profile-1")
        val secondAccount = ReaderAccountScope("https://library.example", "profile-2")

        val first = store.completedFile(firstAccount, "book-1")
        val anotherBook = store.completedFile(firstAccount, "book-2")
        val anotherAccount = store.completedFile(secondAccount, "book-1")

        assertNotEquals(first, anotherBook)
        assertNotEquals(first, anotherAccount)
        assertFalse(first.path.contains("profile-1"))
        assertFalse(first.path.contains("book-1"))
    }
}
