package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.persistence.ReaderClosedSessionContinuation
import com.secondpasslibrary.reader.reader.persistence.ReaderClosedSessionContinuationStore
import com.secondpasslibrary.reader.reader.persistence.ReaderSessionBindingStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionReconcilerTest {
    @Test
    fun `provisional binds exact active Session returned by server`() = runTest {
        val coordinator = RecordingCoordinator { server("server-existing") }
        val binding = RecordingBindingStore()
        val reconciler = reconciler(coordinator, binding)

        val result = reconciler.reconcile(profile(), account(), BOOK_ID, provisional())
            as ReaderSessionReconciliationResult.Resolved

        assertNull(coordinator.requests.single().existingSessionId)
        assertEquals("local-provisional", result.session.sessionId)
        assertEquals("server-existing", result.session.serverSessionId)
        assertEquals("server-existing", binding.boundAuthoritativeId)
    }

    @Test
    fun `provisional accepts convergent open result rather than assumed identity`() = runTest {
        val coordinator = RecordingCoordinator { server("server-created-by-authority") }
        val binding = RecordingBindingStore()

        val reconciler = ReaderSessionReconciler(
            coordinator,
            binding,
            RecordingContinuationStore(provisional()),
            ReaderAnnotationsLoader { _, _ -> emptyList() }
        )
        val result = reconciler.reconcile(
            profile(),
            account(),
            BOOK_ID,
            provisional()
        ) as ReaderSessionReconciliationResult.Resolved

        assertEquals("server-created-by-authority", result.session.serverSessionId)
    }

    @Test
    fun `remotely closed active Session is refreshed before writable Session resolution`() =
        runTest {
            val coordinator = RecordingCoordinator { request ->
                if (request.existingSessionId != null) {
                    server("server-old", ReaderSessionStatus.CLOSED)
                } else {
                    server("server-new")
                }
            }
            val binding = RecordingBindingStore()
            val continuation = RecordingContinuationStore(provisional())
            val reconciler = ReaderSessionReconciler(
                coordinator,
                binding,
                continuation,
                ReaderAnnotationsLoader { _, _ -> emptyList() }
            )

            val result = reconciler.reconcile(
                profile(),
                account(),
                BOOK_ID,
                server("local-old").copy(serverSessionId = "server-old")
            ) as ReaderSessionReconciliationResult.Resolved

            assertEquals(
                listOf("server-old", null),
                coordinator.requests.map {
                    it.existingSessionId
                }
            )
            assertEquals("server-new", result.session.serverSessionId)
            assertEquals("local-provisional", result.session.sessionId)
            assertEquals("local-old", continuation.closedSessionId)
        }

    @Test
    fun `failed provisional reconciliation leaves local binding untouched`() = runTest {
        val coordinator = RecordingCoordinator { error("unreachable") }
        val binding = RecordingBindingStore()

        val reconciler = ReaderSessionReconciler(
            coordinator,
            binding,
            RecordingContinuationStore(provisional()),
            ReaderAnnotationsLoader { _, _ -> emptyList() }
        )
        val result = reconciler.reconcile(
            profile(),
            account(),
            BOOK_ID,
            provisional()
        )

        assertTrue(result is ReaderSessionReconciliationResult.Failed)
        assertNull(binding.boundAuthoritativeId)
    }

    @Test
    fun `closed refresh is returned when replacement resolution fails`() = runTest {
        var call = 0
        val coordinator = RecordingCoordinator {
            call += 1
            if (call == 1) server("server-old", ReaderSessionStatus.CLOSED) else error("offline")
        }
        val binding = RecordingBindingStore()
        val reconciler = ReaderSessionReconciler(
            coordinator,
            binding,
            RecordingContinuationStore(provisional()),
            ReaderAnnotationsLoader { _, _ -> emptyList() }
        )

        val result = reconciler.reconcile(
            profile(),
            account(),
            BOOK_ID,
            server("local-old").copy(serverSessionId = "server-old")
        ) as ReaderSessionReconciliationResult.Failed

        assertEquals(ReaderSessionStatus.CLOSED, result.refreshedSession?.status)
    }

    @Test
    fun `repeated reconciliation retains stable local and server identity`() = runTest {
        val coordinator = RecordingCoordinator { server("server-1") }
        val binding = RecordingBindingStore()
        val reconciler = reconciler(coordinator, binding)
        val first = reconciler.reconcile(profile(), account(), BOOK_ID, provisional())
            as ReaderSessionReconciliationResult.Resolved
        val second = reconciler.reconcile(profile(), account(), BOOK_ID, first.session)
            as ReaderSessionReconciliationResult.Resolved

        assertEquals("local-provisional", second.session.sessionId)
        assertEquals("server-1", second.session.serverSessionId)
        assertEquals(1, binding.bindCalls)
        assertEquals(1, binding.refreshCalls)
    }

    private fun reconciler(coordinator: ReaderSessionCoordinator, binding: RecordingBindingStore) =
        ReaderSessionReconciler(
            coordinator,
            binding,
            RecordingContinuationStore(null),
            ReaderAnnotationsLoader { _, _ -> emptyList() }
        )

    private class RecordingCoordinator(
        private val response: suspend (ReaderSessionRequest) -> ReaderSessionContext
    ) : ReaderSessionCoordinator {
        val requests = mutableListOf<ReaderSessionRequest>()

        override suspend fun resolve(
            profile: ConnectionProfile,
            request: ReaderSessionRequest
        ): ReaderSessionContext {
            requests += request
            return response(request)
        }
    }

    private class RecordingBindingStore : ReaderSessionBindingStore {
        var boundAuthoritativeId: String? = null
        var refreshedStatus: ReaderSessionStatus? = null
        var bindCalls = 0
        var refreshCalls = 0

        override suspend fun bindProvisional(
            account: LocalReaderAccountKey,
            bookId: String,
            localSessionId: String,
            authoritative: ReaderSessionContext
        ): ReaderSessionContext {
            bindCalls += 1
            boundAuthoritativeId = authoritative.serverSessionId
            return authoritative.copy(sessionId = localSessionId)
        }

        override suspend fun refreshConfirmed(
            account: LocalReaderAccountKey,
            bookId: String,
            localSessionId: String,
            authoritative: ReaderSessionContext
        ): ReaderSessionContext {
            refreshCalls += 1
            refreshedStatus = authoritative.status
            return authoritative.copy(sessionId = localSessionId)
        }
    }

    private class RecordingContinuationStore(private val continuation: ReaderSessionContext?) :
        ReaderClosedSessionContinuationStore {
        var closedSessionId: String? = null

        override suspend fun continueFrom(
            account: LocalReaderAccountKey,
            bookId: String,
            closedSession: ReaderSessionContext,
            authoritativeAnnotations: List<ReaderAnnotation>
        ): ReaderClosedSessionContinuation {
            closedSessionId = closedSession.sessionId
            return ReaderClosedSessionContinuation(continuation, 0, 0)
        }
    }

    private fun provisional() = ReaderSessionContext(
        sessionId = "local-provisional",
        status = ReaderSessionStatus.ACTIVE,
        savedProgressCfi = null,
        serverSessionId = null,
        identityKind = ReaderSessionIdentityKind.PROVISIONAL
    )

    private fun server(id: String, status: ReaderSessionStatus = ReaderSessionStatus.ACTIVE) =
        ReaderSessionContext(id, status, null)

    private fun account() = LocalReaderAccountKey.from("https://library.example", "profile-1")

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
        const val BOOK_ID = "book-1"
    }
}
