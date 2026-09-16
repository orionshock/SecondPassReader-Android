package com.secondpasslibrary.reader.library.offline

import com.secondpasslibrary.reader.home.FakeHomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projectionAccount
import com.secondpasslibrary.reader.home.recentItem
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetChecksum
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineLibraryCatalogTest {
    @Test
    fun `only cached books with completed account-scoped epubs are returned`() = runTest {
        val root = Files.createTempDirectory("offline-library").toFile()
        val assets = ReaderBookAssetStore.forTests(root)
        val store = FakeHomeProjectionStore()
        val account = projectionAccount()
        store.seedRecent(
            account,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("downloaded"), recentItem("missing"), recentItem("partial"))
        )
        complete(assets, account.profile.serverOrigin, account.profileId, "book-downloaded")
        val partial = assets.completedFile(
            ReaderAccountScope(account.profile.serverOrigin, account.profileId),
            "book-partial"
        )
        requireNotNull(partial.parentFile).mkdirs()
        partial.resolveSibling("${partial.name}.part").writeBytes(byteArrayOf(1))

        val result = OfflineLibraryCatalog(store, assets)
            .downloadedBooks(account.profile, account.profileId)

        assertEquals(listOf("book-downloaded"), result.map { it.id })
        assertEquals("Book downloaded", result.single().title)
    }

    @Test
    fun `same book asset from another account does not qualify`() = runTest {
        val root = Files.createTempDirectory("offline-library-account").toFile()
        val assets = ReaderBookAssetStore.forTests(root)
        val store = FakeHomeProjectionStore()
        val account = projectionAccount()
        store.seedRecent(
            account,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("shared"))
        )
        complete(assets, account.profile.serverOrigin, "another-profile", "book-shared")

        assertTrue(
            OfflineLibraryCatalog(store, assets)
                .downloadedBooks(account.profile, account.profileId)
                .isEmpty()
        )
    }

    @Test
    fun `asset-owned metadata includes downloaded book outside cached Home projections`() =
        runTest {
            val root = Files.createTempDirectory("offline-library-metadata").toFile()
            val assets = ReaderBookAssetStore.forTests(root)
            val account = projectionAccount()
            complete(
                assets,
                account.profile.serverOrigin,
                account.profileId,
                "book-local",
                "Local Book"
            )

            val result = OfflineLibraryCatalog(FakeHomeProjectionStore(), assets)
                .downloadedBooks(account.profile, account.profileId)

            assertEquals(listOf("book-local"), result.map { it.id })
            assertEquals("Local Book", result.single().title)
        }

    @Test
    fun `corrupt completed EPUB is excluded from offline Library`() = runTest {
        val root = Files.createTempDirectory("offline-library-corrupt").toFile()
        val assets = ReaderBookAssetStore.forTests(root)
        val store = FakeHomeProjectionStore()
        val account = projectionAccount()
        val scope = ReaderAccountScope(account.profile.serverOrigin, account.profileId)
        store.seedRecent(
            account,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("corrupt"))
        )
        complete(assets, account.profile.serverOrigin, account.profileId, "book-corrupt")
        assets.completedFile(scope, "book-corrupt").writeBytes("damaged".toByteArray())

        assertTrue(
            OfflineLibraryCatalog(store, assets)
                .downloadedBooks(account.profile, account.profileId)
                .isEmpty()
        )
        assertTrue(!assets.completedFile(scope, "book-corrupt").exists())
    }

    private suspend fun complete(
        assets: ReaderBookAssetStore,
        serverOrigin: String,
        profileId: String,
        bookId: String,
        title: String = "Book ${bookId.removePrefix("book-")}"
    ) {
        val account = ReaderAccountScope(serverOrigin, profileId)
        val bytes = byteArrayOf(1, 2, 3)
        val checksum = checksum(bytes)
        assets.acquire(account, bookId, checksum, {}) { it.write(bytes) }
        assets.rememberCompletedBook(account, bookId, title, checksum)
    }

    private fun checksum(bytes: ByteArray): ReaderBookAssetChecksum {
        val value = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        return ReaderBookAssetChecksum.fromServer(value)
    }
}
