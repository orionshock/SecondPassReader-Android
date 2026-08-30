package com.secondpasslibrary.reader.reader.persistence

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.reader.home.projection.SecondPassReaderDatabase
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReaderSessionBindingStoreTest {
    private lateinit var database: SecondPassReaderDatabase
    private lateinit var local: RoomLocalReaderStateStore
    private lateinit var outbox: RoomReaderOutboxStore
    private lateinit var bindings: RoomReaderSessionBindingStore

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            SecondPassReaderDatabase::class.java
        ).build()
        val dao = database.localReaderDao()
        local = RoomLocalReaderStateStore(dao)
        outbox = RoomReaderOutboxStore(dao)
        bindings = RoomReaderSessionBindingStore(dao)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun provisionalBindingIsAtomicAndPreservesReaderMutationIntents() = runBlocking {
        val account = account("one")
        val provisional = local.selectOfflineSession(account, BOOK_ID)
        local.writeProgress(
            account,
            provisional.sessionId,
            LOCAL_CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        local.applyAnnotationMutation(account, provisional.sessionId, highlight(provisional))

        val bound = bindings.bindProvisional(
            account,
            BOOK_ID,
            provisional.sessionId,
            server("server-active", SERVER_CFI)
        )

        assertEquals(provisional.sessionId, bound.sessionId)
        assertEquals("server-active", bound.serverSessionId)
        assertEquals(ReaderSessionIdentityKind.SERVER_CONFIRMED, bound.identityKind)
        assertEquals(LOCAL_CFI, bound.savedProgressCfi)
        val intents = outbox.pendingReaderIntents(account, provisional.sessionId)
        assertEquals(2, intents.size)
        assertTrue(intents.none { it is ReaderOutboxIntent.EstablishSession })
        assertTrue(intents.any { it is ReaderOutboxIntent.Progress })
        assertTrue(intents.any { it is ReaderOutboxIntent.AnnotationUpsert })
        assertTrue(
            local.readAnnotations(account, provisional.sessionId).single() is
                ReaderAnnotation.Highlight
        )
    }

    @Test
    fun repeatedBindingIsIdempotent() = runBlocking {
        val account = account("one")
        val provisional = local.selectOfflineSession(account, BOOK_ID)
        val authoritative = server("server-active", SERVER_CFI)

        val first = bindings.bindProvisional(
            account,
            BOOK_ID,
            provisional.sessionId,
            authoritative
        )
        val second = bindings.bindProvisional(
            account,
            BOOK_ID,
            provisional.sessionId,
            authoritative
        )

        assertEquals(first, second)
        assertTrue(outbox.pendingReaderIntents(account, provisional.sessionId).isEmpty())
    }

    @Test
    fun duplicateCachedBindingWithoutPendingWorkConvergesOntoProvisionalIdentity() = runBlocking {
        val account = account("one")
        val provisional = local.selectOfflineSession(account, BOOK_ID)
        local.retainServerSession(account, BOOK_ID, server("server-active", SERVER_CFI))

        val bound = bindings.bindProvisional(
            account,
            BOOK_ID,
            provisional.sessionId,
            server("server-active", SERVER_CFI)
        )

        assertEquals(provisional.sessionId, bound.sessionId)
        assertEquals("server-active", bound.serverSessionId)
        assertNull(database.localReaderDao().session(account.value, "server-active"))
    }

    @Test
    fun bindingConflictWithPendingWorkRollsBackAndKeepsEstablishment() = runBlocking {
        val account = account("one")
        val provisional = local.selectOfflineSession(account, BOOK_ID)
        val duplicate = server("server-active", SERVER_CFI)
        local.retainServerSession(account, BOOK_ID, duplicate)
        local.applyAnnotationMutation(account, duplicate.sessionId, highlight(duplicate))

        val failure = runCatching {
            bindings.bindProvisional(
                account,
                BOOK_ID,
                provisional.sessionId,
                duplicate
            )
        }

        assertTrue(failure.isFailure)
        val retained = database.localReaderDao().session(account.value, provisional.sessionId)
        assertEquals(ReaderSessionIdentityKind.PROVISIONAL.name, retained?.identityKind)
        assertNull(retained?.serverSessionId)
        assertNotNull(database.localReaderDao().session(account.value, duplicate.sessionId))
        assertTrue(
            outbox.pendingReaderIntents(account, provisional.sessionId).any {
                it is ReaderOutboxIntent.EstablishSession
            }
        )
    }

    @Test
    fun closedRefreshPreservesPendingWorkAndNewActiveRemainsSeparate() = runBlocking {
        val account = account("one")
        val former = server("server-old", SERVER_CFI)
        local.retainServerSession(account, BOOK_ID, former)
        local.applyAnnotationMutation(account, former.sessionId, highlight(former))

        val closed = bindings.refreshConfirmed(
            account,
            BOOK_ID,
            former.sessionId,
            server("server-old", SERVER_CFI, ReaderSessionStatus.CLOSED)
        )
        val current = local.retainServerSession(
            account,
            BOOK_ID,
            server("server-new", null)
        )

        assertEquals(ReaderSessionStatus.CLOSED, closed.status)
        assertEquals("server-new", current.sessionId)
        assertTrue(
            outbox.pendingReaderIntents(account, former.sessionId).any {
                it is ReaderOutboxIntent.AnnotationUpsert
            }
        )
        assertEquals(
            ReaderSessionStatus.CLOSED.name,
            database.localReaderDao().session(account.value, former.sessionId)?.serverStatus
        )
    }

    @Test
    fun wrongBookOrAccountCannotAcquireBinding() = runBlocking {
        val first = account("one")
        val other = account("two")
        val provisional = local.selectOfflineSession(first, BOOK_ID)

        assertTrue(
            runCatching {
                bindings.bindProvisional(
                    first,
                    "other-book",
                    provisional.sessionId,
                    server("server-active", null)
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                bindings.bindProvisional(
                    other,
                    BOOK_ID,
                    provisional.sessionId,
                    server("server-active", null)
                )
            }.isFailure
        )
    }

    private fun highlight(session: ReaderSessionContext) =
        ReaderAnnotationMutationRequest.UpsertHighlight(
            session.sessionId,
            "client-1",
            RANGE_CFI,
            "Chapter 1",
            "quote",
            "prefix",
            "suffix",
            ReaderAnnotationColor.YELLOW,
            "note"
        )

    private fun server(
        id: String,
        progress: String?,
        status: ReaderSessionStatus = ReaderSessionStatus.ACTIVE
    ) = ReaderSessionContext(
        sessionId = id,
        status = status,
        savedProgressCfi = progress,
        sessionName = "Authoritative",
        sessionNotes = "Server notes"
    )

    private fun account(profileId: String) =
        LocalReaderAccountKey.from("https://library.example", profileId)

    private companion object {
        const val BOOK_ID = "book-1"
        const val LOCAL_CFI = "epubcfi(/6/2!/4/2:3)"
        const val SERVER_CFI = "epubcfi(/6/4!/4/2:8)"
        const val RANGE_CFI = "epubcfi(/6/2!/4/2,:3,:9)"
    }
}
