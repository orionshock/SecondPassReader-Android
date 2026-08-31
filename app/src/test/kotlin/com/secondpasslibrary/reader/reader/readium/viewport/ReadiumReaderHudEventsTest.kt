package com.secondpasslibrary.reader.reader.readium.viewport

import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatusScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadiumReaderHudEventsTest {
    @Test
    fun `Readium zero based page index maps to exact remaining section pages`() {
        assertEquals(
            ReaderReadingStatus(8, ReaderReadingStatusScope.SECTION),
            readerSectionStatus(pageIndex = 3, totalPages = 12)
        )
        assertEquals(
            ReaderReadingStatus(0, ReaderReadingStatusScope.SECTION),
            readerSectionStatus(pageIndex = 11, totalPages = 12)
        )
    }

    @Test
    fun `invalid pagination never invents reading status`() {
        assertNull(readerSectionStatus(pageIndex = -1, totalPages = 12))
        assertNull(readerSectionStatus(pageIndex = 0, totalPages = 0))
        assertNull(readerSectionStatus(pageIndex = 12, totalPages = 12))
    }

    @Test
    fun `stale navigator page and page-loaded signals cannot reach a newer attachment`() {
        val tracker = ReadiumSectionPaginationTracker()
        val oldGeneration = tracker.newGeneration()
        tracker.publish(oldGeneration, pageIndex = 1, totalPages = 10)
        val newGeneration = tracker.newGeneration()
        tracker.publish(newGeneration, pageIndex = 4, totalPages = 6)

        tracker.publish(oldGeneration, pageIndex = 2, totalPages = 100)

        assertFalse(tracker.isCurrent(oldGeneration))
        assertTrue(tracker.isCurrent(newGeneration))
        assertEquals(
            ReaderReadingStatus(1, ReaderReadingStatusScope.SECTION),
            tracker.status.value
        )
    }
}
