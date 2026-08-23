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
        assertNull(shell.authenticatedFeatureContext)
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
