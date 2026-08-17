package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingHistoryProjectionRepositoryTest {
    @Test
    fun `cached content precedes remote completion and success replaces it`() = runTest {
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
        val states =
            mutableListOf<HomeProjectionState<com.secondpasslibrary.client.RecentReadingItem>>()
        val collection =
            async {
                homeRepository(store, client).readingHistory(account, ACTIVE_ONLY).toList(states)
            }

        runCurrent()

        assertEquals(listOf("cached"), states.first().content?.items?.map { it.sessionId })
        assertEquals(HomeProjectionRefresh.Idle, states.first().refresh)
        assertEquals(HomeProjectionRefresh.Refreshing, states[1].refresh)
        assertEquals(1, client.recentRequests.size)

        releaseRemote.complete(Unit)
        collection.await()

        assertEquals(
            listOf("fresh-2", "fresh-1"),
            states.last().content?.items?.map {
                it.sessionId
            }
        )
        assertEquals(NEW_FETCHED_AT, states.last().content?.fetchedAt)
        assertEquals(HomeProjectionRefresh.Current, states.last().refresh)
        assertEquals(1, store.recentReplacements)
    }

    @Test
    fun `successful empty response replaces old projection`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("stale")))
        }

        val states = homeRepository(store, FakeHomeAuthenticatedClient())
            .readingHistory(account, ACTIVE_ONLY).toList()

        assertEquals(emptyList<Any>(), states.last().content?.items)
        assertEquals(NEW_FETCHED_AT, states.last().content?.fetchedAt)
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

            val state = homeRepository(
                store,
                client
            ).readingHistory(account, ACTIVE_ONLY).toList().last()

            assertEquals(listOf("cached"), state.content?.items?.map { it.sessionId })
            assertEquals(HomeProjectionRefresh.Failed(expected), state.refresh)
            assertEquals(0, store.recentReplacements)
        }
    }

    @Test
    fun `failure without cache leaves content absent`() = runTest {
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = { throw IllegalStateException("unexpected") }
        }

        val state = homeRepository(FakeHomeProjectionStore(), client)
            .readingHistory(projectionAccount(), ACTIVE_ONLY).toList().last()

        assertNull(state.content)
        assertEquals(HomeProjectionRefresh.Failed(HomeProjectionFailure.Other), state.refresh)
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
        val client = FakeHomeAuthenticatedClient().apply {
            recentCall = { throw SplClientException.ServerUnreachable() }
        }
        val repository = homeRepository(store, client)

        val firstActive = repository.readingHistory(first, ACTIVE_ONLY).toList().last()
        val firstAll = repository.readingHistory(first, INCLUDING_CLOSED).toList().last()
        val secondActive = repository.readingHistory(second, ACTIVE_ONLY).toList().last()

        assertEquals(listOf("first-active"), firstActive.content?.items?.map { it.sessionId })
        assertEquals(listOf("first-all"), firstAll.content?.items?.map { it.sessionId })
        assertEquals(listOf("second-active"), secondActive.content?.items?.map { it.sessionId })
        assertEquals(listOf(false, true, false), client.recentRequests.map { it.includeClosed })
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

        val first = async { repository.readingHistory(account, ACTIVE_ONLY).toList() }
        runCurrent()
        val second = async { repository.readingHistory(account, ACTIVE_ONLY).toList() }
        runCurrent()

        assertEquals(1, client.recentRequests.size)
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(first.await().last().refresh is HomeProjectionRefresh.Current)
        assertTrue(second.await().last().refresh is HomeProjectionRefresh.Current)
        assertEquals(1, store.recentReplacements)
    }

    private companion object {
        val ACTIVE_ONLY = HomeRecentReadingVariant.ActiveOnly
        val INCLUDING_CLOSED = HomeRecentReadingVariant.IncludingClosed
    }
}
