package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import javax.inject.Inject

/** Stateless Reader read boundary for one exact Session annotation collection. */
internal fun interface ReaderAnnotationsLoader {
    suspend fun load(profile: ConnectionProfile, sessionId: String): List<ReaderAnnotation>
}

internal class SplReaderAnnotationsLoader @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider
) : ReaderAnnotationsLoader {
    override suspend fun load(
        profile: ConnectionProfile,
        sessionId: String
    ): List<ReaderAnnotation> = clientProvider.forProfile(profile)
        .marginalia.sessions.listAnnotations(sessionId)
        .map { it.toReaderAnnotation() }
}
