package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
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

        val result = reconciler(coordinator, binding).reconcile(
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
            val localStore = RecordingLocalStore()
            val reconciler = ReaderSessionReconciler(coordinator, localStore, binding)

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
            assertEquals(ReaderSessionStatus.CLOSED, binding.refreshedStatus)
            assertEquals("server-new", result.session.serverSessionId)
            assertEquals("server-new", localStore.retained?.serverSessionId)
        }

    @Test
    fun `failed provisional reconciliation leaves local binding untouched`() = runTest {
        val coordinator = RecordingCoordinator { error("unreachable") }
        val binding = RecordingBindingStore()

        val result = reconciler(coordinator, binding).reconcile(
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

        val result = reconciler(coordinator, binding).reconcile(
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
        ReaderSessionReconciler(coordinator, RecordingLocalStore(), binding)

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

    private class RecordingLocalStore : LocalReaderStateStore {
        var retained: ReaderSessionContext? = null

        override suspend fun retainServerSession(
            account: LocalReaderAccountKey,
            bookId: String,
            session: ReaderSessionContext
        ): ReaderSessionContext = session.also { retained = it }

        override suspend fun selectOfflineSession(account: LocalReaderAccountKey, bookId: String) =
            error("unused")

        override suspend fun writeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String,
            provenance: LocalReaderWriteProvenance
        ) = Unit

        override suspend fun acknowledgeProgress(
            account: LocalReaderAccountKey,
            localSessionId: String,
            cfi: String
        ) = Unit

        override suspend fun readAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String
        ) = emptyList<ReaderAnnotation>()

        override suspend fun applyAnnotationMutation(
            account: LocalReaderAccountKey,
            localSessionId: String,
            request: ReaderAnnotationMutationRequest
        ) = emptyList<ReaderAnnotation>()

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            confirmedClientId: String?
        ) = Unit

        override suspend fun purgeAccount(account: LocalReaderAccountKey) = Unit
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
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
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
