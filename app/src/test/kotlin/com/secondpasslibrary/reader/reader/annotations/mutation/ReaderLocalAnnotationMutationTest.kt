package com.secondpasslibrary.reader.reader.annotations.mutation

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderLocalAnnotationMutationTest {
    @Test
    fun `provisional annotation persists and reconciles locally without server delivery`() =
        runTest {
            val store = RecordingStore()
            var serverCalls = 0
            val reconciled = mutableListOf<List<ReaderAnnotation>>()
            val controller = ReaderAnnotationMutationController(
                writer = ReaderAnnotationWriter { _, _ ->
                    serverCalls += 1
                    error("Offline mutation must not reach SPL.")
                },
                scope = this,
                onAuthoritativeAnnotations = { _, annotations -> reconciled += annotations },
                clientIdFactory = { "client-local" },
                localStore = store
            )
            controller.select(
                profile(),
                "profile-1",
                ReaderSessionContext(
                    "local-session",
                    ReaderSessionStatus.ACTIVE,
                    null,
                    serverSessionId = null,
                    identityKind = ReaderSessionIdentityKind.PROVISIONAL
                ),
                serverAvailable = false
            )
            controller.accept(
                ReaderAnnotationMutationIntent.BeginCreate(
                    ReaderSelection(EpubCfi(CFI), "quote", "prefix", "suffix", "Chapter 1")
                )
            )

            controller.accept(
                ReaderAnnotationMutationIntent.SubmitQuickCreate(ReaderAnnotationColor.GREEN)
            )
            advanceUntilIdle()

            val persisted = store.request as ReaderAnnotationMutationRequest.UpsertHighlight
            assertEquals("client-local", persisted.clientId)
            assertEquals(ReaderAnnotationColor.GREEN, persisted.color)
            assertEquals(0, serverCalls)
            assertEquals("client-local", reconciled.last().single().clientId)
        }

    private class RecordingStore : LocalReaderStateStore {
        var request: ReaderAnnotationMutationRequest? = null

        override suspend fun selectOfflineSession(account: LocalReaderAccountKey, bookId: String) =
            error("unused")

        override suspend fun retainServerSession(
            account: LocalReaderAccountKey,
            bookId: String,
            session: ReaderSessionContext
        ) = session

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
        ): List<ReaderAnnotation> {
            this.request = request
            val upsert = request as ReaderAnnotationMutationRequest.UpsertHighlight
            return listOf(
                ReaderAnnotation.Highlight(
                    "local:${upsert.clientId}",
                    upsert.clientId,
                    upsert.cfi,
                    upsert.locationLabel,
                    "now",
                    upsert.text,
                    upsert.prefix,
                    upsert.suffix,
                    upsert.note,
                    upsert.color
                )
            )
        }

        override suspend fun replaceAuthoritativeAnnotations(
            account: LocalReaderAccountKey,
            localSessionId: String,
            annotations: List<ReaderAnnotation>,
            acknowledgedMutation: ReaderAnnotationMutationRequest?
        ) = Unit

        override suspend fun purgeAccount(account: LocalReaderAccountKey) = Unit
    }

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
        const val CFI = "epubcfi(/6/2!/4/2,/1:0,/1:4)"
    }
}
