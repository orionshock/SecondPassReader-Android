package com.secondpasslibrary.reader.library.offline

import com.secondpasslibrary.reader.home.FakeHomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projectionAccount
import com.secondpasslibrary.reader.home.recentItem
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import java.nio.file.Files
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
            val scope = ReaderAccountScope(account.profile.serverOrigin, account.profileId)
            complete(assets, account.profile.serverOrigin, account.profileId, "book-local")
            assets.rememberCompletedBook(scope, "book-local", "Local Book")

            val result = OfflineLibraryCatalog(FakeHomeProjectionStore(), assets)
                .downloadedBooks(account.profile, account.profileId)

            assertEquals(listOf("book-local"), result.map { it.id })
            assertEquals("Local Book", result.single().title)
        }

    private fun complete(
        assets: ReaderBookAssetStore,
        serverOrigin: String,
        profileId: String,
        bookId: String
    ) {
        val file = assets.completedFile(ReaderAccountScope(serverOrigin, profileId), bookId)
        requireNotNull(file.parentFile).mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
    }
}
