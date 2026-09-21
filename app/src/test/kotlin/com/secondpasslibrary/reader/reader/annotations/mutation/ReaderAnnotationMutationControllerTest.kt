package com.secondpasslibrary.reader.reader.annotations.mutation

import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.MarginaliaAnnotationDraft
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.reader.FakeAuthenticatedMarginaliaClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition
import com.secondpasslibrary.reader.reader.location.ReaderSavedLocationLabelPolicy
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAnnotationMutationControllerTest {
    @Test
    fun `create and edit normalize context only when preparing local desired state`() = runTest {
        val store = RecordingStore()
        val controller = controller(this, store)
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(
            ReaderAnnotationMutationIntent.BeginCreate(
                selection().copy(
                    selectedText = "  One\n\n Apocalypses\t always   kick off...  ",
                    prefix = "  Before\u00A0 context  ",
                    suffix = "  After\r\n context  "
                )
            )
        )
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateCreate(note = "  first line\n\tsecond  ")
        )
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()

        val existing = highlight("existing").copy(
            quote = "  Stored\n\n exactly\t as returned  ",
            prefix = "\u00A0 Old\t prefix ",
            suffix = " Old\r\n suffix "
        )
        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(existing))
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateEdit(
                ReaderAnnotationColor.BLUE,
                "  edited\n\tnote  "
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SaveEdit)
        advanceUntilIdle()

        val created = store.requests[0] as ReaderAnnotationMutationRequest.UpsertHighlight
        val edited = store.requests[1] as ReaderAnnotationMutationRequest.UpsertHighlight
        assertEquals("One Apocalypses always kick off...", created.text)
        assertEquals("Before context", created.prefix)
        assertEquals("After context", created.suffix)
        assertEquals("  first line\n\tsecond  ", created.note)
        assertEquals("Stored exactly as returned", edited.text)
        assertEquals("Old prefix", edited.prefix)
        assertEquals("Old suffix", edited.suffix)
        assertEquals("  edited\n\tnote  ", edited.note)
        assertEquals(existing.clientId, edited.clientId)
        assertEquals(existing.cfi, edited.cfi)
    }

    @Test
    fun `normalized blank quote does not commit or request sync`() = runTest {
        val store = RecordingStore()
        var syncRequests = 0
        val controller = controller(this, store) { syncRequests += 1 }
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(
            ReaderAnnotationMutationIntent.BeginCreate(
                selection().copy(selectedText = " \t\n\u00A0\uFEFF")
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()

        assertTrue(store.requests.isEmpty())
        assertEquals(0, syncRequests)
        assertFalse(controller.state.value.submitting)
    }

    @Test
    fun `each quick color commits locally with empty note and requests sync`() = runTest {
        ReaderAnnotationColor.entries.forEach { color ->
            val store = RecordingStore()
            var syncRequests = 0
            val controller = controller(this, store) { syncRequests += 1 }
            controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
            controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
            val clientId = controller.state.value.pendingCreate?.clientId
            controller.accept(ReaderAnnotationMutationIntent.UpdateCreate(note = "discarded"))
            controller.accept(ReaderAnnotationMutationIntent.SubmitQuickCreate(color))
            advanceUntilIdle()

            val committed = store.requests.single()
                as ReaderAnnotationMutationRequest.UpsertHighlight
            assertEquals(color, committed.color)
            assertEquals("", committed.note)
            assertEquals(clientId, committed.clientId)
            assertEquals(1, syncRequests)
        }
    }

    @Test
    fun `local persistence failure retains create identity and draft for retry`() = runTest {
        val store = RecordingStore(failNext = true)
        val controller = controller(this, store)
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
        val clientId = controller.state.value.pendingCreate?.clientId
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateCreate(
                ReaderAnnotationColor.PINK,
                "Exact note  \n"
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()
        assertEquals(clientId, controller.state.value.pendingCreate?.clientId)
        assertEquals("Exact note  \n", controller.state.value.pendingCreate?.note)

        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()
        val retried = store.requests.single()
            as ReaderAnnotationMutationRequest.UpsertHighlight
        assertEquals(clientId, retried.clientId)
        assertTrue(runCatching { UUID.fromString(retried.clientId) }.isSuccess)
        assertNull(controller.state.value.pendingCreate)
    }

    @Test
    fun `edit and delete commit exact identity while closed Session refuses writes`() = runTest {
        val store = RecordingStore()
        val controller = controller(this, store)
        val original = highlight("server-highlight")
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(original))
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateEdit(ReaderAnnotationColor.ORANGE, "Revised")
        )
        controller.accept(ReaderAnnotationMutationIntent.SaveEdit)
        advanceUntilIdle()

        val edit = store.requests.single()
            as ReaderAnnotationMutationRequest.UpsertHighlight
        assertEquals(original.clientId, edit.clientId)
        assertNotEquals(original.id, edit.clientId)
        assertEquals(original.cfi, edit.cfi)
        assertEquals(original.locationLabel, edit.locationLabel)
        assertEquals("Revised", edit.note)

        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(original))
        controller.accept(ReaderAnnotationMutationIntent.ConfirmDelete)
        advanceUntilIdle()
        val delete = store.requests.last() as ReaderAnnotationMutationRequest.Delete
        assertEquals(original.clientId, delete.clientId)

        controller.select(profile(), "closed", ReaderSessionStatus.CLOSED)
        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(original))
        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(original))
        controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
        runCurrent()
        assertEquals(2, store.requests.size)
        assertNull(controller.state.value.editing)
        assertNull(controller.state.value.deleting)
        assertNull(controller.state.value.pendingCreate)
    }

    @Test
    fun `bookmark create and delete are local-first with stable client identity`() = runTest {
        val store = RecordingStore()
        val controller = controller(this, store)
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.CreateBookmark(position()))
        advanceUntilIdle()

        val create = store.requests.single()
            as ReaderAnnotationMutationRequest.UpsertBookmark
        assertTrue(runCatching { UUID.fromString(create.clientId) }.isSuccess)
        assertEquals(CFI, create.cfi)
        assertEquals("042% - Chapter Three", create.locationLabel)

        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(bookmark(create.clientId)))
        controller.accept(ReaderAnnotationMutationIntent.ConfirmDelete)
        advanceUntilIdle()
        assertEquals(
            create.clientId,
            (store.requests.last() as ReaderAnnotationMutationRequest.Delete).clientId
        )
    }

    @Test
    fun `note editor cancel keeps create identity and draft`() = runTest {
        val controller = controller(this, RecordingStore())
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
        val clientId = controller.state.value.pendingCreate?.clientId
        controller.accept(ReaderAnnotationMutationIntent.OpenCreateNote)
        controller.accept(ReaderAnnotationMutationIntent.UpdateCreate(note = "Draft note"))
        controller.accept(ReaderAnnotationMutationIntent.CancelCreateNote)

        assertFalse(controller.state.value.createNoteEditorVisible)
        assertEquals(clientId, controller.state.value.pendingCreate?.clientId)
        assertEquals("Draft note", controller.state.value.pendingCreate?.note)
    }

    @Test
    fun `SPL batch writer remains the outbox transport adapter`() = runTest {
        val batches = mutableListOf<List<MarginaliaAnnotationOperation>>()
        val sessions = object : AuthenticatedReadingSessionsClient by
        FakeAuthenticatedMarginaliaClient.sessions {
            override suspend fun synchronizeAnnotations(
                sessionId: String,
                operations: List<MarginaliaAnnotationOperation>
            ): List<com.secondpasslibrary.client.MarginaliaAnnotation> {
                assertEquals(SESSION_ID, sessionId)
                batches += operations
                return emptyList()
            }
        }
        val writer = SplReaderAnnotationWriter(clientProvider(sessions))
        writer.synchronize(
            profile(),
            SESSION_ID,
            listOf(
                upsertRequest(),
                ReaderAnnotationMutationRequest.UpsertBookmark(
                    SESSION_ID,
                    "client-bookmark",
                    CFI,
                    savedLabel()
                ),
                ReaderAnnotationMutationRequest.Delete(SESSION_ID, "client-delete")
            )
        )

        val draft = (batches.single()[0] as MarginaliaAnnotationOperation.Upsert)
            .annotation as MarginaliaAnnotationDraft.Highlight
        assertEquals("client-existing", draft.clientId)
        assertEquals(MarginaliaHighlightColor.PURPLE, draft.body.color)
        assertEquals("  Exact note\n", draft.body.note)
        assertTrue(
            (batches.single()[1] as MarginaliaAnnotationOperation.Upsert)
                .annotation is MarginaliaAnnotationDraft.Bookmark
        )
        assertEquals(
            "client-delete",
            (batches.single()[2] as MarginaliaAnnotationOperation.Delete).clientId
        )
    }

    @Test
    fun `web palette remains the only supported color set`() {
        assertEquals(
            listOf(0xFFFACC15, 0xFF22C55E, 0xFF3B82F6, 0xFFEC4899, 0xFFA855F7, 0xFFF97316),
            ReaderAnnotationColor.entries.map { it.displayArgb }
        )
    }

    private fun controller(
        scope: CoroutineScope,
        store: RecordingStore,
        onSyncRequested: () -> Unit = {}
    ) = ReaderAnnotationMutationController(scope, store, { _, _ -> }, onSyncRequested)

    private class RecordingStore(var failNext: Boolean = false) : LocalReaderStateStore {
        val requests = mutableListOf<ReaderAnnotationMutationRequest>()

        override suspend fun selectOfflineSession(account: LocalReaderAccountKey, bookId: String) =
            error("unused")

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
        ): List<ReaderAnnotation> {
            if (failNext) {
                failNext = false
                error("disk unavailable")
            }
            requests += request
            return when (request) {
                is ReaderAnnotationMutationRequest.UpsertHighlight -> listOf(
                    ReaderAnnotation.Highlight(
                        "local:${request.clientId}", request.clientId, request.cfi,
                        request.locationLabel, "now", request.text, request.prefix,
                        request.suffix, request.note, request.color
                    )
                )

                is ReaderAnnotationMutationRequest.UpsertBookmark -> listOf(
                    ReaderAnnotation.Bookmark(
                        "local:${request.clientId}",
                        request.clientId,
                        request.cfi,
                        request.locationLabel,
                        "now"
                    )
                )

                is ReaderAnnotationMutationRequest.Delete -> emptyList()
            }
        }

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            acknowledgedMutation: ReaderAnnotationMutationRequest?
        ) = Unit

        override suspend fun purgeAccount(account: LocalReaderAccountKey) = Unit
    }

    private fun selection() = ReaderSelection(
        EpubCfi(CFI),
        "Selected text",
        "Before",
        "After",
        "Chapter 03 · 42%"
    )

    private fun highlight(id: String) = ReaderAnnotation.Highlight(
        id, "client-$id", CFI, "Chapter 03 · 42%", "2026-08-25T00:00:00Z",
        "Original quote", "Before", "After", "Original note", ReaderAnnotationColor.YELLOW
    )

    private fun bookmark(clientId: String) = ReaderAnnotation.Bookmark(
        "server-bookmark",
        clientId,
        CFI,
        savedLabel(),
        "2026-08-25T00:00:00Z"
    )

    private fun position() = EpubCfiPosition(EpubCfi(CFI), 3, 0.42, "Chapter Three")

    private fun savedLabel() = ReaderSavedLocationLabelPolicy.create(0.42, null, 3)

    private fun upsertRequest() = ReaderAnnotationMutationRequest.UpsertHighlight(
        SESSION_ID, "client-existing", CFI, "Chapter 03 · 42%", "Original quote",
        "Before", "After", ReaderAnnotationColor.PURPLE, "  Exact note\n"
    )

    private fun clientProvider(sessions: AuthenticatedReadingSessionsClient) =
        object : AuthenticatedClientProvider {
            override suspend fun forProfile(profile: ConnectionProfile) =
                object : AuthenticatedSecondPassClient {
                    override val library: AuthenticatedLibraryClient get() = error("unused")
                    override val shelves: AuthenticatedShelvesClient get() = error("unused")
                    override val marginalia =
                        object : com.secondpasslibrary.client.AuthenticatedMarginaliaClient {
                            override val books = FakeAuthenticatedMarginaliaClient.books
                            override val sessions = sessions
                        }
                }
        }

    private fun profile() = ConnectionProfile(
        "a6722b5a-7982-4778-8c74-39be4241a654", "https://library.example",
        "https://library.example", "Library", "", "1", "2026-08-25",
        "client-session", "Reader", "reader"
    )
}

private const val SESSION_ID = "session"
private const val CFI = "epubcfi(/6/2!/4/2,/1:0,/1:4)"
