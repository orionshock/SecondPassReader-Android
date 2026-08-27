package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaAnnotationLocation
import com.secondpasslibrary.client.MarginaliaHighlightBody
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAnnotationsControllerTest {
    @Test
    fun `active and closed Session identities load exact authoritative order`() = runTest {
        val requests = mutableListOf<String>()
        val annotations = listOf(
            bookmark("bookmark-1").toReaderAnnotation(),
            highlight("highlight-1").toReaderAnnotation()
        )
        val controller = ReaderAnnotationsController(
            ReaderAnnotationsLoader { _, sessionId ->
                requests += sessionId
                annotations
            },
            this
        )

        controller.select(profile(), "active-session")
        advanceUntilIdle()
        assertEquals(listOf("active-session"), requests)
        assertEquals(
            listOf("bookmark-1", "highlight-1"),
            controller.state.value.annotations.map { annotation -> annotation.id }
        )

        controller.select(profile(), "closed-session")
        advanceUntilIdle()
        assertEquals(listOf("active-session", "closed-session"), requests)
        assertEquals("closed-session", controller.state.value.sessionId)
        assertTrue(controller.state.value.loaded)
    }

    @Test
    fun `Session replacement rejects late stale annotations`() = runTest {
        val firstResult = CompletableDeferred<List<ReaderAnnotation>>()
        val controller = ReaderAnnotationsController(
            ReaderAnnotationsLoader { _, sessionId ->
                if (sessionId == "session-a") {
                    withContext(NonCancellable) { firstResult.await() }
                } else {
                    listOf(ReaderAnnotation.Bookmark("b", "client-b", CFI, "B", UPDATED_AT))
                }
            },
            this
        )

        controller.select(profile(), "session-a")
        runCurrent()
        controller.select(profile(), "session-b")
        runCurrent()
        firstResult.complete(
            listOf(ReaderAnnotation.Bookmark("a", "client-a", CFI, "A", UPDATED_AT))
        )
        advanceUntilIdle()

        assertEquals("session-b", controller.state.value.sessionId)
        assertEquals(listOf("b"), controller.state.value.annotations.map { it.id })
    }

    @Test
    fun `failed load is compact state and retry remains read only`() = runTest {
        var attempts = 0
        val controller = ReaderAnnotationsController(
            ReaderAnnotationsLoader { _, _ ->
                attempts += 1
                if (attempts != 2) {
                    listOf(ReaderAnnotation.Bookmark("b", "client-b", CFI, null, UPDATED_AT))
                } else {
                    throw SplClientException.ServerUnreachable()
                }
            },
            this
        )
        controller.select(profile(), "session")
        advanceUntilIdle()

        controller.clear()
        controller.select(profile(clientSessionId = "new-client-session"), "session")
        advanceUntilIdle()

        assertEquals(ReaderAnnotationsFailure.UNAVAILABLE, controller.state.value.failure)
        assertFalse(controller.state.value.loading)

        controller.retry()
        advanceUntilIdle()
        assertTrue(controller.state.value.loaded)
        assertEquals(listOf("b"), controller.state.value.annotations.map { it.id })
    }

    @Test
    fun `SDK annotations project only Reader-owned fields`() {
        val bookmark = bookmark("bookmark").toReaderAnnotation() as ReaderAnnotation.Bookmark
        val highlight = highlight("highlight").toReaderAnnotation() as ReaderAnnotation.Highlight

        assertEquals(CFI, bookmark.cfi)
        assertEquals("client-bookmark", bookmark.clientId)
        assertEquals("Chapter 2", bookmark.locationLabel)
        assertEquals("Selected quote", highlight.quote)
        assertEquals("client-highlight", highlight.clientId)
        assertEquals("Before", highlight.prefix)
        assertEquals("After", highlight.suffix)
        assertEquals("Reader note", highlight.note)
        assertEquals(ReaderAnnotationColor.BLUE, highlight.color)
        assertEquals(CFI, highlight.cfi)
    }

    @Test
    fun `SDK annotation read projection preserves quote context and note verbatim`() {
        val projected = MarginaliaAnnotation.Highlight(
            id = "verbatim",
            clientId = "client-verbatim",
            location = MarginaliaAnnotationLocation(CFI, "Chapter 3"),
            createdAt = CREATED_AT,
            updatedAt = UPDATED_AT,
            body = MarginaliaHighlightBody(
                text = "  Stored\n\n exactly\t as returned  ",
                prefix = "\u00A0 Before\t ",
                suffix = " After\r\n ",
                color = MarginaliaHighlightColor.YELLOW,
                note = "  first line\n    indented\nlast line  "
            )
        ).toReaderAnnotation() as ReaderAnnotation.Highlight

        assertEquals("  Stored\n\n exactly\t as returned  ", projected.quote)
        assertEquals("\u00A0 Before\t ", projected.prefix)
        assertEquals(" After\r\n ", projected.suffix)
        assertEquals("  first line\n    indented\nlast line  ", projected.note)
    }

    private fun bookmark(id: String) = MarginaliaAnnotation.Bookmark(
        id = id,
        clientId = "client-$id",
        location = MarginaliaAnnotationLocation(CFI, "Chapter 2"),
        createdAt = CREATED_AT,
        updatedAt = UPDATED_AT
    )

    private fun highlight(id: String) = MarginaliaAnnotation.Highlight(
        id = id,
        clientId = "client-$id",
        location = MarginaliaAnnotationLocation(CFI, "Chapter 3"),
        createdAt = CREATED_AT,
        updatedAt = UPDATED_AT,
        body = MarginaliaHighlightBody(
            text = "Selected quote",
            prefix = "Before",
            suffix = "After",
            color = MarginaliaHighlightColor.BLUE,
            note = "Reader note"
        )
    )

    private fun profile(clientSessionId: String = "client-session") = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-24",
        clientSessionId = clientSessionId,
        clientName = "Reader",
        clientType = "reader"
    )

    private companion object {
        const val CFI = "epubcfi(/6/2!/4/2:3)"
        const val CREATED_AT = "2026-08-24T12:00:00Z"
        const val UPDATED_AT = "2026-08-24T13:00:00Z"
    }
}
