package com.secondpasslibrary.reader.reader.persistence

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.reader.home.projection.SecondPassReaderDatabase
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReaderClosedSessionContinuationStoreTest {
    private lateinit var database: SecondPassReaderDatabase
    private lateinit var local: RoomLocalReaderStateStore
    private lateinit var outbox: RoomReaderOutboxStore
    private lateinit var continuation: RoomReaderClosedSessionContinuationStore

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SecondPassReaderDatabase::class.java
        ).build()
        val dao = database.localReaderDao()
        local = RoomLocalReaderStateStore(dao)
        outbox = RoomReaderOutboxStore(dao)
        continuation = RoomReaderClosedSessionContinuationStore(dao)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun closedSessionAtomicallyForwardsEligibleIntentAndRestoresHistory() = runBlocking {
        val account = account()
        val active = server(ReaderSessionStatus.ACTIVE, SERVER_CFI)
        local.retainServerSession(account, BOOK_ID, active)
        val originalEdited = highlight("server-edit", "edit-client", "old note")
        val originalDeleted = bookmark("server-delete", "delete-client")
        val authoritative = listOf(originalEdited, originalDeleted)
        local.replaceAuthoritativeAnnotations(account, active.sessionId, authoritative)

        local.writeProgress(
            account,
            active.sessionId,
            OFFLINE_CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        local.applyAnnotationMutation(
            account,
            active.sessionId,
            highlightUpsert("edit-client", "edited offline")
        )
        local.applyAnnotationMutation(
            account,
            active.sessionId,
            ReaderAnnotationMutationRequest.Delete(
                active.sessionId,
                "delete-client",
                originalDeleted
            )
        )
        local.applyAnnotationMutation(
            account,
            active.sessionId,
            highlightUpsert("new-client", "new offline")
        )
        local.applyAnnotationMutation(
            account,
            active.sessionId,
            ReaderAnnotationMutationRequest.UpsertBookmark(
                active.sessionId,
                "new-bookmark",
                POINT_CFI,
                "Offline bookmark"
            )
        )
        local.applyAnnotationMutation(
            account,
            active.sessionId,
            highlightUpsert("discarded-client", "discard me")
        )
        local.applyAnnotationMutation(
            account,
            active.sessionId,
            ReaderAnnotationMutationRequest.Delete(active.sessionId, "discarded-client")
        )

        val first = continuation.continueFrom(
            account,
            BOOK_ID,
            server(ReaderSessionStatus.CLOSED, SERVER_CFI),
            authoritative
        )
        val next = requireNotNull(first.session)

        assertEquals(1, first.forwardedEditCount)
        assertEquals(1, first.droppedDeleteCount)
        assertEquals(OFFLINE_CFI, next.savedProgressCfi)
        assertEquals(ReaderSessionStatus.CLOSED.name, session(active.sessionId)?.serverStatus)
        assertEquals(
            SERVER_CFI,
            database.localReaderDao().progress(account.value, active.sessionId)?.cfi
        )
        assertEquals(authoritative, local.readAnnotations(account, active.sessionId))

        val forwarded = local.readAnnotations(account, next.sessionId)
        assertEquals(3, forwarded.size)
        assertTrue(forwarded.any { it.clientId == "new-client" })
        assertTrue(forwarded.any { it.clientId == "new-bookmark" })
        val duplicated = forwarded.single { it.clientId !in setOf("new-client", "new-bookmark") }
            as ReaderAnnotation.Highlight
        assertNotEquals("edit-client", duplicated.clientId)
        assertEquals("edited offline", duplicated.note)
        assertEquals(RANGE_CFI, duplicated.cfi)
        assertEquals("quote", duplicated.quote)
        assertEquals("prefix", duplicated.prefix)
        assertEquals("suffix", duplicated.suffix)
        assertEquals("Chapter 1", duplicated.locationLabel)
        assertEquals(ReaderAnnotationColor.GREEN, duplicated.color)
        assertTrue(forwarded.none { it.clientId == "discarded-client" })
        assertTrue(forwarded.none { it.clientId == "delete-client" })

        val intents = outbox.pendingReaderIntents(account, next.sessionId)
        assertEquals(5, intents.size)
        assertEquals(1, intents.count { it is ReaderOutboxIntent.Progress })
        assertEquals(3, intents.count { it is ReaderOutboxIntent.AnnotationUpsert })
        assertTrue(intents.any { it is ReaderOutboxIntent.EstablishSession })
        assertTrue(outbox.pendingReaderIntents(account, active.sessionId).isEmpty())

        val repeated = continuation.continueFrom(
            account,
            BOOK_ID,
            server(ReaderSessionStatus.CLOSED, SERVER_CFI),
            authoritative
        )
        assertEquals(next.sessionId, repeated.session?.sessionId)
        assertEquals(
            forwarded.map { it.clientId }.toSet(),
            local.readAnnotations(account, next.sessionId).map { it.clientId }.toSet()
        )
    }

    @Test
    fun impossibleDeleteAloneRecordsOutcomeWithoutCreatingContinuation() = runBlocking {
        val account = account()
        val active = server(ReaderSessionStatus.ACTIVE, SERVER_CFI)
        val original = bookmark("server-delete", "delete-client")
        local.retainServerSession(account, BOOK_ID, active)
        local.replaceAuthoritativeAnnotations(account, active.sessionId, listOf(original))
        local.applyAnnotationMutation(
            account,
            active.sessionId,
            ReaderAnnotationMutationRequest.Delete(active.sessionId, original.clientId, original)
        )

        val result = continuation.continueFrom(
            account,
            BOOK_ID,
            server(ReaderSessionStatus.CLOSED, SERVER_CFI),
            listOf(original)
        )

        assertNull(result.session)
        assertEquals(1, result.droppedDeleteCount)
        assertEquals(listOf(original), local.readAnnotations(account, active.sessionId))
        assertTrue(outbox.pendingReaderIntents(account, active.sessionId).isEmpty())
        assertNotNull(
            database.localReaderDao().continuationOutcome(account.value, active.sessionId)
        )
    }

    @Test
    fun transactionFailureLeavesClosedSessionTransformationUncommitted() = runBlocking {
        val account = account()
        val active = server(ReaderSessionStatus.ACTIVE, SERVER_CFI)
        local.retainServerSession(account, BOOK_ID, active)
        local.writeProgress(
            account,
            active.sessionId,
            OFFLINE_CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        val dao = database.localReaderDao()
        val current = requireNotNull(dao.session(account.value, active.sessionId))
        val now = Instant.now().toEpochMilli()
        val invalidAnnotation = bookmark("server-invalid", "invalid-client")
            .toEntity(account, "missing-session")
        val candidate = current.copy(
            localSessionId = UUID.randomUUID().toString(),
            serverSessionId = null,
            identityKind = "PROVISIONAL",
            serverStatus = null,
            activeProvisionalBookId = BOOK_ID,
            createdAtEpochMillis = now,
            lastUsedAtEpochMillis = now
        )

        val result = runCatching {
            dao.continueClosedSession(
                current.copy(serverStatus = ReaderSessionStatus.CLOSED.name),
                SERVER_CFI,
                listOf(invalidAnnotation),
                candidate,
                now
            )
        }

        assertTrue(result.isFailure)
        assertEquals(
            ReaderSessionStatus.ACTIVE.name,
            dao.session(account.value, active.sessionId)?.serverStatus
        )
        assertEquals(OFFLINE_CFI, dao.progress(account.value, active.sessionId)?.cfi)
        assertTrue(outbox.pendingReaderIntents(account, active.sessionId).isNotEmpty())
        assertNull(dao.continuationOutcome(account.value, active.sessionId))
    }

    private suspend fun session(localSessionId: String) =
        database.localReaderDao().session(account().value, localSessionId)

    private fun server(status: ReaderSessionStatus, cfi: String?) = ReaderSessionContext(
        sessionId = "server-old",
        status = status,
        savedProgressCfi = cfi
    )

    private fun highlight(id: String, clientId: String, note: String) = ReaderAnnotation.Highlight(
        id,
        clientId,
        RANGE_CFI,
        "Chapter 1",
        "2026-08-30T12:00:00Z",
        "quote",
        "prefix",
        "suffix",
        note,
        ReaderAnnotationColor.YELLOW
    )

    private fun bookmark(id: String, clientId: String) = ReaderAnnotation.Bookmark(
        id,
        clientId,
        POINT_CFI,
        "Chapter 1",
        "2026-08-30T12:00:00Z"
    )

    private fun highlightUpsert(clientId: String, note: String) =
        ReaderAnnotationMutationRequest.UpsertHighlight(
            "server-old",
            clientId,
            RANGE_CFI,
            "Chapter 1",
            "quote",
            "prefix",
            "suffix",
            ReaderAnnotationColor.GREEN,
            note
        )

    private fun account() = LocalReaderAccountKey.from("https://library.example", "profile")

    private companion object {
        const val BOOK_ID = "book-1"
        const val SERVER_CFI = "epubcfi(/6/2!/4/2:1)"
        const val OFFLINE_CFI = "epubcfi(/6/4!/4/2:9)"
        const val RANGE_CFI = "epubcfi(/6/4!/4/2,:2,:8)"
        const val POINT_CFI = "epubcfi(/6/4!/4/2:4)"
    }
}
