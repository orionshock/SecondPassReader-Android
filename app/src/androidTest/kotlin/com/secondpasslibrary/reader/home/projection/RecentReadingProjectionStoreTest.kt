package com.secondpasslibrary.reader.home.projection

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.client.RecentReadingItem
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentReadingProjectionStoreTest {
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
    fun accountAndQueryVariantsRemainIsolated() = runBlocking {
        val firstAccount = account("profile-1")
        val secondAccount = account("profile-2")
        val fetchedAt = Instant.parse("2026-08-16T12:00:00Z")

        store.replaceRecentReading(
            firstAccount,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("first-active")),
            fetchedAt
        )
        store.replaceRecentReading(
            firstAccount,
            HomeRecentReadingVariant.IncludingClosed,
            listOf(recentItem("first-closed")),
            fetchedAt
        )
        store.replaceRecentReading(
            secondAccount,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("second-active")),
            fetchedAt
        )

        assertEquals(
            listOf("first-active"),
            store.readRecentReading(firstAccount, HomeRecentReadingVariant.ActiveOnly)
                ?.items?.map { it.sessionId }
        )
        assertEquals(
            listOf("first-closed"),
            store.readRecentReading(firstAccount, HomeRecentReadingVariant.IncludingClosed)
                ?.items?.map { it.sessionId }
        )
        assertEquals(
            listOf("second-active"),
            store.readRecentReading(secondAccount, HomeRecentReadingVariant.ActiveOnly)
                ?.items?.map { it.sessionId }
        )
    }

    @Test
    fun orderedReplacementPreservesBlankNameNullableProgressAndFetchedAt() = runBlocking {
        val account = account("profile-1")
        val variant = HomeRecentReadingVariant.ActiveOnly
        store.replaceRecentReading(
            account,
            variant,
            listOf(recentItem("second"), recentItem("first")),
            Instant.parse("2026-08-16T10:00:00Z")
        )

        val replacementTime = Instant.parse("2026-08-16T13:00:00Z")
        store.replaceRecentReading(
            account,
            variant,
            listOf(recentItem("replacement", name = "", progress = null)),
            replacementTime
        )

        val snapshot = store.readRecentReading(account, variant)
        assertNotNull(snapshot)
        assertEquals(replacementTime, snapshot?.fetchedAt)
        assertEquals(listOf("replacement"), snapshot?.items?.map { it.sessionId })
        assertEquals("", snapshot?.items?.single()?.sessionName)
        assertNull(snapshot?.items?.single()?.progress)
    }

    @Test
    fun successfulEmptyReplacementRetainsSnapshotAndDoesNotAffectShelves() = runBlocking {
        val account = account("profile-1")
        val recentVariant = HomeRecentReadingVariant.ActiveOnly
        val shelfVariant = HomeShelfVariant.FirstPageWithPreviews
        store.replaceRecentReading(
            account,
            recentVariant,
            listOf(recentItem("old")),
            Instant.parse("2026-08-16T10:00:00Z")
        )
        store.replaceShelves(
            account,
            shelfVariant,
            listOf(shelf("kept", count = 2)),
            Instant.parse("2026-08-16T11:00:00Z")
        )

        val emptyTime = Instant.parse("2026-08-16T14:00:00Z")
        store.replaceRecentReading(account, recentVariant, emptyList(), emptyTime)

        val recent = store.readRecentReading(account, recentVariant)
        assertTrue(store.hasSnapshot(account))
        assertEquals(emptyTime, recent?.fetchedAt)
        assertEquals(emptyList<RecentReadingItem>(), recent?.items)
        assertEquals(
            listOf("kept"),
            store.readShelves(account, shelfVariant)?.items?.map { it.id }
        )
    }

    @Test
    fun accountPurgeRemovesBothHomeProjectionsWithoutTouchingAnotherAccount() = runBlocking {
        val oldAccount = account("profile-1")
        val replacementAccount = account("profile-2")
        val fetchedAt = Instant.parse("2026-08-16T14:00:00Z")
        store.replaceRecentReading(
            oldAccount,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("old")),
            fetchedAt
        )
        store.replaceShelves(
            oldAccount,
            HomeShelfVariant.FirstPageWithPreviews,
            listOf(shelf("old", count = 1)),
            fetchedAt
        )
        store.replaceRecentReading(
            replacementAccount,
            HomeRecentReadingVariant.ActiveOnly,
            listOf(recentItem("replacement")),
            fetchedAt
        )

        store.purgeAccount(oldAccount)

        assertTrue(!store.hasSnapshot(oldAccount))
        assertNull(
            store.readRecentReading(oldAccount, HomeRecentReadingVariant.ActiveOnly)
        )
        assertNull(store.readShelves(oldAccount, HomeShelfVariant.FirstPageWithPreviews))
        assertEquals(
            listOf("replacement"),
            store.readRecentReading(
                replacementAccount,
                HomeRecentReadingVariant.ActiveOnly
            )?.items?.map { it.sessionId }
        )
    }

    private fun account(profileId: String) =
        HomeAccountScopeKey.from("https://library.example/", profileId)
}
