package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.ReaderSessionAuthority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionReconciliationControllerTest {
    @Test
    fun `local Session reconciles once when availability becomes Online`() = runTest {
        var calls = 0
        val resolved = mutableListOf<ReaderSessionContext>()
        val controller = controller(
            scope = this,
            reconcile = { _, _, _, local ->
                calls += 1
                ReaderSessionReconciliationResult.Resolved(
                    local.copy(serverSessionId = "server-1")
                )
            },
            onResolved = { _, session -> resolved += session }
        )

        controller.select(
            profile(),
            PROFILE_ID,
            BOOK_ID,
            provisional(),
            ReaderSessionAuthority.LOCAL
        )
        controller.setAvailability(AppAvailability.Syncing)
        advanceUntilIdle()
        assertEquals(0, calls)

        controller.setAvailability(AppAvailability.Online)
        advanceUntilIdle()
        controller.setAvailability(AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("server-1", resolved.single().serverSessionId)
    }

    @Test
    fun `new Online generation retries a failed reconciliation`() = runTest {
        var calls = 0
        val controller = controller(scope = this, reconcile = { _, _, _, _ ->
            calls += 1
            ReaderSessionReconciliationResult.Failed(
                ReaderSessionReconciliationFailure.UNAVAILABLE
            )
        })
        controller.select(
            profile(),
            PROFILE_ID,
            BOOK_ID,
            provisional(),
            ReaderSessionAuthority.LOCAL
        )

        controller.setAvailability(AppAvailability.Online)
        advanceUntilIdle()
        controller.setAvailability(AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE))
        controller.setAvailability(AppAvailability.Online)
        advanceUntilIdle()

        assertEquals(2, calls)
    }

    @Test
    fun `replacing Reader owner prevents stale reconciliation publication`() = runTest {
        val firstGate = CompletableDeferred<ReaderSessionReconciliationResult>()
        val resolved = mutableListOf<String>()
        val controller = controller(
            scope = this,
            reconcile = { _, _, bookId, local ->
                if (bookId == BOOK_ID) {
                    firstGate.await()
                } else {
                    ReaderSessionReconciliationResult.Resolved(
                        local.copy(serverSessionId = "server-2")
                    )
                }
            },
            onResolved = { localId, _ -> resolved += localId }
        )
        controller.setAvailability(AppAvailability.Online)
        controller.select(
            profile(),
            PROFILE_ID,
            BOOK_ID,
            provisional(),
            ReaderSessionAuthority.LOCAL
        )
        runCurrent()

        controller.select(
            profile(),
            PROFILE_ID,
            "book-2",
            provisional().copy(sessionId = "local-2"),
            authority = ReaderSessionAuthority.LOCAL
        )
        advanceUntilIdle()
        firstGate.complete(
            ReaderSessionReconciliationResult.Resolved(
                provisional().copy(serverSessionId = "stale-server")
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("local-2"), resolved)
    }

    @Test
    fun `authentication failure is surfaced without resolving Session`() = runTest {
        var rejected = false
        val resolved = mutableListOf<ReaderSessionContext>()
        val controller = controller(
            scope = this,
            reconcile = { _, _, _, _ ->
                ReaderSessionReconciliationResult.Failed(
                    ReaderSessionReconciliationFailure.AUTHENTICATION_REQUIRED
                )
            },
            onResolved = { _, session -> resolved += session },
            onAuthenticationRejected = { rejected = true }
        )
        controller.select(
            profile(),
            PROFILE_ID,
            BOOK_ID,
            provisional(),
            ReaderSessionAuthority.LOCAL
        )
        controller.setAvailability(AppAvailability.Online)
        advanceUntilIdle()

        assertTrue(rejected)
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `delivery rejection can refresh an already bound Session`() = runTest {
        var calls = 0
        val controller = controller(
            scope = this,
            reconcile = { _, _, _, local ->
                calls += 1
                ReaderSessionReconciliationResult.Resolved(local)
            }
        )
        val bound = provisional().copy(
            serverSessionId = "server-1",
            identityKind = ReaderSessionIdentityKind.SERVER_CONFIRMED
        )
        controller.select(
            profile(),
            PROFILE_ID,
            BOOK_ID,
            bound,
            ReaderSessionAuthority.SERVER
        )
        controller.setAvailability(AppAvailability.Online)
        advanceUntilIdle()
        assertEquals(0, calls)

        controller.requestAuthorityRefresh(bound.sessionId)
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    private fun controller(
        scope: CoroutineScope,
        reconcile: ReaderSessionReconciliation,
        onResolved: (String, ReaderSessionContext) -> Unit = { _, _ -> },
        onAuthenticationRejected: () -> Unit = {}
    ) = ReaderSessionReconciliationController(
        reconcile,
        scope,
        onResolved,
        onAuthenticationRejected
    )

    private fun provisional() = ReaderSessionContext(
        sessionId = "local-1",
        status = ReaderSessionStatus.ACTIVE,
        savedProgressCfi = null,
        identityKind = ReaderSessionIdentityKind.PROVISIONAL
    )

    private fun profile() = ConnectionProfile(
        serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
        serverOrigin = "https://library.example",
        libraryBaseUrl = "https://library.example",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-30",
        clientSessionId = "client-session",
        clientName = "Reader",
        clientType = "reader"
    )

    private companion object {
        const val PROFILE_ID = "profile-1"
        const val BOOK_ID = "book-1"
    }
}
