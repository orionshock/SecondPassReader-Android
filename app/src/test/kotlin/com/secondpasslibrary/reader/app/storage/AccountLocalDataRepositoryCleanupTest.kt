package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionSnapshot
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projection.HomeShelfVariant
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLocalDataRepositoryCleanupTest {
    @Test
    fun `cleanup cancels and purges only exact old account before replacement is used`() = runTest {
        val events = mutableListOf<String>()
        val home = RecordingHomeStore(events)
        val reader = RecordingReaderStore(events)
        val scheduler = RecordingScheduler(events)
        val assets = ReaderBookAssetStore.forTests(
            Files.createTempDirectory("account-assets").toFile()
        )
        val visibility = RecordingVisibilityStore(events)
        val repository = AccountLocalDataRepository(home, reader, scheduler, assets, visibility)
        val old = AccountLocalScope.from("https://old.example", "profile-1")
        val replacement = AccountLocalScope.from("https://new.example", "profile-1")
        val oldAsset = complete(assets, old.readerScope(), "old-book")
        val replacementAsset = complete(assets, replacement.readerScope(), "new-book")

        repository.purge(old)

        val oldReader = LocalReaderAccountKey.from(old.serverOrigin, old.profileId)
        assertEquals(listOf("cancel", "home", "reader", "visibility"), events)
        assertEquals(listOf(oldReader), scheduler.canceled)
        assertEquals(
            listOf(HomeAccountScopeKey.from(old.serverOrigin, old.profileId)),
            home.purged
        )
        assertEquals(listOf(oldReader), reader.purged)
        assertFalse(oldAsset.exists())
        assertTrue(replacementAsset.exists())
        assertEquals(1, visibility.clearCount)
        assertFalse(
            reader.purged.contains(
                LocalReaderAccountKey.from(replacement.serverOrigin, replacement.profileId)
            )
        )
    }

    @Test
    fun `book recovery removes target asset and reader state without touching another book`() =
        runTest {
            val events = mutableListOf<String>()
            val home = RecordingHomeStore(events)
            val reader = RecordingReaderStore(events)
            val scheduler = RecordingScheduler(events)
            val assets = ReaderBookAssetStore.forTests(
                Files.createTempDirectory("book-recovery").toFile()
            )
            val repository = AccountLocalDataRepository(
                home,
                reader,
                scheduler,
                assets,
                RecordingVisibilityStore(events)
            )
            val account = AccountLocalScope.from("https://example.org", "profile-1")
            val target = complete(assets, account.readerScope(), "target")
            val kept = complete(assets, account.readerScope(), "kept")

            val identity = AuthenticatedConnectionIdentity("https://example.org/api/v1/", "device")
            repository.clearBook(account, "target", identity)
            repository.clearBook(account, "target", identity)

            assertFalse(target.exists())
            assertTrue(kept.exists())
            assertEquals(listOf("target", "target"), reader.purgedBooks)
            assertEquals(listOf("target", "target"), home.purgedBooks)
            assertEquals(0, repository.downloads(account).count { it.bookId == "target" })
            assertEquals(1, repository.downloads(account).count { it.bookId == "kept" })
            assertEquals(0, scheduler.canceled.count { it != LocalReaderAccountKey.from(account) })
        }

    private fun AccountLocalScope.readerScope() = ReaderAccountScope.from(this)

    private suspend fun complete(
        assets: ReaderBookAssetStore,
        account: ReaderAccountScope,
        bookId: String
    ) = assets.acquire(account, bookId, checksum(ASSET_BYTES), {}) { output ->
        output.write(ASSET_BYTES)
    }.file.also {
        assets.rememberCompletedBook(account, bookId, bookId, checksum(ASSET_BYTES))
    }

    private fun checksum(bytes: ByteArray): ReaderBookAssetChecksum {
        val value = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        return ReaderBookAssetChecksum.fromServer(value)
    }

    private class RecordingVisibilityStore(private val events: MutableList<String>) :
        ReaderMarginaliaLayerVisibilityStore {
        var clearCount = 0

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

        override suspend fun clearAccountState() {
            events += "visibility"
            clearCount += 1
        }

        override suspend fun clearBookState(
            scope: ReaderMarginaliaVisibilityScope,
            sessionIds: List<String>
        ) {
            events += "book-visibility"
        }
    }

    private class RecordingScheduler(private val events: MutableList<String>) :
        ReaderPendingSyncScheduler {
        val canceled = mutableListOf<LocalReaderAccountKey>()

        override suspend fun ensureEnqueued(account: LocalReaderAccountKey) {
            events += "reenqueue"
        }

        override fun cancel(account: LocalReaderAccountKey) {
            events += "cancel"
            canceled += account
        }
    }

    private class RecordingHomeStore(private val events: MutableList<String>) :
        HomeProjectionStore {
        val purged = mutableListOf<HomeAccountScopeKey>()
        val purgedBooks = mutableListOf<String>()

        override suspend fun purgeBook(account: HomeAccountScopeKey, bookId: String) {
            purgedBooks += bookId
        }

        override suspend fun hasSnapshot(account: HomeAccountScopeKey) = false

        override suspend fun purgeAccount(account: HomeAccountScopeKey) {
            events += "home"
            purged += account
        }

        override suspend fun readRecentReading(
            account: HomeAccountScopeKey,
            variant: HomeRecentReadingVariant
        ): HomeProjectionSnapshot<RecentReadingItem>? = null

        override suspend fun replaceRecentReading(
            account: HomeAccountScopeKey,
            variant: HomeRecentReadingVariant,
            items: List<RecentReadingItem>,
            fetchedAt: Instant
        ) = Unit

        override suspend fun readShelves(
            account: HomeAccountScopeKey,
            variant: HomeShelfVariant
        ): HomeProjectionSnapshot<ShelfSummary>? = null

        override suspend fun replaceShelves(
            account: HomeAccountScopeKey,
            variant: HomeShelfVariant,
            shelves: List<ShelfSummary>,
            fetchedAt: Instant
        ) = Unit
    }

    private class RecordingReaderStore(private val events: MutableList<String>) :
        LocalReaderStateStore {
        val purged = mutableListOf<LocalReaderAccountKey>()
        val purgedBooks = mutableListOf<String>()

        override suspend fun bookSummary(account: LocalReaderAccountKey, bookId: String) =
            com.secondpasslibrary.reader.reader.persistence.LocalReaderBookSummary(0, 0)

        override suspend fun purgeBook(account: LocalReaderAccountKey, bookId: String) {
            purgedBooks += bookId
        }

        override suspend fun bookSessionIds(account: LocalReaderAccountKey, bookId: String) =
            listOf("session-$bookId")

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

        override suspend fun purgeAccount(account: LocalReaderAccountKey) {
            events += "reader"
            purged += account
        }
    }

    private companion object {
        val ASSET_BYTES = byteArrayOf(1, 2, 3)
    }
}
