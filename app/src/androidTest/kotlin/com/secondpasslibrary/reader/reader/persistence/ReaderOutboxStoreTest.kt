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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReaderOutboxStoreTest {
    private lateinit var database: SecondPassReaderDatabase
    private lateinit var store: RoomLocalReaderStateStore
    private lateinit var outbox: RoomReaderOutboxStore

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            SecondPassReaderDatabase::class.java
        ).build()
        store = RoomLocalReaderStateStore(database.localReaderDao())
        outbox = RoomReaderOutboxStore(database.localReaderDao())
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun provisionalSessionHasOneOrderedEstablishmentIntent() = runBlocking {
        val account = account("one")
        val session = store.selectOfflineSession(account, "book-1")
        store.selectOfflineSession(account, "book-1")

        val establishments = outbox.pendingSessionEstablishments(account)
        val sessionIntents = outbox.pendingReaderIntents(account, session.sessionId)

        assertEquals(1, establishments.size)
        assertEquals(session.sessionId, establishments.single().localSessionId)
        assertTrue(sessionIntents.single() is ReaderOutboxIntent.EstablishSession)

        val confirmed = serverSession("server-1")
        store.retainServerSession(account, "book-2", confirmed)
        assertTrue(outbox.pendingReaderIntents(account, confirmed.sessionId).isEmpty())
    }

    @Test
    fun pendingSessionsExposeExactAccountScopedReconciliationInputs() = runBlocking {
        val firstAccount = account("one")
        val otherAccount = account("other")
        val provisional = store.selectOfflineSession(firstAccount, "book-1")
        val confirmed = serverSession("server-2")
        store.retainServerSession(firstAccount, "book-2", confirmed)
        store.writeProgress(
            firstAccount,
            confirmed.sessionId,
            CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        store.selectOfflineSession(otherAccount, "book-other")

        val pending = outbox.pendingSessions(firstAccount)

        assertEquals(setOf("book-1", "book-2"), pending.map { it.bookId }.toSet())
        assertTrue(pending.any { it.session.sessionId == provisional.sessionId })
        assertTrue(pending.any { it.session.serverSessionId == confirmed.serverSessionId })
        assertTrue(pending.none { it.bookId == "book-other" })
    }

    @Test
    fun progressCoalescesToLatestExactCfi() = runBlocking {
        val account = account("one")
        val session = store.selectOfflineSession(account, "book-1")

        store.writeProgress(
            account,
            session.sessionId,
            CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        store.writeProgress(
            account,
            session.sessionId,
            NEXT_CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )

        val intents = outbox.pendingReaderIntents(account, session.sessionId)
        assertTrue(intents.first() is ReaderOutboxIntent.EstablishSession)
        assertEquals(
            NEXT_CFI,
            (
                intents.single {
                    it is ReaderOutboxIntent.Progress
                } as ReaderOutboxIntent.Progress
                ).cfi
        )
        assertEquals(2, intents.size)

        store.acknowledgeProgress(account, session.sessionId, CFI)
        assertTrue(
            outbox.pendingReaderIntents(account, session.sessionId).any {
                it is ReaderOutboxIntent.Progress
            }
        )
        store.acknowledgeProgress(account, session.sessionId, NEXT_CFI)
        assertFalse(
            outbox.pendingReaderIntents(account, session.sessionId).any {
                it is ReaderOutboxIntent.Progress
            }
        )
    }

    @Test
    fun localCreateEditDeleteCollapsesWithoutServerMutation() = runBlocking {
        val account = account("one")
        val session = store.selectOfflineSession(account, "book-1")

        store.applyAnnotationMutation(account, session.sessionId, highlight("first"))
        store.applyAnnotationMutation(account, session.sessionId, highlight("latest"))
        val upsert = outbox.pendingReaderIntents(account, session.sessionId)
            .single { it is ReaderOutboxIntent.AnnotationUpsert } as
            ReaderOutboxIntent.AnnotationUpsert
        assertEquals("latest", upsert.note)

        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.Delete(session.sessionId, CLIENT_ID)
        )

        assertFalse(
            outbox.pendingReaderIntents(account, session.sessionId).any {
                it is ReaderOutboxIntent.AnnotationUpsert ||
                    it is ReaderOutboxIntent.AnnotationDelete
            }
        )
        assertTrue(store.readAnnotations(account, session.sessionId).isEmpty())
        assertEquals(
            LocalAnnotationSync.LOCAL_DELETED,
            database.localReaderDao().annotation(
                account.value,
                session.sessionId,
                CLIENT_ID
            )?.syncState
        )
    }

    @Test
    fun knownServerAnnotationDeleteAndRestoreConvergeToLatestIntent() = runBlocking {
        val account = account("one")
        val session = serverSession("server-1")
        store.retainServerSession(account, "book-1", session)
        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(ReaderAnnotation.Bookmark("annotation-1", CLIENT_ID, CFI, "One", "now"))
        )

        store.applyAnnotationMutation(account, session.sessionId, highlight("edited"))
        assertTrue(
            outbox.pendingReaderIntents(account, session.sessionId).single() is
                ReaderOutboxIntent.AnnotationUpsert
        )

        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.Delete(session.sessionId, CLIENT_ID)
        )
        assertTrue(
            outbox.pendingReaderIntents(account, session.sessionId).single() is
                ReaderOutboxIntent.AnnotationDelete
        )

        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.UpsertBookmark(
                session.sessionId,
                CLIENT_ID,
                NEXT_CFI,
                "Two"
            )
        )
        val restored = outbox.pendingReaderIntents(account, session.sessionId).single()
            as ReaderOutboxIntent.AnnotationUpsert
        assertEquals(NEXT_CFI, restored.cfi)
    }

    @Test
    fun canonicalAnnotationRollsBackWhenOutboxWriteFails() = runBlocking {
        val account = account("one")
        val session = store.selectOfflineSession(account, "book-1")
        val annotation = highlight("atomic").toEntity(account, session.sessionId)
        val invalidOutbox = annotation.toUpsertOutbox("book-1", 1L).copy(
            localSessionId = "missing-parent"
        )

        val result = runCatching {
            database.localReaderDao().writeAnnotationUpsert(annotation, invalidOutbox)
        }

        assertTrue(result.isFailure)
        assertNull(
            database.localReaderDao().annotation(
                account.value,
                session.sessionId,
                CLIENT_ID
            )
        )
    }

    @Test
    fun refreshPreservesPendingIntentAndAcceptedResponseAcknowledgesOnlyIt() = runBlocking {
        val account = account("one")
        val session = serverSession("server-1")
        store.retainServerSession(account, "book-1", session)
        store.applyAnnotationMutation(account, session.sessionId, highlight("local"))

        store.replaceAuthoritativeAnnotations(account, session.sessionId, emptyList())
        assertTrue(outbox.hasPendingWork(account))
        assertEquals(
            "local",
            (
                store.readAnnotations(account, session.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )

        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(serverHighlight("server")),
            confirmedClientId = CLIENT_ID
        )
        assertFalse(outbox.hasPendingWork(account))
        assertEquals(
            "server",
            (
                store.readAnnotations(account, session.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
    }

    @Test
    fun deliveredAnnotationAcknowledgesOnlyExactSentDesiredState() = runBlocking {
        val account = account("one")
        val session = serverSession("server-1")
        store.retainServerSession(account, "book-1", session)
        store.applyAnnotationMutation(account, session.sessionId, highlight("sent"))
        val sent = outbox.pendingReaderIntents(account, session.sessionId)
            .filterIsInstance<ReaderOutboxIntent.AnnotationUpsert>()

        store.applyAnnotationMutation(account, session.sessionId, highlight("newer"))
        outbox.acceptAnnotationBatch(
            account,
            session.sessionId,
            sent,
            listOf(serverHighlight("sent"))
        )

        val pending = outbox.pendingReaderIntents(account, session.sessionId)
            .filterIsInstance<ReaderOutboxIntent.AnnotationUpsert>()
            .single()
        assertEquals("newer", pending.note)
        assertEquals(
            "newer",
            (
                store.readAnnotations(account, session.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
    }

    @Test
    fun exactAnnotationDeliveryStoresAuthorityAndFinalizesDeleteTombstone() = runBlocking {
        val account = account("one")
        val session = serverSession("server-1")
        store.retainServerSession(account, "book-1", session)
        store.applyAnnotationMutation(account, session.sessionId, highlight("sent"))
        val sentUpsert = outbox.pendingReaderIntents(account, session.sessionId)
            .filterIsInstance<ReaderOutboxIntent.AnnotationUpsert>()
        outbox.acceptAnnotationBatch(
            account,
            session.sessionId,
            sentUpsert,
            listOf(serverHighlight("confirmed"))
        )
        assertTrue(outbox.pendingReaderIntents(account, session.sessionId).isEmpty())
        assertEquals(
            "confirmed",
            (
                store.readAnnotations(account, session.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )

        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.Delete(session.sessionId, CLIENT_ID)
        )
        val sentDelete = outbox.pendingReaderIntents(account, session.sessionId)
            .filterIsInstance<ReaderOutboxIntent.AnnotationDelete>()
        outbox.acceptAnnotationBatch(account, session.sessionId, sentDelete, emptyList())

        assertTrue(outbox.pendingReaderIntents(account, session.sessionId).isEmpty())
        assertTrue(store.readAnnotations(account, session.sessionId).isEmpty())
        assertNull(
            database.localReaderDao().annotation(account.value, session.sessionId, CLIENT_ID)
        )
    }

    @Test
    fun `only active bound Sessions without establishment are delivery eligible`() = runBlocking {
        val account = account("one")
        val provisional = store.selectOfflineSession(account, "book-1")
        store.applyAnnotationMutation(account, provisional.sessionId, highlight("pending"))
        val confirmed = serverSession("server-2")
        store.retainServerSession(account, "book-2", confirmed)
        store.writeProgress(
            account,
            confirmed.sessionId,
            CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )

        val eligible = outbox.boundPendingSessions(account)

        assertEquals(listOf(confirmed.sessionId), eligible.map { it.localSessionId })
        assertEquals("server-2", eligible.single().serverSessionId)
    }

    @Test
    fun sameClientIdAndCleanupRemainAccountAndSessionScoped() = runBlocking {
        val firstAccount = account("one")
        val secondAccount = account("two")
        val first = store.selectOfflineSession(firstAccount, "book-1")
        val otherBook = store.selectOfflineSession(firstAccount, "book-2")
        val otherAccount = store.selectOfflineSession(secondAccount, "book-1")
        listOf(first to firstAccount, otherBook to firstAccount, otherAccount to secondAccount)
            .forEach { (session, account) ->
                store.applyAnnotationMutation(account, session.sessionId, highlight("same"))
            }

        assertTrue(
            outbox.pendingReaderIntents(firstAccount, first.sessionId).any {
                it is ReaderOutboxIntent.AnnotationUpsert
            }
        )
        assertTrue(
            outbox.pendingReaderIntents(firstAccount, otherBook.sessionId).any {
                it is ReaderOutboxIntent.AnnotationUpsert
            }
        )

        store.purgeAccount(firstAccount)
        assertFalse(outbox.hasPendingWork(firstAccount))
        assertTrue(outbox.hasPendingWork(secondAccount))
    }

    private fun highlight(note: String) = ReaderAnnotationMutationRequest.UpsertHighlight(
        "ignored",
        CLIENT_ID,
        RANGE_CFI,
        "Chapter 1",
        "quote",
        "prefix",
        "suffix",
        ReaderAnnotationColor.YELLOW,
        note
    )

    private fun serverHighlight(note: String) = ReaderAnnotation.Highlight(
        "annotation-1",
        CLIENT_ID,
        RANGE_CFI,
        "Chapter 1",
        "now",
        "quote",
        "prefix",
        "suffix",
        note,
        ReaderAnnotationColor.YELLOW
    )

    private fun account(profileId: String) =
        LocalReaderAccountKey.from("https://library.example", profileId)

    private fun serverSession(id: String) =
        ReaderSessionContext(id, ReaderSessionStatus.ACTIVE, CFI)

    private companion object {
        const val CLIENT_ID = "client-1"
        const val CFI = "epubcfi(/6/2!/4/2:3)"
        const val NEXT_CFI = "epubcfi(/6/4!/4/2:8)"
        const val RANGE_CFI = "epubcfi(/6/2!/4/2,:3,:9)"
    }
}
