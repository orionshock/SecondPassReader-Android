package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionDetail
import com.secondpasslibrary.client.ReadingSessionStatus as ServerSessionStatus
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import javax.inject.Inject

/** Copies existing server-owned Reader state without ever establishing a Session. */
internal fun interface ReaderExistingSessionsCache {
    suspend fun cache(profile: ConnectionProfile, profileId: String, bookId: String)
}

internal class SplReaderExistingSessionsCache @Inject constructor(
    private val clients: AuthenticatedClientProvider,
    private val local: LocalReaderStateStore,
    private val annotations: ReaderAnnotationsLoader
) : ReaderExistingSessionsCache {
    override suspend fun cache(profile: ConnectionProfile, profileId: String, bookId: String) {
        val client = clients.forProfile(profile)
        val account = LocalReaderAccountKey.from(profile.serverId, profileId)
        var pageNumber = 1
        do {
            val page = client.marginalia.books.listSessions(
                bookId,
                BookReadingSessionListOptions(page = pageNumber)
            )
            require(page.book.id == bookId) { "Reading Sessions belong to another Book." }
            page.sessions.results.forEach { summary ->
                val detail = client.marginalia.sessions.get(summary.id)
                require(detail.book.id == bookId) { "Reading Session belongs to another Book." }
                val progress = client.marginalia.sessions.getProgress(summary.id)
                val retained = local.retainServerSession(
                    account,
                    bookId,
                    detail.session.toReaderContext(progress?.cfi)
                )
                local.replaceAuthoritativeAnnotations(
                    account,
                    retained.sessionId,
                    annotations.load(profile, summary.id)
                )
            }
            if (!page.sessions.hasNext) break
            pageNumber++
        } while (true)
    }
}

private fun ReadingSessionDetail.toReaderContext(progressCfi: String?): ReaderSessionContext =
    ReaderSessionContext(
        sessionId = summary.id,
        status = when (summary.status) {
            ServerSessionStatus.ACTIVE -> ReaderSessionStatus.ACTIVE
            ServerSessionStatus.CLOSED -> ReaderSessionStatus.CLOSED
        },
        savedProgressCfi = progressCfi,
        sessionName = summary.name,
        startedAt = summary.startedAt,
        closedAt = summary.closedAt,
        lastActivityAt = summary.lastActivityAt,
        annotationCount = summary.annotationCount,
        sessionNotes = summary.notes
    )
