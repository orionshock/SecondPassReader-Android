package com.secondpasslibrary.reader.app

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.AuthenticatedServerInfo
import com.secondpasslibrary.client.CurrentUser
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.LocalAccountContext
import com.secondpasslibrary.reader.connection.PersistedAccountContext
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.home.FakeHomeAuthenticatedClient
import com.secondpasslibrary.reader.home.FakeHomeAuthenticatedClientProvider
import com.secondpasslibrary.reader.home.FakeHomeProjectionStore
import com.secondpasslibrary.reader.home.HomeRefreshAvailability
import com.secondpasslibrary.reader.home.homeRepository
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projectionAccount
import com.secondpasslibrary.reader.home.recentItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionControllerTest {
    @Test
    fun `no local account remains in resolving connection presentation`() = runTest {
        val controller = controller(FakeHomeProjectionStore())

        controller.updateConnection(ConnectionUiState.Restoring, null)

        assertEquals(AppSessionState.Resolving, controller.state.value)
    }

    @Test
    fun `local account without Home cache preserves blocking restore`() = runTest {
        val account = projectionAccount()
        val controller = controller(FakeHomeProjectionStore())

        controller.updateConnection(ConnectionUiState.Restoring, account.localContext())
        advanceUntilIdle()

        assertEquals(
            AppSessionState.ConnectionRequired(ConnectionUiState.Restoring),
            controller.state.value
        )
    }

    @Test
    fun `local account with Home cache admits restoring account shell`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(
                account,
                HomeRecentReadingVariant.ActiveOnly,
                listOf(recentItem("cached"))
            )
        }
        val provider = FakeHomeAuthenticatedClientProvider(FakeHomeAuthenticatedClient())
        val controller = AppSessionController(homeRepository(store, provider), this)

        controller.updateConnection(ConnectionUiState.Restoring, account.localContext())
        advanceUntilIdle()

        val shell = controller.state.value as AppSessionState.AccountShell
        assertEquals(account.profile, shell.profile)
        assertEquals(account.profileId, shell.profileId)
        assertEquals(AppSessionAuthority.Restoring, shell.authority)
        assertEquals(AppAvailability.Syncing, shell.availability)
        assertNull(shell.authenticatedFeatureContext)
        assertEquals(0, provider.accessCount)
    }

    @Test
    fun `verification updates existing shell identity with verified authority`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, HomeRecentReadingVariant.ActiveOnly, emptyList())
        }
        val controller = controller(store)
        controller.updateConnection(ConnectionUiState.Restoring, account.localContext())
        advanceUntilIdle()
        val cachedShell = controller.state.value as AppSessionState.AccountShell
        val verifiedContext = authenticatedContext(account.profileId)

        controller.updateConnection(
            ConnectionUiState.Linked(account.profile, verifiedContext),
            account.localContext()
        )

        val verifiedShell = controller.state.value as AppSessionState.AccountShell
        assertSame(cachedShell.profile, verifiedShell.profile)
        assertEquals(cachedShell.profileId, verifiedShell.profileId)
        assertEquals(AppSessionAuthority.Verified(verifiedContext), verifiedShell.authority)
        assertEquals(AppAvailability.Online, verifiedShell.availability)
        assertSame(verifiedContext, verifiedShell.authenticatedFeatureContext)
    }

    @Test
    fun `transient restore failure retains eligible cached shell`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, HomeRecentReadingVariant.ActiveOnly, emptyList())
        }
        val controller = controller(store)

        controller.updateConnection(
            ConnectionUiState.RestoreProblem(account.profile, "Server unavailable"),
            account.localContext()
        )
        advanceUntilIdle()

        val shell = controller.state.value as AppSessionState.AccountShell
        assertEquals(AppSessionAuthority.TransientFailure("Server unavailable"), shell.authority)
        assertEquals(
            AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE),
            shell.availability
        )
        assertNull(shell.authenticatedFeatureContext)
    }

    @Test
    fun `rejected authority retains eligible cached shell and disables network features`() =
        runTest {
            val account = projectionAccount()
            val store = FakeHomeProjectionStore().apply {
                seedRecent(account, HomeRecentReadingVariant.ActiveOnly, emptyList())
            }
            val controller = controller(store)

            controller.updateConnection(
                ConnectionUiState.AuthenticationRequired(account.profile, "Link again"),
                account.localContext()
            )
            advanceUntilIdle()

            val shell = controller.state.value as AppSessionState.AccountShell
            assertEquals(AppSessionAuthority.AuthenticationRequired("Link again"), shell.authority)
            assertEquals(
                AppAvailability.Offline(AppAvailabilityReason.AUTHENTICATION_REQUIRED),
                shell.availability
            )
            assertNull(shell.authenticatedFeatureContext)
        }

    @Test
    fun `relink progress retains the same eligible account shell`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, HomeRecentReadingVariant.ActiveOnly, emptyList())
        }
        val controller = controller(store)
        val pairing = ConnectionUiState.VerifyingServer(account.profile.serverOrigin)

        controller.updateConnection(pairing, account.localContext())
        advanceUntilIdle()

        val shell = controller.state.value as AppSessionState.AccountShell
        assertEquals(AppSessionAuthority.Healing(pairing), shell.authority)
        assertEquals(account.profileId, shell.profileId)
        assertNull(shell.authenticatedFeatureContext)
    }

    @Test
    fun `online to offline transition retains matching screen context only`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore().apply {
            seedRecent(account, HomeRecentReadingVariant.ActiveOnly, emptyList())
        }
        val controller = controller(store)
        val context = authenticatedContext(account.profileId)
        controller.updateConnection(
            ConnectionUiState.Linked(account.profile, context),
            account.localContext()
        )

        controller.updateConnection(
            ConnectionUiState.RestoreProblem(account.profile, "Server unavailable"),
            account.localContext()
        )
        advanceUntilIdle()

        val shell = controller.state.value as AppSessionState.AccountShell
        assertSame(context, shell.authenticatedFeatureContext)
        assertEquals(
            AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE),
            shell.availability
        )
    }

    @Test
    fun `admitted shell never returns to resolving during same-account restore`() = runTest {
        val account = projectionAccount()
        val store = FakeHomeProjectionStore()
        val controller = controller(store)
        val context = authenticatedContext(account.profileId)
        controller.updateConnection(
            ConnectionUiState.Linked(account.profile, context),
            account.localContext()
        )
        store.hasSnapshotCall = { error("An admitted shell must not re-run cache admission") }

        controller.updateConnection(ConnectionUiState.Restoring, account.localContext())
        runCurrent()

        val shell = controller.state.value as AppSessionState.AccountShell
        assertEquals(AppSessionAuthority.Restoring, shell.authority)
        assertEquals(AppAvailability.Syncing, shell.availability)
        assertSame(context, shell.authenticatedFeatureContext)
    }

    @Test
    fun `temporary missing local descriptor does not evict admitted shell`() = runTest {
        val account = projectionAccount()
        val controller = controller(FakeHomeProjectionStore())
        val context = authenticatedContext(account.profileId)
        controller.updateConnection(
            ConnectionUiState.Linked(account.profile, context),
            account.localContext()
        )

        controller.updateConnection(
            ConnectionUiState.VerifyingServer(account.profile.serverOrigin),
            null
        )

        val shell = controller.state.value as AppSessionState.AccountShell
        assertEquals(account.profileId, shell.profileId)
        assertTrue(shell.authority is AppSessionAuthority.Healing)
        assertEquals(AppAvailability.Syncing, shell.availability)
        assertSame(context, shell.authenticatedFeatureContext)
    }

    @Test
    fun `different account descriptor does not retain admitted shell`() = runTest {
        val first = projectionAccount(profileId = "first")
        val second = projectionAccount(profileId = "second")
        val eligibility = CompletableDeferred<Boolean>()
        val store = FakeHomeProjectionStore().apply {
            hasSnapshotCall = { eligibility.await() }
        }
        val controller = controller(store)
        controller.updateConnection(
            ConnectionUiState.Linked(first.profile, authenticatedContext(first.profileId)),
            first.localContext()
        )

        controller.updateConnection(ConnectionUiState.Restoring, second.localContext())
        runCurrent()

        assertEquals(AppSessionState.Resolving, controller.state.value)
        eligibility.complete(true)
        advanceUntilIdle()
        val shell = controller.state.value as AppSessionState.AccountShell
        assertEquals(second.profileId, shell.profileId)
    }

    @Test
    fun `explicit connection exit removes admitted shell`() = runTest {
        val account = projectionAccount()
        val controller = controller(FakeHomeProjectionStore())
        controller.updateConnection(
            ConnectionUiState.Linked(account.profile, authenticatedContext(account.profileId)),
            account.localContext()
        )
        val entry = ConnectionUiState.ServerEntry(message = "Logged out")

        controller.updateConnection(entry, null)

        assertEquals(AppSessionState.ConnectionRequired(entry), controller.state.value)
    }

    @Test
    fun `Home refresh availability updates ambient state without replacing cached shell`() =
        runTest {
            val account = projectionAccount()
            val store = FakeHomeProjectionStore().apply {
                seedRecent(
                    account,
                    HomeRecentReadingVariant.ActiveOnly,
                    listOf(recentItem("cached"))
                )
            }
            val controller = controller(store)
            val context = authenticatedContext(account.profileId)
            controller.updateConnection(
                ConnectionUiState.Linked(account.profile, context),
                account.localContext()
            )
            val initial = controller.state.value as AppSessionState.AccountShell

            controller.updateHomeRefreshAvailability(HomeRefreshAvailability.REFRESHING)
            val syncing = controller.state.value as AppSessionState.AccountShell
            controller.updateHomeRefreshAvailability(HomeRefreshAvailability.UNREACHABLE)
            val offline = controller.state.value as AppSessionState.AccountShell
            controller.updateHomeRefreshAvailability(HomeRefreshAvailability.REACHABLE)
            val online = controller.state.value as AppSessionState.AccountShell

            assertSame(initial.profile, syncing.profile)
            assertEquals(AppAvailability.Syncing, syncing.availability)
            assertSame(initial.profile, offline.profile)
            assertEquals(
                AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE),
                offline.availability
            )
            assertSame(initial.profile, online.profile)
            assertEquals(AppAvailability.Online, online.availability)
        }

    @Test
    fun `transient restore failure without cache remains connection owned`() = runTest {
        val account = projectionAccount()
        val problem = ConnectionUiState.RestoreProblem(account.profile, "Server unavailable")
        val controller = controller(FakeHomeProjectionStore())

        controller.updateConnection(problem, account.localContext())
        advanceUntilIdle()

        assertEquals(AppSessionState.ConnectionRequired(problem), controller.state.value)
    }

    @Test
    fun `transient restore failure without local account remains connection owned`() = runTest {
        val account = projectionAccount()
        val problem = ConnectionUiState.RestoreProblem(account.profile, "Server unavailable")
        val controller = controller(FakeHomeProjectionStore())

        controller.updateConnection(problem, null)

        assertEquals(AppSessionState.ConnectionRequired(problem), controller.state.value)
    }

    @Test
    fun `first-time pairing state remains connection owned`() = runTest {
        val controller = controller(FakeHomeProjectionStore())
        val entry = ConnectionUiState.ServerEntry()

        controller.updateConnection(entry, null)

        assertEquals(AppSessionState.ConnectionRequired(entry), controller.state.value)
    }

    @Test
    fun `verified authority admits network features without cache or local descriptor`() = runTest {
        val account = projectionAccount()
        val verifiedContext = authenticatedContext(account.profileId)
        val controller = controller(FakeHomeProjectionStore())

        controller.updateConnection(
            ConnectionUiState.Linked(account.profile, verifiedContext),
            null
        )

        val shell = controller.state.value as AppSessionState.AccountShell
        assertSame(verifiedContext, shell.authenticatedFeatureContext)
    }

    @Test
    fun `stale cache result cannot admit the wrong account shell`() = runTest {
        val first = projectionAccount(profileId = "first")
        val second = projectionAccount(profileId = "second")
        val firstResult = CompletableDeferred<Boolean>()
        val store = FakeHomeProjectionStore().apply {
            hasSnapshotCall = { key ->
                if (key == first.scope.storageKey) firstResult.await() else true
            }
        }
        val controller = controller(store)

        controller.updateConnection(ConnectionUiState.Restoring, first.localContext())
        runCurrent()
        controller.updateConnection(ConnectionUiState.Restoring, second.localContext())
        advanceUntilIdle()
        firstResult.complete(true)
        advanceUntilIdle()

        val shell = controller.state.value as AppSessionState.AccountShell
        assertEquals("second", shell.profileId)
        assertTrue(shell.authority is AppSessionAuthority.Restoring)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(store: FakeHomeProjectionStore) =
        AppSessionController(homeRepository(store, FakeHomeAuthenticatedClient()), this)

    private fun com.secondpasslibrary.reader.home.HomeProjectionAccount.localContext() =
        LocalAccountContext(
            profile,
            PersistedAccountContext(profile.authenticatedConnectionIdentity, profileId)
        )

    private fun authenticatedContext(profileId: String) = AuthenticatedContext(
        CurrentUser("reader", "", "", "", profileId, "reader", emptyList(), null, null, null),
        AuthenticatedServerInfo("Library", "", "", false, null, "", null, "1.0", "")
    )
}
