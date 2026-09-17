package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingHistoryProjectionRepositoryTest {
    @Test
    fun `cached projection is emitted before refresh and success returns persisted projection`() =
        runTest {
            val account = projectionAccount()
            val store = FakeHomeProjectionStore().apply {
                seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached")))
            }
            val releaseRemote = CompletableDeferred<Unit>()
            val client = FakeHomeAuthenticatedClient().apply {
                recentCall = {
                    releaseRemote.await()
                    listOf(recentItem("fresh-2"), recentItem("fresh-1"))
                }
            }
            val repository = homeRepository(store, client)

            val states = mutableListOf<HomeProjectionState<RecentReadingItem>>()
            val refresh =
                launch { repository.refreshReadingHistory(account, ACTIVE_ONLY).toList(states) }
            runCurrent()

            assertEquals(listOf("cached"), states[0].content?.items?.map { it.sessionId })
            assertEquals(HomeProjectionRefresh.Idle, states[0].refresh)
            assertEquals(HomeProjectionRefresh.Refreshing, states[1].refresh)
            assertEquals(1, client.recentRequests.size)

            releaseRemote.complete(Unit)
            refresh.join()
            val fresh = states.last()

            assertEquals(HomeProjectionRefresh.Current, fresh.refresh)
            assertEquals(listOf("fresh-2", "fresh-1"), fresh.content?.items?.map { it.sessionId })
            assertEquals(NEW_FETCHED_AT, fresh.content?.fetchedAt)
            assertEquals(1, store.recentReplacements)
        }

    @Test
    fun `successful empty response replaces old projection`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("stale")))
        }
        val repository = homeRepository(store, FakeHomeAuthenticatedClient())

        val refreshed = repository.refreshReadingHistory(account, ACTIVE_ONLY).toList().last()

        assertEquals(HomeProjectionRefresh.Current, refreshed.refresh)
        assertEquals(emptyList<Any>(), refreshed.content?.items)
        assertEquals(NEW_FETCHED_AT, refreshed.content?.fetchedAt)
        assertEquals(1, store.recentReplacements)
    }

    @Test
    fun `refresh failures retain cache and preserve useful classification`() = runTest {
        val failures =
            listOf(
                SplClientException.ServerUnreachable() to HomeProjectionFailure.Unreachable,
                SplClientException.ProtocolInvalid("recent reading") to
                    HomeProjectionFailure.ProtocolInvalid,
                SplClientException.AuthenticationRejected() to
                    HomeProjectionFailure.AuthenticationRejected
            )

        failures.forEach { (failure, expected) ->
            val account = projectionAccount(profileId = expected.name)
            val store = FakeHomeProjectionStore().apply {
                seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached")))
            }
            val client = FakeHomeAuthenticatedClient().apply {
                recentCall = { throw failure }
            }
            val repository = homeRepository(store, client)

            val refreshed = repository.refreshReadingHistory(account, ACTIVE_ONLY).toList().last()

            assertEquals(HomeProjectionRefresh.Failed(expected), refreshed.refresh)
            assertEquals(listOf("cached"), refreshed.content?.items?.map { it.sessionId })
            assertEquals(0, store.recentReplacements)
        }
    }

    @Test
    fun `failure without cache leaves content absent`() = runTest {
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = { throw IllegalStateException("unexpected") }
        }
        val account = projectionAccount()
        val repository = homeRepository(FakeHomeProjectionStore(), client)

        val refreshed = repository.refreshReadingHistory(account, ACTIVE_ONLY).toList().last()

        assertNull(refreshed.content)
        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.Other),
            refreshed.refresh
        )
    }

    @Test
    fun `reading variants and accounts remain isolated`() = runTest {
        val first = projectionAccount("profile-1")
        val second = projectionAccount("profile-2")
        val store = FakeHomeProjectionStore().apply {
            seedRecent(first, ACTIVE_ONLY, listOf(recentItem("first-active")))
            seedRecent(first, INCLUDING_CLOSED, listOf(recentItem("first-all")))
            seedRecent(second, ACTIVE_ONLY, listOf(recentItem("second-active")))
        }
        val repository = homeRepository(store, FakeHomeAuthenticatedClient())

        val firstActive = repository.readCachedReadingHistory(first.scope, ACTIVE_ONLY)
        val firstAll = repository.readCachedReadingHistory(first.scope, INCLUDING_CLOSED)
        val secondActive = repository.readCachedReadingHistory(second.scope, ACTIVE_ONLY)

        assertEquals(listOf("first-active"), firstActive.content?.items?.map { it.sessionId })
        assertEquals(listOf("first-all"), firstAll.content?.items?.map { it.sessionId })
        assertEquals(listOf("second-active"), secondActive.content?.items?.map { it.sessionId })
    }

    @Test
    fun `concurrent identical refreshes share one remote operation`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = {
                gate.await()
                listOf(recentItem("fresh"))
            }
        }
        val store = FakeHomeProjectionStore()
        val repository = homeRepository(store, client)
        val account = projectionAccount()

        val first = async { repository.refreshReadingHistory(account, ACTIVE_ONLY).toList().last() }
        runCurrent()
        val second =
            async { repository.refreshReadingHistory(account, ACTIVE_ONLY).toList().last() }
        runCurrent()

        assertEquals(1, client.recentRequests.size)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(HomeProjectionRefresh.Current, first.await().refresh)
        assertEquals(HomeProjectionRefresh.Current, second.await().refresh)
        assertEquals(1, store.recentReplacements)
    }

    private companion object {
        val ACTIVE_ONLY = HomeRecentReadingVariant.ActiveOnly
        val INCLUDING_CLOSED = HomeRecentReadingVariant.IncludingClosed
    }
}
