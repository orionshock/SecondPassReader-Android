package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfProjectionRepositoryTest {
    @Test
    fun `shelf cache refresh uses Home bounds and preserves server order`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedShelves(account, listOf(shelfItem("cached")))
        }
        val client = FakeHomeAuthenticatedClient().apply {
            shelfCall = { listOf(shelfItem("fresh-2"), shelfItem("fresh-1")) }
        }
        val repository = homeRepository(store, client)

        val states = repository.refreshShelves(account).toList()
        val cached = states.first()
        val fresh = states.last()

        assertEquals(listOf("cached"), cached.content?.items?.map { it.id })
        assertEquals(HomeProjectionRefresh.Idle, cached.refresh)
        assertEquals(HomeProjectionRefresh.Refreshing, states[1].refresh)
        assertEquals(HomeProjectionRefresh.Current, fresh.refresh)
        assertEquals(listOf("fresh-2", "fresh-1"), fresh.content?.items?.map { it.id })
        assertEquals(NEW_FETCHED_AT, fresh.content?.fetchedAt)
        assertEquals(1, store.shelfReplacements)
        assertEquals(1, client.shelfRequests.single().page)
        assertEquals(6, client.shelfRequests.single().pageSize)
        assertEquals(3, client.shelfRequests.single().previewLimit)
        assertEquals(null, client.shelfRequests.single().ordering)
    }

    @Test
    fun `shelf failure remains independent from successful reading refresh`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedShelves(account, listOf(shelfItem("cached-shelf")))
            seedRecent(
                account,
                HomeRecentReadingVariant.ActiveOnly,
                listOf(recentItem("kept-reading"))
            )
        }
        val client = FakeHomeAuthenticatedClient().apply {
            shelfCall = { throw SplClientException.ServerUnreachable() }
            recentCall = { listOf(recentItem("fresh-reading")) }
        }
        val repository = homeRepository(store, client)

        val shelves = repository.refreshShelves(account).toList().last()
        val reading =
            repository.refreshReadingHistory(account, HomeRecentReadingVariant.ActiveOnly)
                .toList()
                .last()

        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.Unreachable),
            shelves.refresh
        )
        assertEquals(HomeProjectionRefresh.Current, reading.refresh)
        assertEquals(listOf("cached-shelf"), shelves.content?.items?.map { it.id })
        assertEquals(listOf("fresh-reading"), reading.content?.items?.map { it.sessionId })
        assertEquals(0, store.shelfReplacements)
        assertEquals(1, store.recentReplacements)
    }
}
