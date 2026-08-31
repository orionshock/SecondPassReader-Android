package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationBatchWriter
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.persistence.ReaderBoundOutboxSession
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.progress.ReaderProgressSyncFailure
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriteOutcome
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderOutboxSynchronizerTest {
    @Test
    fun `annotation operations batch before exact progress`() = runTest {
        val store = MemoryOutboxStore(
            mutableListOf(highlight("h"), bookmark("b"), delete("d"), progress("cfi-1"))
        )
        val requests = mutableListOf<List<ReaderAnnotationMutationRequest>>()
        val order = mutableListOf<String>()
        val synchronizer = synchronizer(
            store,
            annotationWriter = { _, serverSessionId, batch ->
                order += "annotations"
                assertEquals(SERVER_SESSION_ID, serverSessionId)
                requests += batch
                authoritative()
            },
            progressWriter = ReaderProgressWriter { _, serverSessionId, cfi ->
                order += "progress"
                assertEquals(SERVER_SESSION_ID, serverSessionId)
                assertEquals("cfi-1", cfi.value)
                ReaderProgressWriteOutcome.Success
            }
        )

        val report = synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID)

        assertEquals(listOf("annotations", "progress"), order)
        assertEquals(3, report.deliveredAnnotationIntents)
        assertEquals(1, report.deliveredProgressIntents)
        assertTrue(store.intents.isEmpty())
        assertTrue(requests.single()[0] is ReaderAnnotationMutationRequest.UpsertHighlight)
        assertTrue(requests.single()[1] is ReaderAnnotationMutationRequest.UpsertBookmark)
        assertTrue(requests.single()[2] is ReaderAnnotationMutationRequest.Delete)
    }

    @Test
    fun `more than one hundred annotation intents use deterministic chunks`() = runTest {
        val store = MemoryOutboxStore(
            MutableList(205) { index -> bookmark(index.toString()) }
        )
        val sizes = mutableListOf<Int>()
        val synchronizer = synchronizer(store, annotationWriter = { _, _, requests ->
            sizes += requests.size
            authoritative()
        })

        val report = synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID)

        assertEquals(listOf(100, 100, 5), sizes)
        assertEquals(205, report.deliveredAnnotationIntents)
    }

    @Test
    fun `newer annotation and progress survive older in flight responses`() = runTest {
        val store = MemoryOutboxStore(mutableListOf(highlight("h"), progress("cfi-1")))
        val annotationGate = CompletableDeferred<Unit>()
        val progressGate = CompletableDeferred<Unit>()
        val synchronizer = synchronizer(
            store,
            annotationWriter = { _, _, _ ->
                annotationGate.await()
                authoritative()
            },
            progressWriter = ReaderProgressWriter { _, _, _ ->
                progressGate.await()
                ReaderProgressWriteOutcome.Success
            }
        )

        val sync = async { synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID) }
        runCurrent()
        store.replace(highlight("h", note = "newer"))
        annotationGate.complete(Unit)
        runCurrent()
        store.replace(progress("cfi-2"))
        progressGate.complete(Unit)
        sync.await()

        assertEquals(
            "newer",
            store.intents.filterIsInstance<ReaderOutboxIntent.AnnotationUpsert>()
                .single().note
        )
        assertEquals(
            "cfi-2",
            store.intents.filterIsInstance<ReaderOutboxIntent.Progress>()
                .single().cfi
        )
    }

    @Test
    fun `unbound Session and establishment dependency do not deliver`() = runTest {
        val unbound = MemoryOutboxStore(mutableListOf(highlight("h"))).apply {
            sessions.clear()
        }
        val establishment = MemoryOutboxStore(
            mutableListOf(
                ReaderOutboxIntent.EstablishSession("session:local", BOOK_ID, LOCAL_SESSION_ID),
                highlight("h")
            )
        )
        var writes = 0
        val writer = ReaderAnnotationBatchWriter { _, _, _ ->
            writes += 1
            authoritative()
        }

        synchronizer(unbound, writer).syncBoundSession(profile(), account(), LOCAL_SESSION_ID)
        synchronizer(establishment, writer).syncBoundSession(
            profile(),
            account(),
            LOCAL_SESSION_ID
        )

        assertEquals(0, writes)
    }

    @Test
    fun `closed Session requests reconciliation and preserves remaining work`() = runTest {
        val store = MemoryOutboxStore(mutableListOf(highlight("h"), progress("cfi-1")))
        val synchronizer = synchronizer(store, annotationWriter = { _, _, _ ->
            throw SplClientException.ReadingSessionLifecycleRejected(
                ReadingSessionLifecycleRejection.SESSION_CLOSED
            )
        })

        val report = synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID)

        assertEquals(setOf(LOCAL_SESSION_ID), report.reconciliationSessionIds)
        assertEquals(2, store.intents.size)
    }

    @Test
    fun `progress failure and authentication preserve pending intents`() = runTest {
        val unavailable = MemoryOutboxStore(mutableListOf(progress("cfi-1")))
        val unavailableReport = synchronizer(
            unavailable,
            progressWriter = ReaderProgressWriter { _, _, _ ->
                ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
            }
        ).syncBoundSession(profile(), account(), LOCAL_SESSION_ID)
        val auth = MemoryOutboxStore(mutableListOf(highlight("h")))
        val authReport = synchronizer(auth, annotationWriter = { _, _, _ ->
            throw SplClientException.AuthenticationRejected()
        }).syncBoundSession(profile(), account(), LOCAL_SESSION_ID)

        assertTrue(unavailableReport.unavailable)
        assertTrue(authReport.authenticationRequired)
        assertFalse(unavailable.intents.isEmpty())
        assertFalse(auth.intents.isEmpty())
    }

    @Test
    fun `lost annotation response retries the same client identity`() = runTest {
        val store = MemoryOutboxStore(mutableListOf(highlight("h")))
        val clientIds = mutableListOf<String>()
        var attempts = 0
        val synchronizer = synchronizer(store, annotationWriter = { _, _, requests ->
            attempts += 1
            clientIds += requests.single().clientId()
            if (attempts == 1) error("response lost")
            authoritative()
        })

        synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID)
        synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID)

        assertEquals(listOf("h", "h"), clientIds)
        assertTrue(store.intents.isEmpty())
    }

    @Test
    fun `concurrent drain triggers serialize through one actor`() = runTest {
        val store = MemoryOutboxStore(mutableListOf(highlight("h")))
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maximumActive = 0
        val synchronizer = synchronizer(store, annotationWriter = { _, _, _ ->
            active += 1
            maximumActive = maxOf(maximumActive, active)
            gate.await()
            active -= 1
            authoritative()
        })

        val first = async { synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID) }
        runCurrent()
        val second = async { synchronizer.syncBoundSession(profile(), account(), LOCAL_SESSION_ID) }
        runCurrent()
        assertEquals(1, maximumActive)
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, maximumActive)
    }

    private fun synchronizer(
        store: MemoryOutboxStore,
        annotationWriter: ReaderAnnotationBatchWriter = ReaderAnnotationBatchWriter { _, _, _ ->
            authoritative()
        },
        progressWriter: ReaderProgressWriter = ReaderProgressWriter { _, _, _ ->
            ReaderProgressWriteOutcome.Success
        }
    ) = ReaderOutboxSynchronizer(store, annotationWriter, progressWriter)

    private class MemoryOutboxStore(initial: MutableList<ReaderOutboxIntent>) : ReaderOutboxStore {
        val sessions = mutableListOf(
            ReaderBoundOutboxSession(LOCAL_SESSION_ID, SERVER_SESSION_ID, BOOK_ID)
        )
        val intents = initial

        fun replace(next: ReaderOutboxIntent) {
            intents.removeAll { it.id == next.id }
            intents += next
        }

        override suspend fun pendingSessions(account: LocalReaderAccountKey) = emptyList<
            com.secondpasslibrary.reader.reader.persistence.ReaderPendingOutboxSession
            >()

        override suspend fun boundPendingSessions(account: LocalReaderAccountKey) = sessions

        override suspend fun pendingReaderIntents(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = intents.toList()

        override suspend fun hasPendingWork(account: LocalReaderAccountKey) = intents.isNotEmpty()

        override suspend fun acceptProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            sent: ReaderOutboxIntent.Progress
        ) {
            intents.removeAll { it == sent }
        }

        override suspend fun acceptAnnotationBatch(
            account: LocalReaderAccountKey,
            localSessionId: String,
            sent: List<ReaderOutboxIntent>,
            authoritative: List<ReaderAnnotation>
        ) {
            intents.removeAll { it in sent }
        }
    }

    private fun highlight(id: String, note: String = "note") = ReaderOutboxIntent.AnnotationUpsert(
        "annotation:$LOCAL_SESSION_ID:$id",
        BOOK_ID,
        LOCAL_SESSION_ID,
        id,
        "HIGHLIGHT",
        CFI,
        "Chapter",
        "quote",
        "before",
        "after",
        note,
        ReaderAnnotationColor.YELLOW
    )

    private fun bookmark(id: String) = ReaderOutboxIntent.AnnotationUpsert(
        "annotation:$LOCAL_SESSION_ID:$id",
        BOOK_ID,
        LOCAL_SESSION_ID,
        id,
        "BOOKMARK",
        CFI,
        "Chapter",
        null,
        null,
        null,
        null,
        null
    )

    private fun delete(id: String) = ReaderOutboxIntent.AnnotationDelete(
        "annotation:$LOCAL_SESSION_ID:$id",
        BOOK_ID,
        LOCAL_SESSION_ID,
        id
    )

    private fun progress(cfi: String) = ReaderOutboxIntent.Progress(
        "progress:$LOCAL_SESSION_ID",
        BOOK_ID,
        LOCAL_SESSION_ID,
        cfi
    )

    private fun authoritative() = listOf(
        ReaderAnnotation.Highlight(
            "server-h",
            "h",
            CFI,
            "Chapter",
            "2026-08-30T00:00:00Z",
            "quote",
            "before",
            "after",
            "note",
            ReaderAnnotationColor.YELLOW
        )
    )

    private fun ReaderAnnotationMutationRequest.clientId() = when (this) {
        is ReaderAnnotationMutationRequest.Delete -> clientId
        is ReaderAnnotationMutationRequest.UpsertBookmark -> clientId
        is ReaderAnnotationMutationRequest.UpsertHighlight -> clientId
    }

    private fun account() = LocalReaderAccountKey.from("https://library.example", "profile-1")

    private fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-30",
        clientSessionId = "client-session",
        clientName = "Reader",
        clientType = "reader"
    )

    private companion object {
        const val LOCAL_SESSION_ID = "local-session"
        const val SERVER_SESSION_ID = "server-session"
        const val BOOK_ID = "book-1"
        const val CFI = "epubcfi(/6/2!/4/2:3)"
    }
}
