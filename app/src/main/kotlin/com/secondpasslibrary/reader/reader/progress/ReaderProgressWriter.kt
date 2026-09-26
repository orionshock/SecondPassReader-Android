package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.client.ReadingProgressInput
import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface ReaderProgressWriter {
    suspend fun replace(
        profile: ConnectionProfile,
        sessionId: String,
        cfi: EpubCfi,
        locationLabel: String?
    ): ReaderProgressWriteOutcome
}

internal sealed interface ReaderProgressWriteOutcome {
    data object Success : ReaderProgressWriteOutcome

    data class Failure(val reason: ReaderProgressSyncFailure) : ReaderProgressWriteOutcome
}

internal enum class ReaderProgressSyncFailure {
    AUTHENTICATION_REQUIRED,
    SESSION_NOT_WRITABLE,
    UNAVAILABLE
}

@Singleton
internal class SplReaderProgressWriter @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider
) : ReaderProgressWriter {
    private val deliveryMutex = Mutex()

    override suspend fun replace(
        profile: ConnectionProfile,
        sessionId: String,
        cfi: EpubCfi,
        locationLabel: String?
    ): ReaderProgressWriteOutcome = deliveryMutex.withLock {
        replaceSerially(profile, sessionId, cfi, locationLabel)
    }

    private suspend fun replaceSerially(
        profile: ConnectionProfile,
        sessionId: String,
        cfi: EpubCfi,
        locationLabel: String?
    ): ReaderProgressWriteOutcome = try {
        val authoritative = clientProvider.forProfile(profile).marginalia.sessions.replaceProgress(
            sessionId,
            ReadingProgressInput(cfi.value, locationLabel)
        )
        if (authoritative.location == cfi.value) {
            ReaderProgressWriteOutcome.Success
        } else {
            ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SplClientException.AuthenticationRejected) {
        ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.AUTHENTICATION_REQUIRED)
    } catch (failure: SplClientException.ReadingSessionLifecycleRejected) {
        ReaderProgressWriteOutcome.Failure(
            if (failure.reason == ReadingSessionLifecycleRejection.SESSION_CLOSED ||
                failure.reason == ReadingSessionLifecycleRejection.RESOURCE_NOT_FOUND
            ) {
                ReaderProgressSyncFailure.SESSION_NOT_WRITABLE
            } else {
                ReaderProgressSyncFailure.UNAVAILABLE
            }
        )
    } catch (_: Exception) {
        ReaderProgressWriteOutcome.Failure(ReaderProgressSyncFailure.UNAVAILABLE)
    }
}
