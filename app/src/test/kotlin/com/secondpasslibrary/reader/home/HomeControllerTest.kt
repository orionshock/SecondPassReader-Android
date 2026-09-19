package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
    fun `offline transition cancels online Home work and retains cached content`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached-reading")))
        }
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val remote = CompletableDeferred<Unit>()
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = {
                entered.complete(Unit)
                try {
                    remote.await()
                    listOf(recentItem("remote-reading"))
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val controller = controller(store, client)
        controller.initializeCached(account.scope)
        controller.provideVerifiedAuthority(account.profile, account.profileId)
        entered.await()

        controller.updateAppAvailability(
            AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
        )
        advanceUntilIdle()

        assertTrue(cancelled.isCompleted)
        assertTrue(controller.state.value.offline)
        assertEquals(
            listOf("cached-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
    }

    @Test
    fun `offline cached reading marks only locally completed Books readable`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(
                account,
                ACTIVE_ONLY,
                listOf(recentItem("downloaded"), recentItem("remote-only"))
            )
        }
        val controller =
            HomeController(
                homeRepository(store, FakeHomeAuthenticatedClient()),
                this,
                localBookAvailable = { _, bookId -> bookId == "book-downloaded" }
            )

        controller.initializeCached(account.scope)
        controller.updateAppAvailability(
            AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
        )
        advanceUntilIdle()

        assertTrue(controller.state.value.offline)
        assertEquals(setOf("book-downloaded"), controller.state.value.locallyReadableBookIds)

        controller.updateAppAvailability(AppAvailability.Online)
        advanceUntilIdle()

        assertTrue(!controller.state.value.offline)
        assertTrue(controller.state.value.locallyReadableBookIds.isEmpty())
    }

    @Test
    fun `cached-only initialization reads sections without authenticated client access`() =
        runTest {
            val account = projectionAccount()
            val store = FakeHomeProjectionStore().apply {
                seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached-reading")))
                seedShelves(account, listOf(shelfItem("cached-shelf")))
            }
            val provider = FakeHomeAuthenticatedClientProvider(FakeHomeAuthenticatedClient())
            val controller = HomeController(homeRepository(store, provider), this)

            controller.initializeCached(account.scope)
            advanceUntilIdle()

            assertEquals(0, provider.accessCount)
            assertEquals(
                listOf("cached-reading"),
                controller.state.value.recentReading.content?.items?.map { it.sessionId }
            )
            assertEquals(HomeProjectionRefresh.Idle, controller.state.value.recentReading.refresh)
            assertEquals(
                listOf("cached-shelf"),
                controller.state.value.shelves.content?.items?.map { it.id }
            )
        }

    @Test
    fun `verified authority refreshes the existing cached state without resetting it`() = runTest {
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
        controller.initializeCached(account.scope)
        advanceUntilIdle()

        controller.provideVerifiedAuthority(account.profile, account.profileId)
        runCurrent()

        assertEquals(
            listOf("cached-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        assertEquals(HomeProjectionRefresh.Refreshing, controller.state.value.recentReading.refresh)
        assertEquals(
            listOf("cached-shelf"),
            controller.state.value.shelves.content?.items?.map { it.id }
        )

        recentGate.complete(Unit)
        shelfGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `authority removal cancels refresh and returns Home to cached-only behavior`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached-reading")))
            seedShelves(account, listOf(shelfItem("cached-shelf")))
        }
        val gate = CompletableDeferred<Unit>()
        val provider =
            FakeHomeAuthenticatedClientProvider(
                FakeHomeAuthenticatedClient().apply {
                    recentCall = {
                        gate.await()
                        emptyList()
                    }
                    shelfCall = {
                        gate.await()
                        emptyList()
                    }
                }
            )
        val controller = HomeController(homeRepository(store, provider), this)
        controller.provideVerifiedAuthority(account.profile, account.profileId)
        runCurrent()
        val authenticatedAccesses = provider.accessCount

        controller.initializeCached(account.scope)
        advanceUntilIdle()
        controller.retryRecentReading()
        controller.retryShelves()
        advanceUntilIdle()

        assertEquals(authenticatedAccesses, provider.accessCount)
        assertEquals(
            listOf("cached-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        assertEquals(HomeProjectionRefresh.Idle, controller.state.value.recentReading.refresh)
        assertEquals(
            listOf("cached-shelf"),
            controller.state.value.shelves.content?.items?.map { it.id }
        )
        assertEquals(HomeProjectionRefresh.Idle, controller.state.value.shelves.refresh)
    }

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

        controller.provideVerifiedAuthority(account.profile, account.profileId)
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
    fun `cached refresh reports ambient syncing then reachable without hiding content`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached-reading")))
            seedShelves(account, listOf(shelfItem("cached-shelf")))
        }
        val recentGate = CompletableDeferred<Unit>()
        val shelfGate = CompletableDeferred<Unit>()
        val controller =
            controller(
                store,
                FakeHomeAuthenticatedClient().apply {
                    recentCall = {
                        recentGate.await()
                        listOf(recentItem("fresh-reading"))
                    }
                    shelfCall = {
                        shelfGate.await()
                        listOf(shelfItem("fresh-shelf"))
                    }
                }
            )
        val availability = async { controller.refreshAvailability.take(2).toList() }
        runCurrent()

        controller.provideVerifiedAuthority(account.profile, account.profileId)
        runCurrent()

        assertEquals(
            listOf("cached-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        recentGate.complete(Unit)
        shelfGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf(HomeRefreshAvailability.REFRESHING, HomeRefreshAvailability.REACHABLE),
            availability.await()
        )
        assertEquals(
            listOf("fresh-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
    }

    @Test
    fun `unreachable refresh retains cached Home and reports ambient offline`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached-reading")))
            seedShelves(account, listOf(shelfItem("cached-shelf")))
        }
        val controller =
            controller(
                store,
                FakeHomeAuthenticatedClient().apply {
                    recentCall = { throw SplClientException.ServerUnreachable() }
                    shelfCall = { throw SplClientException.ServerUnreachable() }
                }
            )
        val availability = async { controller.refreshAvailability.take(2).toList() }
        runCurrent()

        controller.provideVerifiedAuthority(account.profile, account.profileId)
        advanceUntilIdle()

        assertEquals(
            listOf(HomeRefreshAvailability.REFRESHING, HomeRefreshAvailability.UNREACHABLE),
            availability.await()
        )
        assertEquals(
            listOf("cached-reading"),
            controller.state.value.recentReading.content?.items?.map { it.sessionId }
        )
        assertEquals(
            listOf("cached-shelf"),
            controller.state.value.shelves.content?.items?.map { it.id }
        )
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

        controller.provideVerifiedAuthority(account.profile, account.profileId)
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

        controller.provideVerifiedAuthority(account.profile, account.profileId)
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

        controller.provideVerifiedAuthority(account.profile, account.profileId)
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
        controller.provideVerifiedAuthority(account.profile, account.profileId)
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
        controller.provideVerifiedAuthority(account.profile, account.profileId)
        advanceUntilIdle()

        controller.provideVerifiedAuthority(account.profile, account.profileId)
        controller.retryRecentReading()
        advanceUntilIdle()

        assertEquals(2, client.recentRequests.size)
        assertEquals(1, client.shelfRequests.size)

        controller.retryShelves()
        advanceUntilIdle()
        assertEquals(2, client.shelfRequests.size)
    }

    @Test
    fun `account profile remains an independent Home reset key`() = runTest {
        val account = projectionAccount()
        val client = FakeHomeAuthenticatedClient()
        val controller = controller(FakeHomeProjectionStore(), client)
        controller.provideVerifiedAuthority(account.profile, account.profileId)
        advanceUntilIdle()

        controller.provideVerifiedAuthority(account.profile, "profile-2")
        advanceUntilIdle()

        assertEquals(2, client.recentRequests.size)
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

        controller.provideVerifiedAuthority(account.profile, account.profileId)
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

        controller.navigate(HomeNavigationIntent.LibrarySearch("octavia butler"))

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
