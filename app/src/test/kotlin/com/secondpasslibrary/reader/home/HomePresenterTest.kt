package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.client.ShelfVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomePresenterTest {
    @Test
    fun `reading status retains active and closed vocabulary`() {
        val active = HomePresenter.readingHistory(recent(ReadingSessionStatus.ACTIVE))
        val closed = HomePresenter.readingHistory(recent(ReadingSessionStatus.CLOSED))

        assertEquals("Active", active.statusLabel)
        assertEquals(ReadingStatusIndicator.Active, active.statusIndicator)
        assertEquals("Closed", closed.statusLabel)
        assertEquals(ReadingStatusIndicator.Closed, closed.statusIndicator)
    }

    @Test
    fun `reading presentation keeps opaque location and suppresses redundant session name`() {
        val model = HomePresenter.readingHistory(recent(ReadingSessionStatus.ACTIVE))

        assertEquals("Chapter 4", model.locationLabel)
        assertNull(model.sessionName)
    }

    @Test
    fun `shelf presentation derives owner and visible item count`() {
        val shelf = shelf(ShelfOwner.User("p1", "reader"), 3)

        val model = HomePresenter.shelf(shelf)

        assertEquals("reader", model.ownerLabel)
        assertEquals("3 books", model.itemCountLabel)
    }

    @Test
    fun `shelf presentation handles anonymous and singular ownership labels`() {
        val shelf = shelf(ShelfOwner.User("p1", null), 1)

        val model = HomePresenter.shelf(shelf)

        assertEquals("Personal shelf", model.ownerLabel)
        assertEquals("1 book", model.itemCountLabel)
    }

    @Test
    fun `missing cover remains an explicit fallback state`() {
        val recent = HomePresenter.readingHistory(recent(ReadingSessionStatus.ACTIVE))
        val shelf = HomePresenter.shelf(shelf(ShelfOwner.Group("g1", "Readers", true), 0))

        assertSame(BookCoverPresentation.Missing, recent.cover)
        assertNull(shelf.previewBooks)
    }

    private fun recent(status: ReadingSessionStatus) = RecentReadingItem(
        sessionId = "session-1",
        sessionName = "The Dispossessed",
        status = status,
        lastActivityAt = "2026-08-16T12:00:00Z",
        book = RecentReadingBook("book-1", "The Dispossessed", null, true),
        progress = ReadingProgress("epubcfi(/6/4)", "Chapter 4", "2026-08-16T12:00:00Z")
    )

    private fun shelf(owner: ShelfOwner, count: Int) = ShelfSummary(
        id = "shelf-1",
        name = "Favorites",
        description = null,
        owner = owner,
        visibility = ShelfVisibility.PRIVATE,
        itemCount = count,
        canEdit = true,
        previewBooks = null
    )
}
