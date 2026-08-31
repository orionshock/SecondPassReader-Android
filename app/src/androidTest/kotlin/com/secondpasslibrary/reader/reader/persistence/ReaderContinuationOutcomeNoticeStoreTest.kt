package com.secondpasslibrary.reader.reader.persistence

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.storage.database.SecondPassLocalDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ReaderContinuationOutcomeNoticeStoreTest {
    private lateinit var database: SecondPassLocalDatabase
    private lateinit var dao: LocalReaderDao
    private lateinit var store: RoomReaderContinuationOutcomeNoticeStore

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SecondPassLocalDatabase::class.java
        ).build()
        dao = database.localReaderDao()
        store = RoomReaderContinuationOutcomeNoticeStore(dao)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun pendingNoticeIsAccountScopedAggregatableAndConsumedExactlyOnce() = runBlocking {
        val account = account("profile-a")
        val other = account("profile-b")
        seedOutcome(account, "source-1", edits = 1)
        seedOutcome(account, "source-2", deletes = 2)
        seedOutcome(account, "zero", edits = 0, deletes = 0)
        seedOutcome(other, "other", edits = 8)

        val pending = store.pendingOutcomes(account).first()

        assertEquals(listOf("source-1", "source-2"), pending.map { it.sourceLocalSessionId })
        assertEquals(1, pending.sumOf { it.forwardedEditCount })
        assertEquals(2, pending.sumOf { it.droppedDeleteCount })

        store.consume(account, pending.map { it.sourceLocalSessionId })

        assertEquals(
            emptyList<PendingReaderContinuationOutcome>(),
            store.pendingOutcomes(account).first()
        )
        assertNotNull(dao.continuationOutcome(account.value, "source-1")?.consumedAtEpochMillis)
        assertNull(dao.continuationOutcome(account.value, "zero")?.consumedAtEpochMillis)
        assertEquals(1, store.pendingOutcomes(other).first().size)
    }

    @Test
    fun accountCleanupRemovesPendingNoticeWithoutTouchingAnotherAccount() = runBlocking {
        val account = account("profile-a")
        val other = account("profile-b")
        seedOutcome(account, "source-a", edits = 1)
        seedOutcome(other, "source-b", deletes = 1)

        dao.purgeAccount(account.value)

        assertEquals(
            emptyList<PendingReaderContinuationOutcome>(),
            store.pendingOutcomes(account).first()
        )
        assertEquals("source-b", store.pendingOutcomes(other).first().single().sourceLocalSessionId)
    }

    @Test
    fun backgroundCreatedOutcomeSurvivesDatabaseReopenUntilShellConsumesIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reader-outcome-reopen"
        context.deleteDatabase(name)
        val account = account("profile-a")
        var reopenedDatabase = Room.databaseBuilder(
            context,
            SecondPassLocalDatabase::class.java,
            name
        ).build()
        seedOutcome(
            reopenedDatabase.localReaderDao(),
            account,
            "background-source",
            edits = 1,
            deletes = 1
        )
        reopenedDatabase.close()

        reopenedDatabase = Room.databaseBuilder(
            context,
            SecondPassLocalDatabase::class.java,
            name
        ).build()
        val reopenedStore =
            RoomReaderContinuationOutcomeNoticeStore(reopenedDatabase.localReaderDao())
        val pending = reopenedStore.pendingOutcomes(account).first().single()

        assertEquals("background-source", pending.sourceLocalSessionId)
        assertEquals(1, pending.forwardedEditCount)
        assertEquals(1, pending.droppedDeleteCount)
        reopenedStore.consume(account, listOf(pending.sourceLocalSessionId))
        assertEquals(
            emptyList<PendingReaderContinuationOutcome>(),
            reopenedStore.pendingOutcomes(account).first()
        )
        reopenedDatabase.close()
        context.deleteDatabase(name)
        Unit
    }

    private suspend fun seedOutcome(
        account: LocalReaderAccountKey,
        sourceId: String,
        edits: Int = 0,
        deletes: Int = 0
    ) = seedOutcome(dao, account, sourceId, edits, deletes)

    private suspend fun seedOutcome(
        targetDao: LocalReaderDao,
        account: LocalReaderAccountKey,
        sourceId: String,
        edits: Int = 0,
        deletes: Int = 0
    ) {
        targetDao.upsertSession(
            LocalReaderSessionEntity(
                accountKey = account.value,
                localSessionId = sourceId,
                bookId = "book-$sourceId",
                serverSessionId = UUID.randomUUID().toString(),
                identityKind = "SERVER_CONFIRMED",
                serverStatus = "CLOSED",
                activeProvisionalBookId = null,
                sessionName = null,
                sessionNotes = "",
                startedAt = null,
                closedAt = null,
                lastActivityAt = null,
                serverAnnotationCount = 0,
                createdAtEpochMillis = 1,
                lastUsedAtEpochMillis = 1
            )
        )
        targetDao.upsertContinuationOutcome(
            LocalReaderContinuationOutcomeEntity(
                accountKey = account.value,
                sourceLocalSessionId = sourceId,
                continuationLocalSessionId = null,
                forwardedEditCount = edits,
                droppedDeleteCount = deletes,
                createdAtEpochMillis = 1,
                consumedAtEpochMillis = null
            )
        )
    }

    private fun account(profile: String) =
        LocalReaderAccountKey.from("https://library.example", profile)
}
