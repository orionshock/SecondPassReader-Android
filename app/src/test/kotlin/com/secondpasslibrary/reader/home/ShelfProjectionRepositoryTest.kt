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

        val states = homeRepository(store, client).shelves(account).toList()

        assertEquals(listOf("cached"), states.first().content?.items?.map { it.id })
        assertEquals(listOf("fresh-2", "fresh-1"), states.last().content?.items?.map { it.id })
        assertEquals(NEW_FETCHED_AT, states.last().content?.fetchedAt)
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

        val shelves = repository.shelves(account).toList().last()
        val reading = repository.readingHistory(
            account,
            HomeRecentReadingVariant.ActiveOnly
        ).toList().last()

        assertEquals(listOf("cached-shelf"), shelves.content?.items?.map { it.id })
        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.Unreachable),
            shelves.refresh
        )
        assertEquals(listOf("fresh-reading"), reading.content?.items?.map { it.sessionId })
        assertEquals(HomeProjectionRefresh.Current, reading.refresh)
        assertEquals(0, store.shelfReplacements)
        assertEquals(1, store.recentReplacements)
    }
}
