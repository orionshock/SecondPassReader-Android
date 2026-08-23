package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
    fun `cached read precedes explicit refresh and success replaces it`() = runTest {
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

        val cached = repository.readCachedReadingHistory(account.scope, ACTIVE_ONLY)
        val refresh = async { repository.refreshReadingHistory(account, ACTIVE_ONLY) }
        runCurrent()

        assertEquals(listOf("cached"), cached.content?.items?.map { it.sessionId })
        assertEquals(HomeProjectionRefresh.Idle, cached.refresh)
        assertEquals(1, client.recentRequests.size)

        releaseRemote.complete(Unit)
        assertEquals(HomeProjectionRefresh.Current, refresh.await())
        val fresh = repository.readCachedReadingHistory(account.scope, ACTIVE_ONLY)

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

        val refresh = repository.refreshReadingHistory(account, ACTIVE_ONLY)
        val cached = repository.readCachedReadingHistory(account.scope, ACTIVE_ONLY)

        assertEquals(HomeProjectionRefresh.Current, refresh)
        assertEquals(emptyList<Any>(), cached.content?.items)
        assertEquals(NEW_FETCHED_AT, cached.content?.fetchedAt)
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

            val refresh = repository.refreshReadingHistory(account, ACTIVE_ONLY)
            val cached = repository.readCachedReadingHistory(account.scope, ACTIVE_ONLY)

            assertEquals(HomeProjectionRefresh.Failed(expected), refresh)
            assertEquals(listOf("cached"), cached.content?.items?.map { it.sessionId })
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

        val refresh = repository.refreshReadingHistory(account, ACTIVE_ONLY)
        val cached = repository.readCachedReadingHistory(account.scope, ACTIVE_ONLY)

        assertNull(cached.content)
        assertEquals(HomeProjectionRefresh.Failed(HomeProjectionFailure.Other), refresh)
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

        val first = async { repository.refreshReadingHistory(account, ACTIVE_ONLY) }
        runCurrent()
        val second = async { repository.refreshReadingHistory(account, ACTIVE_ONLY) }
        runCurrent()

        assertEquals(1, client.recentRequests.size)
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(first.await() is HomeProjectionRefresh.Current)
        assertTrue(second.await() is HomeProjectionRefresh.Current)
        assertEquals(1, store.recentReplacements)
    }

    private companion object {
        val ACTIVE_ONLY = HomeRecentReadingVariant.ActiveOnly
        val INCLUDING_CLOSED = HomeRecentReadingVariant.IncludingClosed
    }
}
