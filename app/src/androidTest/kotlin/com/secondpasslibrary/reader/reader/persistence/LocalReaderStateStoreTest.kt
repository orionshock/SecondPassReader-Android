package com.secondpasslibrary.reader.reader.persistence

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.storage.database.SecondPassLocalDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalReaderStateStoreTest {
    private lateinit var database: SecondPassLocalDatabase
    private lateinit var store: RoomLocalReaderStateStore

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            SecondPassLocalDatabase::class.java
        ).build()
        store = RoomLocalReaderStateStore(database.localReaderDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun activeServerSessionWinsAndClosedSessionCreatesOneStableProvisional() = runBlocking {
        val account = account("profile-1")
        store.retainServerSession(account, "book-active", serverSession("server-active"))
        store.retainServerSession(
            account,
            "book-closed",
            serverSession("server-closed", ReaderSessionStatus.CLOSED)
        )

        val active = store.selectOfflineSession(account, "book-active")
        val provisional = store.selectOfflineSession(account, "book-closed")
        val reopened = store.selectOfflineSession(account, "book-closed")

        assertEquals("server-active", active.serverSessionId)
        assertEquals(ReaderSessionIdentityKind.SERVER_CONFIRMED, active.identityKind)
        assertEquals(ReaderSessionIdentityKind.PROVISIONAL, provisional.identityKind)
        assertNull(provisional.serverSessionId)
        assertEquals(provisional.sessionId, reopened.sessionId)
    }

    @Test
    fun accountAndBookScopesDoNotShareProvisionalSessions() = runBlocking {
        val first = store.selectOfflineSession(account("profile-1"), "book-1")
        val otherBook = store.selectOfflineSession(account("profile-1"), "book-2")
        val otherAccount = store.selectOfflineSession(account("profile-2"), "book-1")

        assertTrue(first.sessionId != otherBook.sessionId)
        assertTrue(first.sessionId != otherAccount.sessionId)
    }

    @Test
    fun progressAndAnnotationFieldsRoundTripWithoutNormalization() = runBlocking {
        val account = account("profile-1")
        val session = store.selectOfflineSession(account, "book-1")
        store.writeProgress(
            account,
            session.sessionId,
            CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.UpsertHighlight(
                session.sessionId,
                "client-1",
                RANGE_CFI,
                "Chapter 2",
                "exact quote",
                " prefix\n",
                "\tsuffix ",
                ReaderAnnotationColor.PURPLE,
                "  first line\n\tsecond line  "
            )
        )

        val reopened = store.selectOfflineSession(account, "book-1")
        val highlight = store.readAnnotations(account, session.sessionId).single()
            as ReaderAnnotation.Highlight

        assertEquals(CFI, reopened.savedProgressCfi)
        assertEquals(" prefix\n", highlight.prefix)
        assertEquals("\tsuffix ", highlight.suffix)
        assertEquals("  first line\n\tsecond line  ", highlight.note)
        assertEquals(ReaderAnnotationColor.PURPLE, highlight.color)
    }

    @Test
    fun locallyDirtyProgressCannotMutateKnownClosedServerSession() = runBlocking {
        val account = account("profile-1")
        store.retainServerSession(
            account,
            "book-1",
            serverSession("server-closed", ReaderSessionStatus.CLOSED)
        )

        store.writeProgress(
            account,
            "server-closed",
            "epubcfi(/6/8!/4/2:9)",
            LocalReaderWriteProvenance.LOCAL_PENDING
        )

        assertEquals(
            CFI,
            database.localReaderDao().progress(account.value, "server-closed")?.cfi
        )
    }

    @Test
    fun bookmarkIsBodylessAndSameClientIdIsSessionScoped() = runBlocking {
        val account = account("profile-1")
        val first = store.selectOfflineSession(account, "book-1")
        val second = store.selectOfflineSession(account, "book-2")
        for (session in listOf(first, second)) {
            store.applyAnnotationMutation(
                account,
                session.sessionId,
                ReaderAnnotationMutationRequest.UpsertBookmark(
                    session.sessionId,
                    "shared-client",
                    CFI,
                    "Chapter 1"
                )
            )
        }

        assertTrue(
            store.readAnnotations(account, first.sessionId).single() is ReaderAnnotation.Bookmark
        )
        assertTrue(
            store.readAnnotations(account, second.sessionId).single() is ReaderAnnotation.Bookmark
        )
    }

    @Test
    fun newerLocalProgressIsNotOverwrittenByServerProjectionRefresh() = runBlocking {
        val account = account("profile-1")
        val server = serverSession("server-active")
        store.retainServerSession(account, "book-1", server)
        store.writeProgress(
            account,
            server.sessionId,
            NEXT_CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )

        val retained = store.retainServerSession(account, "book-1", server)

        assertEquals(NEXT_CFI, retained.savedProgressCfi)
        assertEquals(
            LocalReaderWriteProvenance.LOCAL_PENDING.name,
            database.localReaderDao().progress(account.value, server.sessionId)?.provenance
        )
    }

    @Test
    fun onlineRefreshRetainsStableLocalIdentityAfterProvisionalBinding() = runBlocking {
        val account = account("profile-1")
        val provisional = store.selectOfflineSession(account, "book-1")
        val authoritative = serverSession("server-active")
        RoomReaderSessionBindingStore(database.localReaderDao()).bindProvisional(
            account,
            "book-1",
            provisional.sessionId,
            authoritative
        )
        store.applyAnnotationMutation(
            account,
            provisional.sessionId,
            ReaderAnnotationMutationRequest.UpsertBookmark(
                provisional.sessionId,
                "client-1",
                CFI,
                "Chapter 1"
            )
        )

        val retained = store.retainServerSession(account, "book-1", authoritative)

        assertEquals(provisional.sessionId, retained.sessionId)
        assertEquals(authoritative.serverSessionId, retained.serverSessionId)
        assertEquals(
            "client-1",
            store.readAnnotations(account, retained.sessionId).single().clientId
        )
        assertNull(database.localReaderDao().session(account.value, authoritative.sessionId))
    }

    @Test
    fun refreshingServerSessionMetadataDoesNotCascadeDeleteLocalReaderState() = runBlocking {
        val account = account("profile-1")
        val session = serverSession("server-active")
        store.retainServerSession(account, "book-1", session)
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.UpsertBookmark(
                session.sessionId,
                "client-1",
                CFI,
                "Chapter 1"
            )
        )

        store.retainServerSession(account, "book-1", session.copy(sessionName = "Updated"))

        assertEquals(
            "client-1",
            store.readAnnotations(account, session.sessionId).single().clientId
        )
    }

    @Test
    fun deleteLeavesPendingTombstoneAndAuthoritativeRefreshDoesNotEraseIt() = runBlocking {
        val account = account("profile-1")
        val session = store.selectOfflineSession(account, "book-1")
        val upsert = ReaderAnnotationMutationRequest.UpsertBookmark(
            session.sessionId,
            "client-1",
            CFI,
            "Chapter 1"
        )
        store.applyAnnotationMutation(account, session.sessionId, upsert)
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.Delete(session.sessionId, "client-1")
        )
        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(ReaderAnnotation.Bookmark("server-1", "client-1", CFI, null, "now"))
        )

        assertTrue(store.readAnnotations(account, session.sessionId).isEmpty())
        assertEquals(
            "LOCAL_DELETED",
            database.localReaderDao().annotation(
                account.value,
                session.sessionId,
                "client-1"
            )?.syncState
        )

        assertTrue(
            database.localReaderDao().annotation(
                account.value,
                session.sessionId,
                "client-1"
            ) != null
        )
    }

    @Test
    fun authoritativeRefreshReplacesConfirmedProjectionWithoutPendingState() = runBlocking {
        val account = account("profile-1")
        val session = serverSession("server-active")
        store.retainServerSession(account, "book-1", session)
        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(serverHighlight("client-1", "old"))
        )

        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(serverHighlight("client-1", "new"))
        )

        assertEquals(
            "new",
            (
                store.readAnnotations(account, session.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
        assertFalse(
            database.localReaderDao().hasPendingWorkForSession(
                account.value,
                session.sessionId
            )
        )
    }

    @Test
    fun pendingCreateAndEditRemainVisibleAcrossAuthoritativeRefresh() = runBlocking {
        val account = account("profile-1")
        val session = serverSession("server-active")
        store.retainServerSession(account, "book-1", session)
        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(serverHighlight("edited-client", "server"))
        )
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            highlightMutation("new-client", "new local")
        )
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            highlightMutation("edited-client", "edited local")
        )

        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(serverHighlight("edited-client", "stale server"))
        )

        val highlights = store.readAnnotations(account, session.sessionId)
            .filterIsInstance<ReaderAnnotation.Highlight>()
            .associateBy { it.clientId }
        assertEquals("new local", highlights.getValue("new-client").note)
        assertEquals("edited local", highlights.getValue("edited-client").note)
    }

    @Test
    fun localEditCommittedAfterLegacyPendingSnapshotSurvivesRefresh() = runBlocking {
        val account = account("profile-1")
        val session = serverSession("server-active")
        store.retainServerSession(account, "book-1", session)
        val authoritative = serverHighlight("client-1", "server")
        store.replaceAuthoritativeAnnotations(account, session.sessionId, listOf(authoritative))

        val legacySnapshot = database.localReaderDao().pendingAnnotations(
            account.value,
            session.sessionId
        )
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            highlightMutation("client-1", "committed after snapshot")
        )
        store.replaceAuthoritativeAnnotations(account, session.sessionId, listOf(authoritative))

        assertTrue(legacySnapshot.isEmpty())
        assertEquals(
            "committed after snapshot",
            (
                store.readAnnotations(account, session.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
    }

    @Test
    fun localDeleteCommittedAfterLegacyPendingSnapshotSurvivesRefresh() = runBlocking {
        val account = account("profile-1")
        val session = serverSession("server-active")
        store.retainServerSession(account, "book-1", session)
        val authoritative = ReaderAnnotation.Bookmark(
            "server-bookmark",
            "client-1",
            CFI,
            "Chapter 1",
            "now"
        )
        store.replaceAuthoritativeAnnotations(account, session.sessionId, listOf(authoritative))

        val legacySnapshot = database.localReaderDao().pendingAnnotations(
            account.value,
            session.sessionId
        )
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.Delete(
                session.sessionId,
                authoritative.clientId,
                authoritative
            )
        )
        store.replaceAuthoritativeAnnotations(account, session.sessionId, listOf(authoritative))

        assertTrue(legacySnapshot.isEmpty())
        assertTrue(store.readAnnotations(account, session.sessionId).isEmpty())
        assertEquals(
            LocalAnnotationSync.LOCAL_DELETED,
            database.localReaderDao().annotation(
                account.value,
                session.sessionId,
                authoritative.clientId
            )?.syncState
        )
    }

    @Test
    fun staleAcknowledgementCannotDiscardNewerLocalDesiredState() = runBlocking {
        val account = account("profile-1")
        val session = serverSession("server-active")
        store.retainServerSession(account, "book-1", session)
        val sent = highlightMutation("client-1", "sent")
        store.applyAnnotationMutation(account, session.sessionId, sent)
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            highlightMutation("client-1", "newer")
        )

        store.replaceAuthoritativeAnnotations(
            account,
            session.sessionId,
            listOf(serverHighlight("client-1", "sent")),
            acknowledgedMutation = sent
        )

        assertEquals(
            "newer",
            (
                store.readAnnotations(account, session.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
        val pending = RoomReaderOutboxStore(database.localReaderDao())
            .pendingReaderIntents(account, session.sessionId)
            .filterIsInstance<ReaderOutboxIntent.Annotation>()
            .single()
        assertEquals(
            "newer",
            (pending.mutation as ReaderAnnotationMutationRequest.UpsertHighlight).note
        )
    }

    @Test
    fun refreshPreservedCreateRemainsEligibleForClosedSessionContinuation() = runBlocking {
        val account = account("profile-1")
        val session = serverSession("server-active")
        store.retainServerSession(account, "book-1", session)
        database.localReaderDao().pendingAnnotations(account.value, session.sessionId)
        store.applyAnnotationMutation(
            account,
            session.sessionId,
            highlightMutation("new-client", "offline")
        )
        store.replaceAuthoritativeAnnotations(account, session.sessionId, emptyList())

        val continuation = RoomReaderClosedSessionContinuationStore(database.localReaderDao())
            .continueFrom(
                account,
                "book-1",
                serverSession("server-active", ReaderSessionStatus.CLOSED),
                emptyList()
            )
        val next = requireNotNull(continuation.session)

        assertEquals(
            "offline",
            (
                store.readAnnotations(account, next.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
        assertTrue(
            RoomReaderOutboxStore(database.localReaderDao())
                .pendingReaderIntents(account, next.sessionId)
                .any { it is ReaderOutboxIntent.Annotation }
        )
    }

    @Test
    fun authoritativeMergeRemainsAccountAndSessionScoped() = runBlocking {
        val firstAccount = account("profile-1")
        val secondAccount = account("profile-2")
        val first = store.selectOfflineSession(firstAccount, "book-1")
        val otherSession = store.selectOfflineSession(firstAccount, "book-2")
        val otherAccount = store.selectOfflineSession(secondAccount, "book-1")
        store.applyAnnotationMutation(
            firstAccount,
            otherSession.sessionId,
            highlightMutation("other-session", "other session")
        )
        store.applyAnnotationMutation(
            secondAccount,
            otherAccount.sessionId,
            highlightMutation("other-account", "other account")
        )

        store.replaceAuthoritativeAnnotations(firstAccount, first.sessionId, emptyList())

        assertEquals(
            "other session",
            (
                store.readAnnotations(firstAccount, otherSession.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
        assertEquals(
            "other account",
            (
                store.readAnnotations(secondAccount, otherAccount.sessionId).single() as
                    ReaderAnnotation.Highlight
                ).note
        )
    }

    @Test
    fun accountCleanupLeavesOtherAccountReaderState() = runBlocking {
        val firstAccount = account("profile-1")
        val secondAccount = account("profile-2")
        val first = store.selectOfflineSession(firstAccount, "book-1")
        val second = store.selectOfflineSession(secondAccount, "book-1")
        store.writeProgress(
            firstAccount,
            first.sessionId,
            CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        store.applyAnnotationMutation(
            firstAccount,
            first.sessionId,
            highlightMutation("pending-client", "pending note")
        )
        database.localReaderDao().upsertContinuationOutcome(
            LocalReaderContinuationOutcomeEntity(
                accountKey = firstAccount.value,
                sourceLocalSessionId = first.sessionId,
                continuationLocalSessionId = null,
                forwardedEditCount = 1,
                droppedDeleteCount = 1,
                createdAtEpochMillis = 1,
                consumedAtEpochMillis = null
            )
        )

        store.purgeAccount(firstAccount)

        assertNull(database.localReaderDao().session(firstAccount.value, first.sessionId))
        assertNull(database.localReaderDao().progress(firstAccount.value, first.sessionId))
        assertTrue(store.readAnnotations(firstAccount, first.sessionId).isEmpty())
        assertFalse(
            database.localReaderDao().hasPendingWorkForSession(
                firstAccount.value,
                first.sessionId
            )
        )
        assertNull(
            database.localReaderDao().continuationOutcome(firstAccount.value, first.sessionId)
        )
        assertEquals(
            second.sessionId,
            database.localReaderDao().session(secondAccount.value, second.sessionId)?.localSessionId
        )
    }

    private fun account(profileId: String) =
        LocalReaderAccountKey.from("https://library.example", profileId)

    private fun serverSession(
        id: String,
        status: ReaderSessionStatus = ReaderSessionStatus.ACTIVE
    ) = ReaderSessionContext(id, status, CFI)

    private fun highlightMutation(clientId: String, note: String) =
        ReaderAnnotationMutationRequest.UpsertHighlight(
            "ignored",
            clientId,
            RANGE_CFI,
            "Chapter 1",
            "quote",
            "prefix",
            "suffix",
            ReaderAnnotationColor.YELLOW,
            note
        )

    private fun serverHighlight(clientId: String, note: String) = ReaderAnnotation.Highlight(
        "server-$clientId",
        clientId,
        RANGE_CFI,
        "Chapter 1",
        "now",
        "quote",
        "prefix",
        "suffix",
        note,
        ReaderAnnotationColor.YELLOW
    )

    private companion object {
        const val CFI = "epubcfi(/6/2!/4/2:3)"
        const val NEXT_CFI = "epubcfi(/6/4!/4/2:8)"
        const val RANGE_CFI = "epubcfi(/6/2!/4/2,:3,:9)"
    }
}
