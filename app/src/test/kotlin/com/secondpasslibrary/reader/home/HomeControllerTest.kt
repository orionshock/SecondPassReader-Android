package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeControllerTest {
    @Test
    fun `cached sections remain visible while independent refreshes run`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached-reading")))
            seedShelves(account, listOf(shelfItem("cached-shelf")))
        }
        val recentGate = CompletableDeferred<Unit>()
        val shelfGate = CompletableDeferred<Unit>()
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = {
                recentGate.await()
                listOf(recentItem("fresh-reading"))
            }
            shelfCall = {
                shelfGate.await()
                listOf(shelfItem("fresh-shelf"))
            }
        }
        val controller = controller(store, client)

        controller.initialize(account.profile, account.profileId)
        runCurrent()

        assertEquals(
            listOf("cached-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        assertEquals(
            HomeProjectionRefresh.Refreshing,
            controller.state.value.recentReading.refresh
        )
        assertEquals(
            listOf("cached-shelf"),
            controller.state.value.shelves.content?.items?.map { it.id }
        )
        assertEquals(HomeProjectionRefresh.Refreshing, controller.state.value.shelves.refresh)

        recentGate.complete(Unit)
        runCurrent()
        assertEquals(
            listOf("fresh-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        assertEquals(
            listOf("cached-shelf"),
            controller.state.value.shelves.content?.items?.map { it.id }
        )
        shelfGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `cached failures remain visible and independently classified`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached-reading")))
            seedShelves(account, listOf(shelfItem("cached-shelf")))
        }
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = { throw SplClientException.ServerUnreachable() }
            shelfCall = { throw SplClientException.ProtocolInvalid("shelves") }
        }
        val controller = controller(store, client)

        controller.initialize(account.profile, account.profileId)
        advanceUntilIdle()

        assertEquals(
            listOf("cached-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.Unreachable),
            controller.state.value.recentReading.refresh
        )
        assertEquals(
            listOf("cached-shelf"),
            controller.state.value.shelves.content?.items?.map { it.id }
        )
        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.ProtocolInvalid),
            controller.state.value.shelves.refresh
        )
    }

    @Test
    fun `no cache progresses from loading to blocking error`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = {
                gate.await()
                throw SplClientException.ServerUnreachable()
            }
        }
        val account = projectionAccount()
        val controller = controller(FakeHomeProjectionStore(), client)

        controller.initialize(account.profile, account.profileId)
        runCurrent()
        assertNull(controller.state.value.recentReading.content)
        assertEquals(
            HomeProjectionRefresh.Refreshing,
            controller.state.value.recentReading.refresh
        )

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(controller.state.value.recentReading.content)
        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.Unreachable),
            controller.state.value.recentReading.refresh
        )
    }

    @Test
    fun `successful empty projection is content rather than missing cache`() = runTest {
        val account = projectionAccount()
        val controller = controller(FakeHomeProjectionStore(), FakeHomeAuthenticatedClient())

        controller.initialize(account.profile, account.profileId)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), controller.state.value.recentReading.content?.items)
        assertEquals(HomeProjectionRefresh.Current, controller.state.value.recentReading.refresh)
        assertEquals(emptyList<Any>(), controller.state.value.shelves.content?.items)
    }

    @Test
    fun `show closed selects its own cache and refreshes only recent reading`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, INCLUDING_CLOSED, listOf(recentItem("cached-closed")))
        }
        val closedGate = CompletableDeferred<Unit>()
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = { options ->
                if (options.includeClosed) closedGate.await()
                emptyList()
            }
        }
        val controller = controller(store, client)
        controller.initialize(account.profile, account.profileId)
        advanceUntilIdle()

        controller.setShowClosedSessions(true)
        runCurrent()

        assertEquals(
            listOf("cached-closed"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        assertEquals(false, client.recentRequests.first().includeClosed)
        assertEquals(true, client.recentRequests.last().includeClosed)
        assertEquals(1, client.shelfRequests.size)
        closedGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `retries stay focused and repeated initialization does not reload`() = runTest {
        val account = projectionAccount()
        val client = FakeHomeAuthenticatedClient()
        val controller = controller(FakeHomeProjectionStore(), client)
        controller.initialize(account.profile, account.profileId)
        advanceUntilIdle()

        controller.initialize(account.profile, account.profileId)
        controller.retryRecentReading()
        advanceUntilIdle()

        assertEquals(2, client.recentRequests.size)
        assertEquals(1, client.shelfRequests.size)

        controller.retryShelves()
        advanceUntilIdle()
        assertEquals(2, client.shelfRequests.size)
    }

    @Test
    fun `authentication rejection emits one connection event and is not offline`() = runTest {
        val account = projectionAccount()
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = { throw SplClientException.AuthenticationRejected() }
            shelfCall = { throw SplClientException.AuthenticationRejected() }
        }
        val controller = controller(FakeHomeProjectionStore(), client)
        val event = async { controller.connectionEvents.first() }
        runCurrent()

        controller.initialize(account.profile, account.profileId)
        advanceUntilIdle()

        assertEquals(HomeConnectionEvent.AuthenticationRejected, event.await())
        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.AuthenticationRejected),
            controller.state.value.recentReading.refresh
        )
        assertTrue(
            controller.state.value.recentReading.refresh !=
                HomeProjectionRefresh.Failed(HomeProjectionFailure.Unreachable)
        )
    }

    @Test
    fun `search emits Library navigation without loading Home`() = runTest {
        val controller = controller(FakeHomeProjectionStore(), FakeHomeAuthenticatedClient())
        val intent = async { controller.navigation.first() }
        runCurrent()

        controller.searchLibrary("octavia butler")

        assertEquals(HomeNavigationIntent.LibrarySearch("octavia butler"), intent.await())
    }

    private fun TestScope.controller(
        store: FakeHomeProjectionStore,
        client: FakeHomeAuthenticatedClient
    ) = HomeController(homeRepository(store, client), this)

    private companion object {
        val ACTIVE_ONLY = HomeRecentReadingVariant.ActiveOnly
        val INCLUDING_CLOSED = HomeRecentReadingVariant.IncludingClosed
    }
}
