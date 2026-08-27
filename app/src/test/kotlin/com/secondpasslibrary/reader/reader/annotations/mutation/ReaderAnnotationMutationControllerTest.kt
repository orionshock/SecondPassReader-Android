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
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.annotations.selection.readerLocationLabel
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition
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
    fun `create and edit normalize quote context only when preparing a write`() = runTest {
        val requests = mutableListOf<ReaderAnnotationMutationRequest.UpsertHighlight>()
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, request ->
                requests += request as ReaderAnnotationMutationRequest.UpsertHighlight
                emptyList()
            }
        )
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
            ReaderAnnotationMutationIntent.UpdateCreate(note = "  first line\n\tsecond line  ")
        )
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()

        val existing = highlight("existing").copy(
            quote = "  Stored\n\n exactly\t as returned  ",
            prefix = "\u00A0 Old\t prefix ",
            suffix = " Old\r\n suffix ",
            note = "old"
        )
        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(existing))
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateEdit(
                color = ReaderAnnotationColor.BLUE,
                note = "  edited\n\tnote  "
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SaveEdit)
        advanceUntilIdle()

        assertEquals("One Apocalypses always kick off...", requests[0].text)
        assertEquals("Before context", requests[0].prefix)
        assertEquals("After context", requests[0].suffix)
        assertEquals("  first line\n\tsecond line  ", requests[0].note)
        assertEquals(CFI, requests[0].cfi)
        assertEquals("Stored exactly as returned", requests[1].text)
        assertEquals("Old prefix", requests[1].prefix)
        assertEquals("Old suffix", requests[1].suffix)
        assertEquals("  edited\n\tnote  ", requests[1].note)
        assertEquals(existing.clientId, requests[1].clientId)
        assertEquals(existing.cfi, requests[1].cfi)
        assertEquals(existing.locationLabel, requests[1].locationLabel)
    }

    @Test
    fun `normalized blank quote does not submit a highlight`() = runTest {
        var calls = 0
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, _ ->
                calls += 1
                emptyList()
            }
        )
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(
            ReaderAnnotationMutationIntent.BeginCreate(
                selection().copy(selectedText = " \t\n\u00A0\uFEFF")
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()

        assertEquals(0, calls)
        assertFalse(controller.state.value.submitting)
    }

    @Test
    fun `note editor keeps create identity and cancel returns to empty quick highlight`() =
        runTest {
            val controller = controller(this, ReaderAnnotationWriter { _, _ -> emptyList() })
            controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
            controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
            val clientId = controller.state.value.pendingCreate?.clientId

            controller.accept(ReaderAnnotationMutationIntent.OpenCreateNote)
            controller.accept(ReaderAnnotationMutationIntent.UpdateCreate(note = "Draft note"))

            assertTrue(controller.state.value.createNoteEditorVisible)
            assertEquals(clientId, controller.state.value.pendingCreate?.clientId)
            assertEquals("Draft note", controller.state.value.pendingCreate?.note)

            controller.accept(ReaderAnnotationMutationIntent.CancelCreateNote)

            assertFalse(controller.state.value.createNoteEditorVisible)
            assertEquals(clientId, controller.state.value.pendingCreate?.clientId)
            assertEquals("", controller.state.value.pendingCreate?.note)
        }

    @Test
    fun `create keeps one identity and sends latest exact draft`() = runTest {
        val requests = mutableListOf<ReaderAnnotationMutationRequest>()
        var fail = true
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, request ->
                requests += request
                if (fail) error("offline")
                listOf(highlight("server"))
            }
        )
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
        val clientId = controller.state.value.pendingCreate?.clientId
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateCreate(
                color = ReaderAnnotationColor.PINK,
                note = "Exact note  \n"
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()

        assertEquals(clientId, controller.state.value.pendingCreate?.clientId)
        assertEquals("Exact note  \n", controller.state.value.pendingCreate?.note)

        fail = false
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateCreate(
                color = ReaderAnnotationColor.BLUE,
                note = "Latest note"
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        advanceUntilIdle()

        val retried = requests.last() as ReaderAnnotationMutationRequest.UpsertHighlight
        assertEquals(clientId, retried.clientId)
        assertEquals(CFI, retried.cfi)
        assertEquals("Selected text", retried.text)
        assertEquals("Before", retried.prefix)
        assertEquals("After", retried.suffix)
        assertEquals(ReaderAnnotationColor.BLUE, retried.color)
        assertEquals("Latest note", retried.note)
        assertTrue(runCatching { UUID.fromString(retried.clientId) }.isSuccess)

        controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
        assertNotEquals(clientId, controller.state.value.pendingCreate?.clientId)
    }

    @Test
    fun `edit preserves immutable representation and existing client identity`() = runTest {
        val requests = mutableListOf<ReaderAnnotationMutationRequest>()
        var reconciled = emptyList<ReaderAnnotation>()
        val original = highlight("server-highlight")
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, request ->
                requests += request
                listOf(original.copy(color = ReaderAnnotationColor.ORANGE, note = "Revised"))
            }
        ) { _, annotations -> reconciled = annotations }
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(original))

        val initial = controller.state.value.editing
        assertEquals(original.color, initial?.color)
        assertEquals(original.note, initial?.note)

        controller.accept(
            ReaderAnnotationMutationIntent.UpdateEdit(
                color = ReaderAnnotationColor.ORANGE,
                note = "Revised"
            )
        )
        controller.accept(ReaderAnnotationMutationIntent.SaveEdit)
        advanceUntilIdle()

        val request = requests.single() as ReaderAnnotationMutationRequest.UpsertHighlight
        assertEquals(original.clientId, request.clientId)
        assertNotEquals(original.id, request.clientId)
        assertEquals(original.cfi, request.cfi)
        assertEquals(original.locationLabel, request.locationLabel)
        assertEquals(original.quote, request.text)
        assertEquals(original.prefix, request.prefix)
        assertEquals(original.suffix, request.suffix)
        assertEquals(ReaderAnnotationColor.ORANGE, request.color)
        assertEquals("Revised", request.note)
        assertEquals(
            ReaderAnnotationColor.ORANGE,
            (reconciled.single() as ReaderAnnotation.Highlight).color
        )
    }

    @Test
    fun `unchanged edit closes without mutation and failed edit retains latest draft`() = runTest {
        var calls = 0
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, _ ->
                calls += 1
                error("offline")
            }
        )
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(highlight("unchanged")))
        controller.accept(ReaderAnnotationMutationIntent.SaveEdit)
        runCurrent()
        assertEquals(0, calls)
        assertNull(controller.state.value.editing)

        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(highlight("failed")))
        controller.accept(
            ReaderAnnotationMutationIntent.UpdateEdit(ReaderAnnotationColor.GREEN, "Draft note")
        )
        controller.accept(ReaderAnnotationMutationIntent.SaveEdit)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("client-failed", controller.state.value.editing?.annotation?.clientId)
        assertEquals(ReaderAnnotationColor.GREEN, controller.state.value.editing?.color)
        assertEquals("Draft note", controller.state.value.editing?.note)
        assertFalse(controller.state.value.submitting)
    }

    @Test
    fun `delete uses client identity and closed Session refuses all writes`() = runTest {
        val requests = mutableListOf<ReaderAnnotationMutationRequest>()
        var reconciled: List<ReaderAnnotation>? = null
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, request ->
                requests += request
                emptyList()
            }
        ) { _, annotations -> reconciled = annotations }
        val annotation = highlight("server-row")
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(annotation))
        assertEquals(annotation, controller.state.value.deleting)
        controller.accept(ReaderAnnotationMutationIntent.ConfirmDelete)
        advanceUntilIdle()

        val deleted = requests.single() as ReaderAnnotationMutationRequest.Delete
        assertEquals(annotation.clientId, deleted.clientId)
        assertNotEquals(annotation.id, deleted.clientId)
        assertEquals(emptyList<ReaderAnnotation>(), reconciled)

        controller.select(profile(), "closed", ReaderSessionStatus.CLOSED)
        controller.accept(ReaderAnnotationMutationIntent.BeginEdit(annotation))
        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(annotation))
        controller.accept(ReaderAnnotationMutationIntent.BeginCreate(selection()))
        controller.accept(ReaderAnnotationMutationIntent.ConfirmDelete)
        controller.accept(ReaderAnnotationMutationIntent.SubmitCreate)
        runCurrent()
        assertEquals(1, requests.size)
        assertNull(controller.state.value.editing)
        assertNull(controller.state.value.deleting)
        assertNull(controller.state.value.pendingCreate)
    }

    @Test
    fun `failed delete retains confirmation and annotation identity for retry`() = runTest {
        val annotation = highlight("failed-delete")
        val controller = controller(this, ReaderAnnotationWriter { _, _ -> error("offline") })
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(annotation))
        controller.accept(ReaderAnnotationMutationIntent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(annotation, controller.state.value.deleting)
        assertEquals(ReaderAnnotationMutationFailure.UNAVAILABLE, controller.state.value.failure)
        assertFalse(controller.state.value.submitting)
    }

    @Test
    fun `bookmark delete uses client identity and authoritative removal`() = runTest {
        val requests = mutableListOf<ReaderAnnotationMutationRequest>()
        var reconciled: List<ReaderAnnotation>? = null
        val bookmark = bookmark("server-bookmark")
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, request ->
                requests += request
                emptyList()
            }
        ) { _, annotations -> reconciled = annotations }
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(bookmark))
        controller.accept(ReaderAnnotationMutationIntent.ConfirmDelete)
        advanceUntilIdle()

        val request = requests.single() as ReaderAnnotationMutationRequest.Delete
        assertEquals(bookmark.clientId, request.clientId)
        assertNotEquals(bookmark.id, request.clientId)
        assertEquals(emptyList<ReaderAnnotation>(), reconciled)
    }

    @Test
    fun `bookmark create retries stable identity and reconciles authoritative order`() = runTest {
        val requests = mutableListOf<ReaderAnnotationMutationRequest>()
        val authoritative = listOf(bookmark("server-new"), highlight("server-existing"))
        var fail = true
        var reconciled = emptyList<ReaderAnnotation>()
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, request ->
                requests += request
                if (fail) error("offline")
                authoritative
            }
        ) { _, annotations -> reconciled = annotations }
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.ACTIVE)
        controller.accept(ReaderAnnotationMutationIntent.CreateBookmark(position()))
        advanceUntilIdle()

        val first = requests.single() as ReaderAnnotationMutationRequest.UpsertBookmark
        assertTrue(runCatching { UUID.fromString(first.clientId) }.isSuccess)
        assertEquals(CFI, first.cfi)
        assertEquals(readerLocationLabel(3, 0.42), first.locationLabel)
        assertEquals(first.clientId, controller.state.value.pendingBookmark?.clientId)

        fail = false
        controller.accept(ReaderAnnotationMutationIntent.RetryBookmark)
        advanceUntilIdle()

        val retried = requests.last() as ReaderAnnotationMutationRequest.UpsertBookmark
        assertEquals(first.clientId, retried.clientId)
        assertEquals(authoritative, reconciled)

        controller.accept(ReaderAnnotationMutationIntent.CreateBookmark(position()))
        advanceUntilIdle()
        val independent = requests.last() as ReaderAnnotationMutationRequest.UpsertBookmark
        assertNotEquals(first.clientId, independent.clientId)
    }

    @Test
    fun `closed Session refuses bookmark create and bookmark delete`() = runTest {
        val requests = mutableListOf<ReaderAnnotationMutationRequest>()
        val controller = controller(
            this,
            ReaderAnnotationWriter { _, request ->
                requests += request
                emptyList()
            }
        )
        val bookmark = bookmark("closed")
        controller.select(profile(), SESSION_ID, ReaderSessionStatus.CLOSED)
        controller.accept(ReaderAnnotationMutationIntent.CreateBookmark(position()))
        controller.accept(ReaderAnnotationMutationIntent.RetryBookmark)
        controller.accept(ReaderAnnotationMutationIntent.RequestDelete(bookmark))
        controller.accept(ReaderAnnotationMutationIntent.ConfirmDelete)
        runCurrent()

        assertTrue(requests.isEmpty())
        assertNull(controller.state.value.pendingBookmark)
        assertNull(controller.state.value.deleting)
    }

    @Test
    fun `SPL writer sends one batch upsert or delete keyed only by client ID`() = runTest {
        val batches = mutableListOf<List<MarginaliaAnnotationOperation>>()
        val delegate = FakeAuthenticatedMarginaliaClient.sessions
        val sessions = object : AuthenticatedReadingSessionsClient by delegate {
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
        writer.synchronize(profile(), upsertRequest())
        writer.synchronize(
            profile(),
            ReaderAnnotationMutationRequest.UpsertBookmark(
                SESSION_ID,
                "client-bookmark",
                CFI,
                readerLocationLabel(3, 0.42)
            )
        )
        writer.synchronize(
            profile(),
            ReaderAnnotationMutationRequest.Delete(SESSION_ID, "client-existing")
        )

        val draft = (batches.first().single() as MarginaliaAnnotationOperation.Upsert)
            .annotation as MarginaliaAnnotationDraft.Highlight
        assertEquals("client-existing", draft.clientId)
        assertEquals(CFI, draft.location.cfi)
        assertEquals("Original quote", draft.body.text)
        assertEquals("Before", draft.body.prefix)
        assertEquals("After", draft.body.suffix)
        assertEquals(MarginaliaHighlightColor.PURPLE, draft.body.color)
        assertEquals("  Exact note\n", draft.body.note)
        val bookmarkDraft = (batches[1].single() as MarginaliaAnnotationOperation.Upsert)
            .annotation as MarginaliaAnnotationDraft.Bookmark
        assertEquals("client-bookmark", bookmarkDraft.clientId)
        assertEquals(CFI, bookmarkDraft.location.cfi)
        assertEquals(readerLocationLabel(3, 0.42), bookmarkDraft.location.locationLabel)
        assertEquals(
            "client-existing",
            (batches.last().single() as MarginaliaAnnotationOperation.Delete).clientId
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
        writer: ReaderAnnotationWriter,
        reconcile: (String, List<ReaderAnnotation>) -> Unit = { _, _ -> }
    ) = ReaderAnnotationMutationController(writer, scope, reconcile)

    private fun selection() = ReaderSelection(
        EpubCfi(CFI),
        "Selected text",
        "Before",
        "After",
        "Chapter 03 · 42%"
    )

    private fun highlight(id: String) = ReaderAnnotation.Highlight(
        id = id,
        clientId = "client-$id",
        cfi = CFI,
        locationLabel = "Chapter 03 · 42%",
        updatedAt = "2026-08-25T00:00:00Z",
        quote = "Original quote",
        prefix = "Before",
        suffix = "After",
        note = "Original note",
        color = ReaderAnnotationColor.YELLOW
    )

    private fun bookmark(id: String) = ReaderAnnotation.Bookmark(
        id = id,
        clientId = "client-$id",
        cfi = CFI,
        locationLabel = readerLocationLabel(3, 0.42),
        updatedAt = "2026-08-25T00:00:00Z"
    )

    private fun position() = EpubCfiPosition(EpubCfi(CFI), 3, 0.42)

    private fun upsertRequest() = ReaderAnnotationMutationRequest.UpsertHighlight(
        SESSION_ID,
        "client-existing",
        CFI,
        "Chapter 03 · 42%",
        "Original quote",
        "Before",
        "After",
        ReaderAnnotationColor.PURPLE,
        "  Exact note\n"
    )

    private fun clientProvider(sessions: AuthenticatedReadingSessionsClient) =
        object : AuthenticatedClientProvider {
            override suspend fun forProfile(
                profile: ConnectionProfile
            ): AuthenticatedSecondPassClient = object : AuthenticatedSecondPassClient {
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
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-25",
        clientSessionId = "client-session",
        clientName = "Reader",
        clientType = "reader"
    )
}

private const val SESSION_ID = "session"
private const val CFI = "epubcfi(/6/2!/4/2,/1:0,/1:4)"
