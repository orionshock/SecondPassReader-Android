package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.design.icons.AppIcon
import java.time.ZoneOffset
import java.util.Locale
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
    fun `reading presentation uses activity date when Session name repeats Book title`() {
        val model = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.ACTIVE),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US
        )

        assertEquals("Chapter 4", model.locationLabel)
        assertEquals("Read Aug 16, 2026", model.sessionIdentityLabel)
        assertEquals(
            "The Dispossessed, Read Aug 16, 2026, Chapter 4, Active",
            model.accessibilityDescription
        )
        assertEquals(
            OpenReaderIntent(
                "book-1",
                "session-1",
                "The Dispossessed"
            ),
            model.primaryIntent
        )
    }

    @Test
    fun `unavailable reading history does not expose a Reader action`() {
        val model =
            HomePresenter.readingHistory(
                recent(ReadingSessionStatus.CLOSED, canOpen = false)
            )

        assertNull(model.primaryIntent)
    }

    @Test
    fun `explicit Session name is primary identity and malformed date has safe fallback`() {
        val named = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.CLOSED).copy(sessionName = "Second pass")
        )
        val fallback = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.CLOSED).copy(
                sessionName = "",
                lastActivityAt = "unknown"
            )
        )

        assertEquals("Second pass", named.sessionIdentityLabel)
        assertEquals("Reading Session", fallback.sessionIdentityLabel)
    }

    @Test
    fun `same Book Sessions retain distinct presentation identities`() {
        val active = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.ACTIVE).copy(sessionName = "Current read")
        )
        val closedFirst = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.CLOSED).copy(sessionName = "First pass")
        )
        val closedSecond = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.CLOSED).copy(sessionName = "Reference pass")
        )

        assertEquals(
            listOf("Current read", "First pass", "Reference pass"),
            listOf(active, closedFirst, closedSecond).map { it.sessionIdentityLabel }
        )
        assertEquals(
            listOf(
                ReadingStatusIndicator.Active,
                ReadingStatusIndicator.Closed,
                ReadingStatusIndicator.Closed
            ),
            listOf(active, closedFirst, closedSecond).map { it.statusIndicator }
        )
    }

    @Test
    fun `offline reading uses local availability and removes Session mutations`() {
        val unavailable = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.ACTIVE),
            offlineReadable = false,
            offline = true
        )
        val downloaded = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.ACTIVE, canOpen = false),
            offlineReadable = true,
            offline = true
        )
        val closedUnavailable = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.CLOSED),
            offlineReadable = false,
            offline = true
        )
        val reconnected = HomePresenter.readingHistory(
            recent(ReadingSessionStatus.ACTIVE),
            offlineReadable = false,
            offline = false
        )

        assertNull(unavailable.primaryIntent)
        assertEquals(true, unavailable.unavailableOffline)
        assertEquals("book-1", downloaded.primaryIntent?.bookId)
        assertEquals(false, downloaded.unavailableOffline)
        assertEquals(ReadingStatusIndicator.Closed, closedUnavailable.statusIndicator)
        assertEquals(true, closedUnavailable.unavailableOffline)
        assertEquals(false, reconnected.unavailableOffline)
        assertEquals("book-1", reconnected.primaryIntent?.bookId)
        assertEquals(
            listOf(
                HomeNavigationIntent.BookAction(BookCardAction.BookDetails("book-1")),
                HomeNavigationIntent.OpenReadingSessionDetail("session-1")
            ),
            downloaded.contextActions
        )
    }

    @Test
    fun `active reading history exposes detail edit and close context actions`() {
        val model = HomePresenter.readingHistory(recent(ReadingSessionStatus.ACTIVE))

        assertEquals(
            listOf(
                HomeNavigationIntent.BookAction(BookCardAction.BookDetails("book-1")),
                HomeNavigationIntent.OpenReadingSessionDetail("session-1"),
                HomeNavigationIntent.OpenReadingSessionDetail(
                    "session-1",
                    ReadingSessionDetailAction.EDIT
                ),
                HomeNavigationIntent.OpenReadingSessionDetail(
                    "session-1",
                    ReadingSessionDetailAction.CLOSE
                )
            ),
            model.contextActions
        )
    }

    @Test
    fun `closed reading history exposes read-only context actions`() {
        val model = HomePresenter.readingHistory(recent(ReadingSessionStatus.CLOSED))

        assertEquals(
            listOf(
                HomeNavigationIntent.BookAction(BookCardAction.BookDetails("book-1")),
                HomeNavigationIntent.OpenReadingSessionDetail("session-1")
            ),
            model.contextActions
        )
    }

    @Test
    fun `shelf presentation derives owner and visible item count`() {
        val shelf = shelf(ShelfOwner.User("p1", "reader"), 3)

        val model = HomePresenter.shelf(shelf)

        assertEquals("reader", model.ownerLabel)
        assertEquals(AppIcon.User, model.ownerIcon)
        assertEquals("3 books", model.itemCountLabel)
        assertEquals("shelf-1", model.id)
        assertEquals(HomeShelfOrigin.PERSONAL, model.origin)
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

    @Test
    fun `group and shared shelf owners retain distinct semantic icons`() {
        val group =
            HomePresenter.shelf(shelf(ShelfOwner.Group("g1", "Readers", true), 0))
        val shared =
            HomePresenter.shelf(shelf(ShelfOwner.User("p2", "other-reader"), 2, false))

        assertEquals("Readers", group.ownerLabel)
        assertEquals(AppIcon.GroupShelf, group.ownerIcon)
        assertEquals(HomeShelfOrigin.GROUP, group.origin)
        assertEquals("other-reader", shared.ownerLabel)
        assertEquals(AppIcon.SharedShelf, shared.ownerIcon)
        assertEquals(HomeShelfOrigin.SHARED, shared.origin)
    }

    private fun recent(status: ReadingSessionStatus, canOpen: Boolean = true) = RecentReadingItem(
        sessionId = "session-1",
        sessionName = "The Dispossessed",
        status = status,
        lastActivityAt = "2026-08-16T12:00:00Z",
        book = RecentReadingBook("book-1", "The Dispossessed", null, canOpen),
        progress = ReadingProgress("epubcfi(/6/4)", "Chapter 4", "2026-08-16T12:00:00Z")
    )

    private fun shelf(owner: ShelfOwner, count: Int, canEdit: Boolean = true) = ShelfSummary(
        id = "shelf-1",
        name = "Favorites",
        description = null,
        owner = owner,
        visibility = ShelfVisibility.PRIVATE,
        itemCount = count,
        canEdit = canEdit,
        previewBooks = null
    )
}
