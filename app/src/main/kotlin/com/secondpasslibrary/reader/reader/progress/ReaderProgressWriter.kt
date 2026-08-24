package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.client.ReadingProgressInput
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal fun interface ReaderProgressWriter {
    suspend fun replace(
        profile: ConnectionProfile,
        sessionId: String,
        cfi: EpubCfi
    ): ReaderProgressWriteOutcome
}

internal sealed interface ReaderProgressWriteOutcome {
    data object Success : ReaderProgressWriteOutcome

    data class Failure(val reason: ReaderProgressSyncFailure) : ReaderProgressWriteOutcome
}

internal enum class ReaderProgressSyncFailure {
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE
}

internal class SplReaderProgressWriter @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider
) : ReaderProgressWriter {
    override suspend fun replace(
        profile: ConnectionProfile,
        sessionId: String,
        cfi: EpubCfi
    ): ReaderProgressWriteOutcome = try {
        val authoritative = clientProvider.forProfile(profile).marginalia.sessions.replaceProgress(
            sessionId,
            ReadingProgressInput(cfi.value)
        )
        if (authoritative.cfi == cfi.value) {
            ReaderProgressWriteOutcome.Success
        } else {
            ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SplClientException.AuthenticationRejected) {
        ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.AUTHENTICATION_REQUIRED)
    } catch (_: Exception) {
        ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
    }
}
