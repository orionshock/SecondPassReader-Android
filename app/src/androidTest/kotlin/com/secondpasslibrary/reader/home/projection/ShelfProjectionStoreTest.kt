package com.secondpasslibrary.reader.home.projection

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShelfProjectionStoreTest {
    private lateinit var database: SecondPassReaderDatabase
    private lateinit var store: HomeProjectionStore

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, SecondPassReaderDatabase::class.java).build()
        store =
            RoomHomeProjectionStore(
                database.recentReadingProjectionDao(),
                database.shelfProjectionDao(),
                database.homeProjectionCleanupDao()
            )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun shelfAndPreviewOrderingRoundTripsWithoutReinterpretation() = runBlocking {
        val account = account("profile-1")
        val variant = HomeShelfVariant.FirstPageWithPreviews
        val fetchedAt = Instant.parse("2026-08-16T12:00:00Z")
        store.replaceShelves(
            account,
            variant,
            listOf(
                shelf("second", 7, listOf(preview("b3"), preview("b2", withCover = false))),
                shelf("first", 0, emptyList())
            ),
            fetchedAt
        )

        val snapshot = store.readShelves(account, variant)
        assertNotNull(snapshot)
        assertEquals(fetchedAt, snapshot?.fetchedAt)
        assertEquals(listOf("second", "first"), snapshot?.items?.map { it.id })
        assertEquals(listOf("b3", "b2"), snapshot?.items?.first()?.previewBooks?.map { it.id })
        assertNull(snapshot?.items?.first()?.previewBooks?.last()?.cover)
        assertEquals(0, snapshot?.items?.last()?.itemCount)
        assertEquals(emptyList<Any>(), snapshot?.items?.last()?.previewBooks)
    }

    @Test
    fun shelfReplacementRemovesOldPreviewsAndUpdatesFetchedAt() = runBlocking {
        val account = account("profile-1")
        val variant = HomeShelfVariant.FirstPageWithPreviews
        store.replaceShelves(
            account,
            variant,
            listOf(shelf("old", 2, listOf(preview("old-book")))),
            Instant.parse("2026-08-16T10:00:00Z")
        )

        val replacementTime = Instant.parse("2026-08-16T15:00:00Z")
        store.replaceShelves(
            account,
            variant,
            listOf(shelf("new", 1, previews = null)),
            replacementTime
        )

        val snapshot = store.readShelves(account, variant)
        assertEquals(replacementTime, snapshot?.fetchedAt)
        assertEquals(listOf("new"), snapshot?.items?.map { it.id })
        assertNull(snapshot?.items?.single()?.previewBooks)
    }

    @Test
    fun successfulEmptyShelfSnapshotDoesNotOverwriteRecentReading() = runBlocking {
        val account = account("profile-1")
        val shelfVariant = HomeShelfVariant.FirstPageWithPreviews
        val recentVariant = HomeRecentReadingVariant.IncludingClosed
        store.replaceShelves(
            account,
            shelfVariant,
            listOf(shelf("old", 3)),
            Instant.parse("2026-08-16T10:00:00Z")
        )
        store.replaceRecentReading(
            account,
            recentVariant,
            listOf(recentItem("kept")),
            Instant.parse("2026-08-16T11:00:00Z")
        )

        val emptyTime = Instant.parse("2026-08-16T16:00:00Z")
        store.replaceShelves(account, shelfVariant, emptyList(), emptyTime)

        assertTrue(store.hasSnapshot(account))
        assertEquals(emptyTime, store.readShelves(account, shelfVariant)?.fetchedAt)
        assertEquals(emptyList<Any>(), store.readShelves(account, shelfVariant)?.items)
        assertEquals(
            listOf("kept"),
            store.readRecentReading(account, recentVariant)?.items?.map { it.sessionId }
        )
    }

    private fun account(profileId: String) =
        HomeAccountScopeKey.from("https://library.example", profileId)
}
