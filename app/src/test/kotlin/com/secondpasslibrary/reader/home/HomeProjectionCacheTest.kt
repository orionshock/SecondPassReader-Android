package com.secondpasslibrary.reader.home

import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeProjectionCacheTest {
    @Test
    fun `no snapshot makes cache unavailable`() = runTest {
        val repository = homeRepository(FakeHomeProjectionStore(), FakeHomeAuthenticatedClient())

        assertFalse(repository.hasCachedProjection(projectionAccount().scope))
    }

    @Test
    fun `reading snapshot with data makes cache available`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, listOf(recentItem("cached")))
        }

        assertTrue(
            homeRepository(store, FakeHomeAuthenticatedClient())
                .hasCachedProjection(account.scope)
        )
    }

    @Test
    fun `authoritative empty reading snapshot makes cache available`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, emptyList())
        }

        assertTrue(
            homeRepository(store, FakeHomeAuthenticatedClient())
                .hasCachedProjection(account.scope)
        )
    }

    @Test
    fun `shelf snapshot with data makes cache available`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedShelves(account, listOf(shelfItem("cached")))
        }

        assertTrue(
            homeRepository(store, FakeHomeAuthenticatedClient())
                .hasCachedProjection(account.scope)
        )
    }

    @Test
    fun `authoritative empty shelf snapshot makes cache available`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply { seedShelves(account, emptyList()) }

        assertTrue(
            homeRepository(store, FakeHomeAuthenticatedClient())
                .hasCachedProjection(account.scope)
        )
    }

    @Test
    fun `cache eligibility is isolated by account scope`() = runTest {
        val cachedAccount = projectionAccount(profileId = "cached")
        val otherAccount = projectionAccount(profileId = "other")
        val store = FakeHomeProjectionStore().apply {
            seedRecent(cachedAccount, ACTIVE_ONLY, emptyList())
        }
        val repository = homeRepository(store, FakeHomeAuthenticatedClient())

        assertTrue(repository.hasCachedProjection(cachedAccount.scope))
        assertFalse(repository.hasCachedProjection(otherAccount.scope))
    }

    @Test
    fun `cached-only reads do not acquire authenticated client`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, ACTIVE_ONLY, emptyList())
            seedShelves(account, emptyList())
        }
        val provider = FakeHomeAuthenticatedClientProvider(FakeHomeAuthenticatedClient())
        val repository = homeRepository(store, provider)

        repository.hasCachedProjection(account.scope)
        repository.readCachedReadingHistory(account.scope, ACTIVE_ONLY)
        repository.readCachedShelves(account.scope)

        assertEquals(0, provider.accessCount)
    }

    @Test
    fun `cached-only read distinguishes missing from authoritative empty snapshot`() = runTest {
        val missing = projectionAccount(profileId = "missing")
        val empty = projectionAccount(profileId = "empty")
        val store = FakeHomeProjectionStore().apply {
            seedRecent(empty, ACTIVE_ONLY, emptyList())
        }
        val repository = homeRepository(store, FakeHomeAuthenticatedClient())

        val missingState = repository.readCachedReadingHistory(missing.scope, ACTIVE_ONLY)
        val emptyState = repository.readCachedReadingHistory(empty.scope, ACTIVE_ONLY)

        assertNull(missingState.content)
        assertNotNull(emptyState.content)
        assertEquals(emptyList<Any>(), emptyState.content?.items)
    }

    private companion object {
        val ACTIVE_ONLY = HomeRecentReadingVariant.ActiveOnly
    }
}
