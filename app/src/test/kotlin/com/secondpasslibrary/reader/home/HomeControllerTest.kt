package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingBook
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeControllerTest {
    @Test
    fun `initial load requests Home bounds and preserves server ordering`() = runTest {
        val client = FakeAuthenticatedClient().apply {
            recentResult = listOf(recent("recent-2"), recent("recent-1"))
            shelfResult = listOf(shelf("shelf-2"), shelf("shelf-1"))
        }
        val controller = HomeController(provider(client), this)

        controller.initialize(profile())
        advanceUntilIdle()
        controller.initialize(profile())
        advanceUntilIdle()

        assertEquals(
            listOf(RecentReadingOptions(limit = 10, includeClosed = false)),
            client.recentRequests
        )
        assertEquals(
            ShelfListOptions(page = 1, pageSize = 6, previewLimit = 3),
            client.shelfRequests.single()
        )
        val recent = controller.state.value.recentReading as HomeSectionState.Loaded
        val shelves = controller.state.value.shelves as HomeSectionState.Loaded
        assertEquals(listOf("recent-2", "recent-1"), recent.items.map { it.sessionId })
        assertEquals(listOf("shelf-2", "shelf-1"), shelves.items.map { it.id })
    }

    @Test
    fun `section failure does not blank the independently loaded section`() = runTest {
        val client = FakeAuthenticatedClient().apply {
            recentFailure = SplClientException.ServerUnreachable()
            shelfResult = listOf(shelf("shelf-1"))
        }
        val controller = HomeController(provider(client), this)

        controller.initialize(profile())
        advanceUntilIdle()

        assertTrue(controller.state.value.recentReading is HomeSectionState.Error)
        assertTrue(controller.state.value.shelves is HomeSectionState.Loaded)
    }

    @Test
    fun `show closed change reloads only recent reading`() = runTest {
        val client = FakeAuthenticatedClient()
        val controller = HomeController(provider(client), this)
        controller.initialize(profile())
        advanceUntilIdle()

        controller.setShowClosedSessions(true)
        advanceUntilIdle()
        controller.setShowClosedSessions(true)
        advanceUntilIdle()

        assertEquals(listOf(false, true), client.recentRequests.map { it.includeClosed })
        assertEquals(1, client.shelfRequests.size)
        assertTrue(controller.state.value.showClosedSessions)
    }

    @Test
    fun `section retries remain focused`() = runTest {
        val client = FakeAuthenticatedClient().apply {
            recentFailure = SplClientException.ServerUnreachable()
            shelfFailure = SplClientException.AuthenticatedRequestFailed()
        }
        val controller = HomeController(provider(client), this)
        controller.initialize(profile())
        advanceUntilIdle()

        client.recentFailure = null
        controller.retryRecentReading()
        advanceUntilIdle()
        assertTrue(controller.state.value.recentReading is HomeSectionState.Empty)
        assertTrue(controller.state.value.shelves is HomeSectionState.Error)
        assertEquals(2, client.recentRequests.size)
        assertEquals(1, client.shelfRequests.size)

        client.shelfFailure = null
        controller.retryShelves()
        advanceUntilIdle()
        assertTrue(controller.state.value.shelves is HomeSectionState.Empty)
        assertEquals(2, client.shelfRequests.size)
    }

    @Test
    fun `search emits a Library routing intent without data access`() = runTest {
        val client = FakeAuthenticatedClient()
        val controller = HomeController(provider(client), this)
        val intent = async { controller.navigation.first() }
        runCurrent()

        controller.searchLibrary("octavia butler")

        assertEquals(HomeNavigationIntent.LibrarySearch("octavia butler"), intent.await())
        assertTrue(client.recentRequests.isEmpty())
        assertTrue(client.shelfRequests.isEmpty())
    }

    private class FakeAuthenticatedClient : AuthenticatedSecondPassClient {
        val recentRequests = mutableListOf<RecentReadingOptions>()
        val shelfRequests = mutableListOf<ShelfListOptions>()
        var recentResult: List<RecentReadingItem> = emptyList()
        var shelfResult: List<ShelfSummary> = emptyList()
        var recentFailure: Throwable? = null
        var shelfFailure: Throwable? = null

        override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> {
            recentRequests += options
            recentFailure?.let { throw it }
            return recentResult
        }

        override suspend fun listShelves(options: ShelfListOptions): ShelfPage {
            shelfRequests += options
            shelfFailure?.let { throw it }
            return ShelfPage(
                totalCount = shelfResult.size,
                hasNextPage = false,
                hasPreviousPage = false,
                shelves = shelfResult
            )
        }
    }

    private companion object {
        fun provider(client: AuthenticatedSecondPassClient) = object : AuthenticatedClientProvider {
            override suspend fun forProfile(
                profile: ConnectionProfile
            ): AuthenticatedSecondPassClient = client
        }

        fun recent(id: String) = RecentReadingItem(
            sessionId = id,
            sessionName = id,
            status = ReadingSessionStatus.ACTIVE,
            lastActivityAt = "2026-08-16T12:00:00Z",
            book = RecentReadingBook("book-$id", "Book $id", null, true),
            progress = null
        )

        fun shelf(id: String) = ShelfSummary(
            id = id,
            name = id,
            description = null,
            owner = ShelfOwner.User("profile-1", "reader", null, null),
            visibility = "private",
            itemCount = 1,
            canEdit = true,
            previewBooks = null
        )

        fun profile() = ConnectionProfile(
            serverOrigin = "https://library.example",
            serverBaseUrl = "https://library.example/",
            apiBaseUrl = "https://library.example/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "",
            clientSessionId = "session-1",
            clientName = "Tablet",
            clientType = "second-pass-android-client"
        )
    }
}
