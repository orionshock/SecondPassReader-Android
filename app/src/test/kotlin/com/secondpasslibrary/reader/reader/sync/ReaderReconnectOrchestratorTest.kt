package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationBatchWriter
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.persistence.ReaderBoundOutboxSession
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.ReaderPendingOutboxSession
import com.secondpasslibrary.reader.reader.progress.ReaderProgressSyncFailure
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriteOutcome
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriter
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliation
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationFailure
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationResult
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderReconnectOrchestratorTest {
    @Test
    fun `provisional Session reconciles binds then drains annotations before progress`() = runTest {
        val events = mutableListOf<String>()
        val store = Store(provisional(), includeEstablishment = true)
        val reconciliation = ReaderSessionReconciliation { _, _, _, local ->
            events += "reconcile"
            store.bind(local.sessionId, SERVER_SESSION_ID)
            ReaderSessionReconciliationResult.Resolved(store.pending.single().session)
        }
        val orchestrator = orchestrator(store, reconciliation, events)

        orchestrator.update(profile(), PROFILE_ID, AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(listOf("reconcile", "annotations", "progress"), events)
        assertTrue(store.intents.isEmpty())
    }

    @Test
    fun `bound writable Session is authority checked before drain`() = runTest {
        val events = mutableListOf<String>()
        val store = Store(bound())
        val reconciliation = ReaderSessionReconciliation { _, _, _, local ->
            events += "reconcile:${local.serverSessionId}"
            ReaderSessionReconciliationResult.Resolved(local)
        }

        orchestrator(store, reconciliation, events)
            .update(profile(), PROFILE_ID, AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(
            listOf("reconcile:$SERVER_SESSION_ID", "annotations", "progress"),
            events
        )
    }

    @Test
    fun `remotely closed intent is forwarded then drained from continuation Session`() = runTest {
        val events = mutableListOf<String>()
        val store = Store(bound())
        val reconciliation = ReaderSessionReconciliation { _, _, _, _ ->
            events += "reconcile"
            store.forwardTo("replacement-local", "replacement-server")
            ReaderSessionReconciliationResult.Resolved(
                bound("replacement-local", "replacement-server")
            )
        }

        orchestrator(store, reconciliation, events)
            .update(profile(), PROFILE_ID, AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(listOf("reconcile", "annotations", "progress"), events)
        assertTrue(store.intents.isEmpty())
    }

    @Test
    fun `reconciliation failure leaves work pending and does not drain`() = runTest {
        val events = mutableListOf<String>()
        val store = Store(provisional(), includeEstablishment = true)
        val reconciliation = ReaderSessionReconciliation { _, _, _, _ ->
            events += "reconcile"
            ReaderSessionReconciliationResult.Failed(
                ReaderSessionReconciliationFailure.UNAVAILABLE
            )
        }

        orchestrator(store, reconciliation, events)
            .update(profile(), PROFILE_ID, AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(listOf("reconcile"), events)
        assertTrue(store.intents.isNotEmpty())
    }

    @Test
    fun `delivery failure preserves unresolved work without reconnect retry loop`() = runTest {
        val store = Store(bound())
        val reconciliation = ReaderSessionReconciliation { _, _, _, local ->
            ReaderSessionReconciliationResult.Resolved(local)
        }
        val synchronizer = ReaderOutboxSynchronizer(
            store,
            ReaderAnnotationBatchWriter { _, _, _ -> emptyList() },
            ReaderProgressWriter { _, _, _, _ ->
                ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
            }
        )
        val orchestrator = ReaderReconnectController(
            ReaderReconnectOrchestrator(reconciliation, store, synchronizer),
            this
        ) {}

        orchestrator.update(profile(), PROFILE_ID, AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(1, store.intents.filterIsInstance<ReaderOutboxIntent.Progress>().size)
    }

    @Test
    fun `repeated Online and reconnect flapping never overlap a run`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = Store(bound())
        var active = 0
        var maximumActive = 0
        var calls = 0
        val reconciliation = ReaderSessionReconciliation { _, _, _, local ->
            calls += 1
            active += 1
            maximumActive = maxOf(maximumActive, active)
            try {
                gate.await()
                ReaderSessionReconciliationResult.Resolved(local)
            } finally {
                active -= 1
            }
        }
        val orchestrator = orchestrator(store, reconciliation)

        orchestrator.update(profile(), PROFILE_ID, AppAvailability.Online)
        orchestrator.update(profile(), PROFILE_ID, AppAvailability.Online)
        runCurrent()
        orchestrator.update(
            profile(),
            PROFILE_ID,
            AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
        )
        orchestrator.update(profile(), PROFILE_ID, AppAvailability.Online)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(1, maximumActive)
    }

    @Test
    fun `account switch cancels former account before it can drain`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = Store(bound())
        val reconciliation = ReaderSessionReconciliation { _, account, _, local ->
            if (account == account(PROFILE_ID)) gate.await()
            ReaderSessionReconciliationResult.Resolved(local)
        }
        val orchestrator = orchestrator(store, reconciliation)

        orchestrator.update(profile(), PROFILE_ID, AppAvailability.Online)
        runCurrent()
        orchestrator.update(profile(), OTHER_PROFILE_ID, AppAvailability.Online)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(account(OTHER_PROFILE_ID)), store.acceptedAccounts)
    }

    @Test
    fun `empty account outbox performs no server work`() = runTest {
        val store = Store(bound()).apply { intents.clear() }
        var reconciliations = 0
        val reconciliation = ReaderSessionReconciliation { _, _, _, local ->
            reconciliations += 1
            ReaderSessionReconciliationResult.Resolved(local)
        }

        orchestrator(store, reconciliation)
            .update(profile(), PROFILE_ID, AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(0, reconciliations)
    }

    @Test
    fun `new local work while already online drains only after explicit sync request`() = runTest {
        val events = mutableListOf<String>()
        val store = Store(bound()).apply { intents.clear() }
        val reconciliation = ReaderSessionReconciliation { _, _, _, local ->
            events += "reconcile"
            ReaderSessionReconciliationResult.Resolved(local)
        }
        val controller = orchestrator(store, reconciliation, events)
        controller.update(profile(), PROFILE_ID, AppAvailability.Online)
        advanceUntilIdle()
        assertTrue(events.isEmpty())

        store.intents += highlight("local-session")
        store.intents += progress("local-session")
        controller.requestSync()
        advanceUntilIdle()

        assertEquals(listOf("reconcile", "annotations", "progress"), events)
        assertTrue(store.intents.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.orchestrator(
        store: Store,
        reconciliation: ReaderSessionReconciliation,
        events: MutableList<String> = mutableListOf()
    ): ReaderReconnectController {
        val annotationWriter = ReaderAnnotationBatchWriter { _, _, _ ->
            events += "annotations"
            emptyList()
        }
        val progressWriter = ReaderProgressWriter { _, _, _, _ ->
            events += "progress"
            ReaderProgressWriteOutcome.Success
        }
        return ReaderReconnectController(
            ReaderReconnectOrchestrator(
                reconciliation,
                store,
                ReaderOutboxSynchronizer(store, annotationWriter, progressWriter)
            ),
            this
        ) {}
    }

    private class Store(session: ReaderSessionContext, includeEstablishment: Boolean = false) :
        ReaderOutboxStore {
        val pending = mutableListOf(ReaderPendingOutboxSession(BOOK_ID, session))
        val acceptedAccounts = mutableListOf<LocalReaderAccountKey>()
        val intents = mutableListOf<ReaderOutboxIntent>().apply {
            if (includeEstablishment) add(establishment(session.sessionId))
            add(highlight(session.sessionId))
            add(bookmark(session.sessionId))
            add(progress(session.sessionId))
        }

        fun bind(localSessionId: String, serverSessionId: String) {
            val current = pending.single { it.session.sessionId == localSessionId }
            pending[0] = current.copy(
                session = current.session.copy(
                    serverSessionId = serverSessionId,
                    identityKind = ReaderSessionIdentityKind.SERVER_CONFIRMED
                )
            )
            intents.removeAll { it is ReaderOutboxIntent.EstablishSession }
        }

        fun forwardTo(localSessionId: String, serverSessionId: String) {
            val current = pending.single()
            pending[0] = current.copy(session = bound(localSessionId, serverSessionId))
            val moved = intents.map { intent ->
                when (intent) {
                    is ReaderOutboxIntent.EstablishSession ->
                        establishment(localSessionId)

                    is ReaderOutboxIntent.Progress -> progress(localSessionId)

                    is ReaderOutboxIntent.Annotation -> when (intent.mutation) {
                        is ReaderAnnotationMutationRequest.UpsertBookmark -> bookmark(
                            localSessionId
                        )

                        is ReaderAnnotationMutationRequest.UpsertHighlight -> highlight(
                            localSessionId
                        )

                        is ReaderAnnotationMutationRequest.Delete -> ReaderOutboxIntent.Annotation(
                            "annotation:$localSessionId:${intent.mutation.clientId}",
                            intent.bookId,
                            localSessionId,
                            intent.mutation.copy(sessionId = localSessionId)
                        )
                    }
                }
            }
            intents.clear()
            intents += moved.filterNot { it is ReaderOutboxIntent.EstablishSession }
        }

        override suspend fun pendingSessions(account: LocalReaderAccountKey) = pending.toList()

        override suspend fun boundPendingSessions(account: LocalReaderAccountKey) =
            pending.mapNotNull { item ->
                val session = item.session
                session.serverSessionId?.takeIf { session.status == ReaderSessionStatus.ACTIVE }
                    ?.let { ReaderBoundOutboxSession(session.sessionId, it, item.bookId) }
            }.filter { bound ->
                intents.none {
                    it.localSessionId == bound.localSessionId &&
                        it is ReaderOutboxIntent.EstablishSession
                }
            }

        override suspend fun pendingReaderIntents(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = intents.filter { it.localSessionId == localSessionId }

        override suspend fun hasPendingWork(account: LocalReaderAccountKey) = intents.isNotEmpty()

        override suspend fun acceptProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            sent: ReaderOutboxIntent.Progress
        ) {
            intents.remove(sent)
        }

        override suspend fun acceptAnnotationBatch(
            account: LocalReaderAccountKey,
            localSessionId: String,
            sent: List<ReaderOutboxIntent.Annotation>,
            authoritative: List<ReaderAnnotation>
        ) {
            acceptedAccounts += account
            intents.removeAll(sent.toSet())
        }
    }

    private companion object {
        const val BOOK_ID = "book-1"
        const val PROFILE_ID = "profile-1"
        const val OTHER_PROFILE_ID = "profile-2"
        const val SERVER_SESSION_ID = "server-session"

        fun account(profileId: String) =
            LocalReaderAccountKey.from("https://library.example", profileId)

        fun profile(clientName: String = PROFILE_ID) = ConnectionProfile(
            serverOrigin = "https://library.example",
            serverBaseUrl = "https://library.example/",
            apiBaseUrl = "https://library.example/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "2026-08-30",
            clientSessionId = "client-session",
            clientName = clientName,
            clientType = "reader"
        )

        fun provisional() = ReaderSessionContext(
            sessionId = "local-session",
            status = ReaderSessionStatus.ACTIVE,
            savedProgressCfi = null,
            serverSessionId = null,
            identityKind = ReaderSessionIdentityKind.PROVISIONAL
        )

        fun bound(
            localSessionId: String = "local-session",
            serverSessionId: String = SERVER_SESSION_ID
        ) = ReaderSessionContext(
            sessionId = localSessionId,
            status = ReaderSessionStatus.ACTIVE,
            savedProgressCfi = null,
            serverSessionId = serverSessionId
        )

        fun establishment(localSessionId: String) = ReaderOutboxIntent.EstablishSession(
            "session:$localSessionId",
            BOOK_ID,
            localSessionId
        )

        fun highlight(localSessionId: String) = ReaderOutboxIntent.Annotation(
            "annotation:$localSessionId:highlight",
            BOOK_ID,
            localSessionId,
            ReaderAnnotationMutationRequest.UpsertHighlight(
                localSessionId,
                "highlight",
                "epubcfi(/6/2!/4/2:1,/1:0,/1:4)",
                "Chapter 1",
                "text",
                "before",
                "after",
                com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor.YELLOW,
                "note"
            )
        )

        fun progress(localSessionId: String) = ReaderOutboxIntent.Progress(
            "progress:$localSessionId",
            BOOK_ID,
            localSessionId,
            "epubcfi(/6/2!/4/2:3)"
        )

        fun bookmark(localSessionId: String) = ReaderOutboxIntent.Annotation(
            "annotation:$localSessionId:bookmark",
            BOOK_ID,
            localSessionId,
            ReaderAnnotationMutationRequest.UpsertBookmark(
                localSessionId,
                "bookmark",
                "epubcfi(/6/2!/4/2:3)",
                "Chapter 1"
            )
        )
    }
}
