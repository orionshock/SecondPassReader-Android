package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.ReadingSessionStatus
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginaliaPresentationTest {
    @Test
    fun `status controls retain All Active Closed vocabulary`() {
        assertEquals(
            listOf("All", "Active", "Closed"),
            ReadingSessionStatusFilter.entries.map { it.presentationLabel }
        )
    }

    @Test
    fun `Book is primary and blank Session name is suppressed`() {
        val presentation = sessionItem("session-1").toRowPresentation(
            ZoneId.of("UTC"),
            Locale.US
        )

        assertEquals("Book book-session-1", presentation.bookTitle)
        assertNull(presentation.sessionName)
        assertEquals("2 annotations", presentation.annotationCountLabel)
    }

    @Test
    fun `active and closed presentation remain distinct`() {
        val active = sessionItem("active").toRowPresentation()
        val closed = sessionItem("closed", ReadingSessionStatus.CLOSED).toRowPresentation()

        assertEquals("Active", active.statusLabel)
        assertTrue(active.active)
        assertEquals("Closed", closed.statusLabel)
        assertFalse(closed.active)
    }

    @Test
    fun `timestamp uses requested locale and device timezone boundary`() {
        val formatted = formatSessionTimestamp(
            "2026-08-15T19:06:39.631898-07:00",
            ZoneId.of("America/Phoenix"),
            Locale.US
        )

        assertEquals("Aug 15, 2026, 7:06\u202FPM", formatted)
    }

    @Test
    fun `global and Book contexts produce contextual titles and empty copy`() {
        val global = ReadingSessionsState()
        val book = ReadingSessionsState(
            context = MarginaliaHistoryContext.Book("book-1"),
            book = sessionBook("book-1")
        )

        assertEquals("Marginalia", global.screenTitle())
        assertEquals("Reading sessions for Book book-1", book.screenTitle())
        assertEquals("No reading sessions for this book.", book.emptyMessage())
    }

    @Test
    fun `empty copy follows active and closed filters`() {
        assertEquals(
            "No active reading sessions.",
            ReadingSessionsState(statusFilter = ReadingSessionStatusFilter.ACTIVE).emptyMessage()
        )
        assertEquals(
            "No closed reading sessions.",
            ReadingSessionsState(statusFilter = ReadingSessionStatusFilter.CLOSED).emptyMessage()
        )
    }

    @Test
    fun `paging trigger waits until the visible tail approaches`() {
        assertFalse(shouldRequestMoreSessions(lastVisibleIndex = 4, itemCount = 20))
        assertTrue(shouldRequestMoreSessions(lastVisibleIndex = 15, itemCount = 20))
        assertFalse(shouldRequestMoreSessions(lastVisibleIndex = -1, itemCount = 0))
    }

    @Test
    fun `result count uses correct singular and plural labels`() {
        assertEquals("1 session", sessionCountLabel(1))
        assertEquals("5 sessions", sessionCountLabel(5))
    }
}
