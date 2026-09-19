package com.secondpasslibrary.reader.reader.asset

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookAssetStoreTest {
    @Test
    fun `valid checksum promotes download and completed EPUB is reused`() = runTest {
        val fixture = fixture()
        var downloads = 0

        val first = fixture.store.acquire(
            fixture.account,
            "book-1",
            checksum(EPUB_BYTES),
            {}
        ) { output ->
            downloads += 1
            output.write(EPUB_BYTES)
        }
        fixture.store.rememberCompletedBook(
            fixture.account,
            "book-1",
            "Book",
            checksum(EPUB_BYTES)
        )
        val second = fixture.store.acquire(
            fixture.account,
            "book-1",
            checksum(EPUB_BYTES),
            {}
        ) { downloads += 1 }

        assertFalse(first.reused)
        assertTrue(second.reused)
        assertEquals(first.file, second.file)
        assertEquals(1, downloads)
        assertTrue(first.file.readBytes().contentEquals(EPUB_BYTES))
        assertTrue(fixture.root.walkTopDown().none { it.name.endsWith(".part") })
    }

    @Test
    fun `checksum mismatch and truncated download publish no completed asset`() = runTest {
        val fixture = fixture()

        val mismatch = runCatching {
            fixture.store.acquire(
                fixture.account,
                "mismatch",
                checksum(EPUB_BYTES),
                {}
            ) { it.write("different".toByteArray()) }
        }
        val truncated = runCatching {
            fixture.store.acquire(
                fixture.account,
                "truncated",
                checksum(EPUB_BYTES),
                {}
            ) { it.write(EPUB_BYTES.copyOf(EPUB_BYTES.size - 1)) }
        }

        assertTrue(mismatch.exceptionOrNull() is ReaderEpubIntegrityException)
        assertTrue(truncated.exceptionOrNull() is ReaderEpubIntegrityException)
        assertFalse(fixture.store.completedFile(fixture.account, "mismatch").exists())
        assertFalse(fixture.store.completedFile(fixture.account, "truncated").exists())
    }

    @Test
    fun `empty and interrupted downloads leave no completed or partial asset`() = runTest {
        val fixture = fixture()
        val empty = runCatching {
            fixture.store.acquire(fixture.account, "empty", checksum(EPUB_BYTES), {}) {}
        }
        val interrupted = runCatching {
            fixture.store.acquire(
                fixture.account,
                "interrupted",
                checksum(EPUB_BYTES),
                {}
            ) { output ->
                output.write(EPUB_BYTES.copyOf(3))
                throw IOException("broken download")
            }
        }

        assertTrue(empty.exceptionOrNull() is ReaderEpubIntegrityException)
        assertTrue(interrupted.exceptionOrNull() is IOException)
        assertTrue(fixture.root.walkTopDown().none { it.name.endsWith(".epub") })
        assertTrue(fixture.root.walkTopDown().none { it.name.endsWith(".part") })
    }

    @Test
    fun `partial file never qualifies as completed`() = runTest {
        val fixture = fixture()
        val completed = fixture.store.completedFile(fixture.account, "book-1")
        requireNotNull(completed.parentFile).mkdirs()
        completed.resolveSibling("${completed.name}.part").writeBytes(EPUB_BYTES)

        assertEquals(null, fixture.store.findCompleted(fixture.account, "book-1"))
    }

    @Test
    fun `corrupt completed EPUB is invalidated and retry replaces it`() = runTest {
        val fixture = fixture()
        complete(fixture, "book-1", EPUB_BYTES)
        val completed = fixture.store.completedFile(fixture.account, "book-1")
        completed.writeBytes("corrupt".toByteArray())

        assertEquals(null, fixture.store.findCompleted(fixture.account, "book-1"))
        assertFalse(completed.exists())

        val retried = fixture.store.acquire(
            fixture.account,
            "book-1",
            checksum(EPUB_BYTES),
            {}
        ) { it.write(EPUB_BYTES) }

        assertFalse(retried.reused)
        assertTrue(retried.file.readBytes().contentEquals(EPUB_BYTES))
    }

    @Test
    fun `wrong Book checksum pairing invalidates completed EPUB`() = runTest {
        val fixture = fixture()
        complete(fixture, "book-1", EPUB_BYTES)

        val result = runCatching {
            fixture.store.acquire(
                fixture.account,
                "book-1",
                checksum("other".toByteArray()),
                {}
            ) { it.write(EPUB_BYTES) }
        }

        assertTrue(result.exceptionOrNull() is ReaderEpubIntegrityException)
        assertFalse(fixture.store.completedFile(fixture.account, "book-1").exists())
    }

    @Test
    fun `completed lookup is account scoped`() = runTest {
        val fixture = fixture()
        val other = ReaderAccountScope("https://library.example", "profile-2")
        complete(fixture, "book-1", EPUB_BYTES)

        assertTrue(fixture.store.findCompleted(fixture.account, "book-1") != null)
        assertEquals(null, fixture.store.findCompleted(other, "book-1"))
    }

    @Test
    fun `removing one download removes epub and metadata without touching another account`() =
        runTest {
            val fixture = fixture()
            val other = ReaderAccountScope("https://library.example", "profile-2")
            complete(fixture, "book-1", EPUB_BYTES)
            fixture.store.acquire(other, "book-1", checksum(EPUB_BYTES), {}) {
                it.write(EPUB_BYTES)
            }
            fixture.store.rememberCompletedBook(other, "book-1", "Other Book", checksum(EPUB_BYTES))

            val completed = fixture.store.completedBooks(fixture.account).single()
            assertEquals(EPUB_BYTES.size.toLong(), completed.sizeBytes)
            fixture.store.removeCompleted(fixture.account, "book-1")

            assertFalse(fixture.store.completedFile(fixture.account, "book-1").exists())
            assertTrue(fixture.store.completedBooks(fixture.account).isEmpty())
            assertTrue(
                requireNotNull(fixture.store.completedFile(fixture.account, "book-1").parentFile)
                    .listFiles().orEmpty().none { it.extension == "metadata" }
            )
            assertEquals(1, fixture.store.completedBooks(other).size)
        }

    @Test
    fun `checksum accepts hexadecimal case but rejects malformed server values`() {
        val expected = checksum(EPUB_BYTES)
        val file = Files.createTempFile("reader-checksum", ".epub").toFile()
        file.writeBytes(EPUB_BYTES)

        assertTrue(ReaderBookAssetChecksum.fromServer(expected.value.uppercase()).matches(file))
        assertTrue(runCatching { ReaderBookAssetChecksum.fromServer(null) }.isFailure)
        assertTrue(
            runCatching {
                ReaderBookAssetChecksum.fromServer("sha256:${expected.value}")
            }.isFailure
        )
        assertTrue(runCatching { ReaderBookAssetChecksum.fromServer("abc") }.isFailure)
    }

    @Test
    fun `account and Book scopes resolve to isolated opaque paths`() {
        val fixture = fixture()
        val secondAccount = ReaderAccountScope("https://library.example", "profile-2")

        val first = fixture.store.completedFile(fixture.account, "book-1")
        val anotherBook = fixture.store.completedFile(fixture.account, "book-2")
        val anotherAccount = fixture.store.completedFile(secondAccount, "book-1")

        assertNotEquals(first, anotherBook)
        assertNotEquals(first, anotherAccount)
        assertFalse(first.path.contains("profile-1"))
        assertFalse(first.path.contains("book-1"))
    }

    private suspend fun complete(fixture: Fixture, bookId: String, bytes: ByteArray) {
        val expected = checksum(bytes)
        fixture.store.acquire(fixture.account, bookId, expected, {}) { it.write(bytes) }
        fixture.store.rememberCompletedBook(fixture.account, bookId, "Book", expected)
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("reader-assets").toFile()
        return Fixture(
            root,
            ReaderBookAssetStore.forTests(root),
            ReaderAccountScope("https://library.example", "profile-1")
        )
    }

    private data class Fixture(
        val root: File,
        val store: ReaderBookAssetStore,
        val account: ReaderAccountScope
    )

    private fun checksum(bytes: ByteArray): ReaderBookAssetChecksum {
        val value = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        return ReaderBookAssetChecksum.fromServer(value)
    }

    private companion object {
        val EPUB_BYTES = "representative epub bytes".toByteArray()
    }
}
