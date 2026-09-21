package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.reader.home.FakeHomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projectionAccount
import com.secondpasslibrary.reader.home.recentItem
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetChecksum
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaVisibilityScope
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLocalBookCatalogTest {
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
        complete(assets, account.profile.serverId, account.profileId, "book-downloaded")
        val partial = assets.completedFile(
            ReaderAccountScope(account.profile.serverId, account.profileId),
            "book-partial"
        )
        requireNotNull(partial.parentFile).mkdirs()
        partial.resolveSibling("${partial.name}.part").writeBytes(byteArrayOf(1))

        val result = catalog(store, assets).downloadedBooks(account.localScope())

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
        complete(assets, account.profile.serverId, "another-profile", "book-shared")

        assertTrue(
            catalog(store, assets).downloadedBooks(account.localScope())
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
                account.profile.serverId,
                account.profileId,
                "book-local",
                "Local Book"
            )

            val result = catalog(FakeHomeProjectionStore(), assets)
                .downloadedBooks(account.localScope())

            assertEquals(listOf("book-local"), result.map { it.id })
            assertEquals("Local Book", result.single().title)
        }

    @Test
    fun `cover from cached Book survives coverless asset metadata precedence`() = runTest {
        val root = Files.createTempDirectory("offline-library-cover").toFile()
        val assets = ReaderBookAssetStore.forTests(root)
        val home = FakeHomeProjectionStore()
        val account = projectionAccount()
        val cover = PublicBookCoverReference.fromAbsoluteUrl("https://library.example/cover.png")
        val recent = recentItem("covered")
        home.seedRecent(
            account,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recent.copy(book = recent.book.copy(cover = cover)))
        )
        complete(assets, account.profile.serverId, account.profileId, "book-covered")

        assertEquals(
            cover,
            catalog(home, assets).downloadedBooks(account.localScope()).single().cover
        )
    }

    @Test
    fun `corrupt completed EPUB is excluded from offline Library`() = runTest {
        val root = Files.createTempDirectory("offline-library-corrupt").toFile()
        val assets = ReaderBookAssetStore.forTests(root)
        val store = FakeHomeProjectionStore()
        val account = projectionAccount()
        val scope = ReaderAccountScope(account.profile.serverId, account.profileId)
        store.seedRecent(
            account,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("corrupt"))
        )
        complete(assets, account.profile.serverId, account.profileId, "book-corrupt")
        assets.completedFile(scope, "book-corrupt").writeBytes("damaged".toByteArray())

        assertTrue(
            catalog(store, assets).downloadedBooks(account.localScope())
                .isEmpty()
        )
        assertTrue(!assets.completedFile(scope, "book-corrupt").exists())
    }

    @Test
    fun `download management removes only current account assets and offline Library entries`() =
        runTest {
            val root = Files.createTempDirectory("offline-download-management").toFile()
            val assets = ReaderBookAssetStore.forTests(root)
            val account = projectionAccount()
            val other = AccountLocalScope.from(account.profile.serverId, "other-profile")
            complete(assets, account.profile.serverId, account.profileId, "book-one")
            complete(assets, account.profile.serverId, account.profileId, "book-two")
            complete(assets, other.serverId, other.profileId, "book-other")
            val repository = catalog(FakeHomeProjectionStore(), assets)

            assertEquals(2, repository.downloads(account.localScope()).size)
            assertEquals(3L, repository.downloads(account.localScope()).first().sizeBytes)
            repository.removeDownload(account.localScope(), "book-one")
            val remainingDownloads = repository.downloads(account.localScope())
            val offlineBooks = repository.downloadedBooks(account.localScope())
            assertEquals(listOf("book-two"), remainingDownloads.map(AccountLocalDownload::bookId))
            assertEquals(listOf("book-two"), offlineBooks.map { it.id })

            repository.removeAllDownloads(account.localScope())
            assertTrue(repository.downloads(account.localScope()).isEmpty())
            val otherDownloads = repository.downloads(other)
            assertEquals(listOf("book-other"), otherDownloads.map(AccountLocalDownload::bookId))
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

    private fun com.secondpasslibrary.reader.home.HomeProjectionAccount.localScope() =
        AccountLocalScope.from(profile.serverId, profileId)

    private fun catalog(home: HomeProjectionStore, assets: ReaderBookAssetStore) =
        AccountLocalDataRepository(
            home,
            UnusedReaderStore,
            UnusedReaderScheduler,
            assets,
            UnusedVisibilityStore
        )

    private data object UnusedReaderScheduler : ReaderPendingSyncScheduler {
        override suspend fun ensureEnqueued(account: LocalReaderAccountKey) = Unit

        override fun cancel(account: LocalReaderAccountKey): Unit = error("Sync must be preserved")
    }

    private data object UnusedVisibilityStore : ReaderMarginaliaLayerVisibilityStore {
        override suspend fun read(
            scope: ReaderMarginaliaVisibilityScope,
            sessionId: String,
            now: Instant
        ): Boolean? = null

        override suspend fun write(
            scope: ReaderMarginaliaVisibilityScope,
            sessionId: String,
            visible: Boolean,
            touchedAt: Instant
        ) = Unit

        override suspend fun clearAccountState() = Unit
    }

    private data object UnusedReaderStore : LocalReaderStateStore {
        override suspend fun bookSummary(account: LocalReaderAccountKey, bookId: String) =
            com.secondpasslibrary.reader.reader.persistence.LocalReaderBookSummary(0, 0)

        override suspend fun selectOfflineSession(
            account: LocalReaderAccountKey,
            bookId: String
        ): ReaderSessionContext = error("unused")

        override suspend fun retainServerSession(
            account: LocalReaderAccountKey,
            bookId: String,
            session: ReaderSessionContext
        ) = session

        override suspend fun writeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String,
            provenance: LocalReaderWriteProvenance,
            locationLabel: String?
        ) = Unit

        override suspend fun acknowledgeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String
        ) = Unit

        override suspend fun readAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = emptyList<ReaderAnnotation>()

        override suspend fun applyAnnotationMutation(
            account: LocalReaderAccountKey,
            localSessionId: String,
            request: ReaderAnnotationMutationRequest
        ) = emptyList<ReaderAnnotation>()

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            acknowledgedMutation: ReaderAnnotationMutationRequest?
        ) = Unit

        override suspend fun purgeAccount(account: LocalReaderAccountKey): Unit =
            error("Reader-authored state must be preserved")
    }
}
