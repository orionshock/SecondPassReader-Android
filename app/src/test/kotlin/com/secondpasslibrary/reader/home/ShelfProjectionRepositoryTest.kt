package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
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

        val cached = repository.readCachedShelves(account.scope)
        val refresh = repository.refreshShelves(account)
        val fresh = repository.readCachedShelves(account.scope)

        assertEquals(listOf("cached"), cached.content?.items?.map { it.id })
        assertEquals(HomeProjectionRefresh.Current, refresh)
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

        val shelfRefresh = repository.refreshShelves(account)
        val readingRefresh =
            repository.refreshReadingHistory(account, HomeRecentReadingVariant.ActiveOnly)
        val shelves = repository.readCachedShelves(account.scope)
        val reading =
            repository.readCachedReadingHistory(
                account.scope,
                HomeRecentReadingVariant.ActiveOnly
            )

        assertEquals(
            HomeProjectionRefresh.Failed(HomeProjectionFailure.Unreachable),
            shelfRefresh
        )
        assertEquals(HomeProjectionRefresh.Current, readingRefresh)
        assertEquals(listOf("cached-shelf"), shelves.content?.items?.map { it.id })
        assertEquals(listOf("fresh-reading"), reading.content?.items?.map { it.sessionId })
        assertEquals(0, store.shelfReplacements)
        assertEquals(1, store.recentReplacements)
    }
}
