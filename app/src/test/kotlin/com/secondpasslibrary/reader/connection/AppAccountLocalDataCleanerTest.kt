package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionSnapshot
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projection.HomeShelfVariant
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppAccountLocalDataCleanerTest {
    @Test
    fun `cleanup cancels and purges only exact old account before replacement is used`() = runTest {
        val events = mutableListOf<String>()
        val home = RecordingHomeStore(events)
        val reader = RecordingReaderStore(events)
        val scheduler = RecordingScheduler(events)
        val cleaner = AppAccountLocalDataCleaner(home, reader, scheduler)
        val old = AccountLocalDataKey.from("https://old.example", "profile-1")
        val replacement = AccountLocalDataKey.from("https://new.example", "profile-1")

        cleaner.purge(old)

        val oldReader = LocalReaderAccountKey.from(old.serverOrigin, old.profileId)
        assertEquals(listOf("cancel", "home", "reader"), events)
        assertEquals(listOf(oldReader), scheduler.canceled)
        assertEquals(
            listOf(HomeAccountScopeKey.from(old.serverOrigin, old.profileId)),
            home.purged
        )
        assertEquals(listOf(oldReader), reader.purged)
        assertFalse(
            reader.purged.contains(
                LocalReaderAccountKey.from(replacement.serverOrigin, replacement.profileId)
            )
        )
    }

    private class RecordingScheduler(private val events: MutableList<String>) :
        ReaderPendingSyncScheduler {
        val canceled = mutableListOf<LocalReaderAccountKey>()

        override suspend fun ensureEnqueued(account: LocalReaderAccountKey) = Unit

        override fun cancel(account: LocalReaderAccountKey) {
            events += "cancel"
            canceled += account
        }
    }

    private class RecordingHomeStore(private val events: MutableList<String>) :
        HomeProjectionStore {
        val purged = mutableListOf<HomeAccountScopeKey>()

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
}
