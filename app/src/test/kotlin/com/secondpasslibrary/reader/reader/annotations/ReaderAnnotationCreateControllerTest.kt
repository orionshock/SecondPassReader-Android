package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.client.MarginaliaAnnotationDraft
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.reader.FakeAuthenticatedMarginaliaClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
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
class ReaderAnnotationCreateControllerTest {
    @Test
    fun `active Session submits exact highlight and reconciles authoritative order`() = runTest {
        val requests = mutableListOf<ReaderHighlightCreateRequest>()
        var reconciled = emptyList<ReaderAnnotation>()
        val authoritative = listOf(bookmark("server-b"), highlight("server-h"))
        val controller = controller(
            scope = this,
            writer = ReaderHighlightWriter { _, request ->
                requests += request
                authoritative
            },
            reconcile = { _, annotations -> reconciled = annotations }
        )
        controller.select(profile(), "session", ReaderSessionStatus.ACTIVE)

        controller.begin(selection())
        controller.updateColor(ReaderAnnotationColor.PINK)
        controller.updateNote("Exact note  \n")
        controller.submit()
        advanceUntilIdle()

        val request = requests.single()
        assertEquals("session", request.sessionId)
        assertEquals(CREATE_CFI, request.selection.cfi.value)
        assertEquals("Selected text", request.selection.selectedText)
        assertEquals("Before", request.selection.prefix)
        assertEquals("After", request.selection.suffix)
        assertEquals("Chapter 03 · 42%", request.selection.locationLabel)
        assertEquals(ReaderAnnotationColor.PINK, request.color)
        assertEquals("Exact note  \n", request.note)
        assertTrue(runCatching { UUID.fromString(request.clientId) }.isSuccess)
        assertTrue(request.clientId.length <= 255)
        assertEquals(listOf("server-b", "server-h"), reconciled.map { it.id })
        assertNull(controller.state.value.pending)
    }

    @Test
    fun `failed retry reuses client ID and independent create gets another`() = runTest {
        val ids = mutableListOf<String>()
        var fail = true
        val controller = controller(
            scope = this,
            writer = ReaderHighlightWriter { _, request ->
                ids += request.clientId
                if (fail) throw IllegalStateException("offline")
                listOf(highlight("server"))
            }
        )
        controller.select(profile(), "session", ReaderSessionStatus.ACTIVE)
        controller.begin(selection())
        val originalId = controller.state.value.pending?.clientId
        controller.updateNote("First draft")
        controller.submit()
        advanceUntilIdle()
        val pendingId = controller.state.value.pending?.clientId
        assertEquals("First draft", controller.state.value.pending?.note)

        fail = false
        controller.updateColor(ReaderAnnotationColor.BLUE)
        controller.updateNote("Latest draft")
        assertEquals(pendingId, controller.state.value.pending?.clientId)
        assertEquals(ReaderAnnotationColor.BLUE, controller.state.value.pending?.color)
        controller.submit()
        advanceUntilIdle()
        controller.begin(selection())
        controller.submit()
        advanceUntilIdle()

        assertEquals(originalId, pendingId)
        assertEquals(pendingId, ids[0])
        assertEquals(ids[0], ids[1])
        assertNotEquals(ids[1], ids[2])
    }

    @Test
    fun `closed Session never sends and Session replacement cancels stale ownership`() = runTest {
        var calls = 0
        val controller = controller(
            scope = this,
            writer = ReaderHighlightWriter { _, _ ->
                calls += 1
                emptyList()
            }
        )
        controller.select(profile(), "closed", ReaderSessionStatus.CLOSED)
        controller.begin(selection())
        controller.submit()
        runCurrent()
        assertEquals(0, calls)
        assertFalse(controller.state.value.submitting)

        controller.select(profile(), "active", ReaderSessionStatus.ACTIVE)
        controller.begin(selection())
        controller.updateColor(ReaderAnnotationColor.GREEN)
        controller.submit()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `web palette and supported semantic colors are exact`() {
        assertEquals(
            listOf(
                0xFFFACC15,
                0xFF22C55E,
                0xFF3B82F6,
                0xFFEC4899,
                0xFFA855F7,
                0xFFF97316
            ),
            ReaderAnnotationColor.entries.map { it.displayArgb }
        )
        assertEquals(ReaderAnnotationColor.YELLOW, ReaderAnnotationColor.entries.first())
    }

    @Test
    fun `SPL writer sends one exact batch upsert with no alternate mutation path`() = runTest {
        val batches = mutableListOf<Pair<String, List<MarginaliaAnnotationOperation>>>()
        val delegate = FakeAuthenticatedMarginaliaClient.sessions
        val sessions = object : AuthenticatedReadingSessionsClient by delegate {
            override suspend fun synchronizeAnnotations(
                sessionId: String,
                operations: List<MarginaliaAnnotationOperation>
            ): List<com.secondpasslibrary.client.MarginaliaAnnotation> {
                batches += sessionId to operations
                return listOf(highlight("authoritative").toSdkAnnotation())
            }
        }
        val marginalia = object : com.secondpasslibrary.client.AuthenticatedMarginaliaClient {
            override val books = FakeAuthenticatedMarginaliaClient.books
            override val sessions = sessions
        }
        val client = object : AuthenticatedSecondPassClient {
            override val library: AuthenticatedLibraryClient get() = error("unused")
            override val shelves: AuthenticatedShelvesClient get() = error("unused")
            override val marginalia = marginalia
        }
        val writer = SplReaderHighlightWriter(
            object : AuthenticatedClientProvider {
                override suspend fun forProfile(profile: ConnectionProfile) = client
            }
        )

        writer.create(
            profile(),
            ReaderHighlightCreateRequest(
                "session",
                "client-create",
                selection(),
                ReaderAnnotationColor.PURPLE,
                "  Preserve exactly.\n"
            )
        )
        writer.create(
            profile(),
            ReaderHighlightCreateRequest(
                "session",
                "client-empty-note",
                selection(),
                ReaderAnnotationColor.YELLOW,
                ""
            )
        )

        val (sessionId, operations) = batches.first()
        val draft = (operations.single() as MarginaliaAnnotationOperation.Upsert).annotation
            as MarginaliaAnnotationDraft.Highlight
        assertEquals("session", sessionId)
        assertEquals("client-create", draft.clientId)
        assertEquals(CREATE_CFI, draft.location.cfi)
        assertEquals("Chapter 03 · 42%", draft.location.locationLabel)
        assertEquals("Selected text", draft.body.text)
        assertEquals("Before", draft.body.prefix)
        assertEquals("After", draft.body.suffix)
        assertEquals(MarginaliaHighlightColor.PURPLE, draft.body.color)
        assertEquals("  Preserve exactly.\n", draft.body.note)
        val emptyNoteDraft =
            (batches.last().second.single() as MarginaliaAnnotationOperation.Upsert)
                .annotation as MarginaliaAnnotationDraft.Highlight
        assertEquals("", emptyNoteDraft.body.note)
    }

    @Test
    fun `pending draft defaults and edits preserve logical identity`() = runTest {
        val controller = controller(this, ReaderHighlightWriter { _, _ -> emptyList() })
        controller.select(profile(), "session", ReaderSessionStatus.ACTIVE)
        controller.begin(selection())
        val clientId = controller.state.value.pending?.clientId

        assertEquals(ReaderAnnotationColor.YELLOW, controller.state.value.pending?.color)
        assertEquals("", controller.state.value.pending?.note)

        controller.updateNote("Reader note")
        controller.updateColor(ReaderAnnotationColor.ORANGE)

        assertEquals(clientId, controller.state.value.pending?.clientId)
        assertEquals("Reader note", controller.state.value.pending?.note)
        assertEquals(ReaderAnnotationColor.ORANGE, controller.state.value.pending?.color)

        controller.updateNote("x".repeat(MAX_HIGHLIGHT_NOTE_LENGTH + 1))
        assertEquals("Reader note", controller.state.value.pending?.note)

        controller.dismiss()
        assertNull(controller.state.value.pending)
    }

    private fun controller(
        scope: CoroutineScope,
        writer: ReaderHighlightWriter,
        reconcile: (String, List<ReaderAnnotation>) -> Unit = { _, _ -> }
    ) = ReaderAnnotationCreateController(writer, scope, reconcile)

    private fun selection() = ReaderSelection(
        cfi = EpubCfi(CREATE_CFI),
        selectedText = "Selected text",
        prefix = "Before",
        suffix = "After",
        locationLabel = "Chapter 03 · 42%"
    )

    private fun bookmark(id: String) = ReaderAnnotation.Bookmark(
        id,
        CREATE_CFI,
        "Chapter 03 · 42%",
        "2026-08-25T00:00:00Z"
    )

    private fun highlight(id: String) = ReaderAnnotation.Highlight(
        id,
        CREATE_CFI,
        "Chapter 03 · 42%",
        "2026-08-25T00:00:00Z",
        "Selected text",
        null,
        ReaderAnnotationColor.YELLOW
    )

    private fun ReaderAnnotation.Highlight.toSdkAnnotation() =
        com.secondpasslibrary.client.MarginaliaAnnotation.Highlight(
            id = id,
            clientId = "client-$id",
            location = com.secondpasslibrary.client.MarginaliaAnnotationLocation(
                cfi,
                locationLabel
            ),
            createdAt = updatedAt,
            updatedAt = updatedAt,
            body = com.secondpasslibrary.client.MarginaliaHighlightBody(
                text = quote,
                prefix = null,
                suffix = null,
                color = MarginaliaHighlightColor.YELLOW,
                note = note
            )
        )

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

private const val CREATE_CFI = "epubcfi(/6/2!/4/2,/1:0,/1:4)"
