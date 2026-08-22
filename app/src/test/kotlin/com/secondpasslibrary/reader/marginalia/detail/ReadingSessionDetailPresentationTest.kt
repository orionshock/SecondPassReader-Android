package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaAnnotationLocation
import com.secondpasslibrary.client.MarginaliaHighlightBody
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingSessionDetail
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.reader.design.marginalia.AnnotationHighlightTone
import com.secondpasslibrary.reader.design.marginalia.toHighlightTone
import com.secondpasslibrary.reader.marginalia.detail.annotations.ReadingSessionAnnotationPresentation
import com.secondpasslibrary.reader.marginalia.detail.annotations.toPresentation
import com.secondpasslibrary.reader.marginalia.sessionBook
import com.secondpasslibrary.reader.marginalia.sessionSummary
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionDetailPresentationTest {
    private val zone = ZoneId.of("America/Phoenix")
    private val locale = Locale.US

    @Test
    fun `detail presents metadata without exposing progress CFI`() {
        val detail = detail(
            name = "Morning pass",
            progress = ReadingProgress("secret-cfi", "Chapter 10", "2026-08-03T00:00:00Z")
        ).toDetailPresentation(zone, locale)

        assertEquals("Book book-session-1", detail.bookTitle)
        assertEquals("Morning pass", detail.sessionName)
        assertEquals("Active", detail.statusLabel)
        assertEquals("Chapter 10", detail.progressLocation)
        assertFalse(detail.toString().contains("secret-cfi"))
    }

    @Test
    fun `detail app bar uses book identity when loaded`() {
        val state = ReadingSessionDetailState(detail = detail())

        assertEquals("Book book-session-1", state.screenTitle())
        assertEquals("Reading session", ReadingSessionDetailState(loading = true).screenTitle())
    }

    @Test
    fun `blank name and nullable progress stay absent`() {
        val detail = detail(name = "", progress = null).toDetailPresentation(zone, locale)

        assertNull(detail.sessionName)
        assertEquals("Unnamed reading session", detail.sessionNameOrFallback())
        assertNull(detail.progressLocation)
    }

    @Test
    fun `closed detail presents closed timestamp and read only notice`() {
        val detail = detail(status = ReadingSessionStatus.CLOSED).toDetailPresentation(zone, locale)

        assertEquals("Closed", detail.statusLabel)
        assertFalse(detail.active)
        assertEquals("This reading session is closed.", detail.closedNotice)
        assertEquals("Aug 1, 2026, 5:00\u202FPM", detail.closedLabel)
    }

    @Test
    fun `bookmark presentation excludes protocol identities and CFI`() {
        val model = bookmark().toPresentation(zone, locale)

        assertTrue(model is ReadingSessionAnnotationPresentation.Bookmark)
        assertEquals("Bookmark", model.label)
        assertEquals("Chapter 2", model.locationLabel)
        assertFalse(model.toString().contains("server-secret"))
        assertFalse(model.toString().contains("client-secret"))
        assertFalse(model.toString().contains("opaque-cfi"))
    }

    @Test
    fun `highlight and commented highlight map distinct presentation`() {
        val highlight = highlight(note = null).toPresentation(zone, locale)
        val commented = highlight(note = "Remember this").toPresentation(zone, locale)

        assertEquals("Highlight", highlight.label)
        assertEquals("Commented highlight", commented.label)
        assertEquals(
            "Selected words",
            (commented as ReadingSessionAnnotationPresentation.Highlight).quote
        )
        assertEquals("Remember this", commented.note)
    }

    @Test
    fun `all durable colors map to concrete presentation tones`() {
        assertEquals(
            listOf(
                AnnotationHighlightTone.YELLOW,
                AnnotationHighlightTone.GREEN,
                AnnotationHighlightTone.BLUE,
                AnnotationHighlightTone.PINK,
                AnnotationHighlightTone.PURPLE,
                AnnotationHighlightTone.ORANGE
            ),
            MarginaliaHighlightColor.entries.map { it.toHighlightTone() }
        )
    }

    @Test
    fun `mapping annotations preserves server order`() {
        val annotations = listOf(bookmark(), highlight(note = null), highlight(note = "Note"))

        assertEquals(
            listOf("Bookmark", "Highlight", "Commented highlight"),
            annotations.map { it.toPresentation(zone, locale).label }
        )
    }
}

private fun detail(
    name: String = "Session",
    status: ReadingSessionStatus = ReadingSessionStatus.ACTIVE,
    progress: ReadingProgress? = null
): ReadingSessionDetailResult {
    val summary = sessionSummary("session-1", status).copy(
        name = name,
        startedAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        closedAt = if (status == ReadingSessionStatus.CLOSED) "2026-08-02T00:00:00Z" else null
    )
    return ReadingSessionDetailResult(
        sessionBook("book-session-1"),
        ReadingSessionDetail(summary, progress)
    )
}

private fun bookmark() = MarginaliaAnnotation.Bookmark(
    id = "server-secret",
    clientId = "client-secret",
    location = MarginaliaAnnotationLocation("opaque-cfi", "Chapter 2"),
    createdAt = "2026-08-01T00:00:00Z",
    updatedAt = "2026-08-02T00:00:00Z"
)

private fun highlight(note: String?) = MarginaliaAnnotation.Highlight(
    id = "highlight-server-secret",
    clientId = "highlight-client-secret",
    location = MarginaliaAnnotationLocation("highlight-cfi", "Chapter 3"),
    createdAt = "2026-08-01T00:00:00Z",
    updatedAt = "2026-08-02T00:00:00Z",
    body = MarginaliaHighlightBody(
        text = "Selected words",
        prefix = "hidden prefix",
        suffix = "hidden suffix",
        color = MarginaliaHighlightColor.BLUE,
        note = note
    )
)
