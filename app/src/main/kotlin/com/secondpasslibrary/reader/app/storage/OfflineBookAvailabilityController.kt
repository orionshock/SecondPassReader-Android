package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetRequest
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import com.secondpasslibrary.reader.reader.session.ReaderExistingSessionsCache
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class OfflineBookKey(val account: AccountLocalScope, val bookId: String)

internal class BookDownloadNeedsLibraryException :
    Exception("Connect to the Library to download this Book.")

/** One account-scoped asset operation shared by Book surfaces and download management. */
@Singleton
internal class OfflineBookAvailabilityController @Inject constructor(
    private val resolver: ReaderBookAssetResolver,
    private val assets: ReaderBookAssetStore,
    private val downloads: AccountLocalDownloadRepository,
    private val sessions: ReaderExistingSessionsCache
) {
    private val mutation = Mutex()
    private val mutableBusy = MutableStateFlow<Set<OfflineBookKey>>(emptySet())
    val busy = mutableBusy.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    suspend fun isAvailable(account: AccountLocalScope, bookId: String): Boolean =
        assets.findCompleted(ReaderAccountScope.from(account), bookId) != null

    suspend fun makeAvailable(
        profile: ConnectionProfile,
        profileId: String,
        bookId: String,
        availability: AppAvailability
    ) {
        val account = AccountLocalScope.from(profile.serverOrigin, profileId)
        val key = OfflineBookKey(account, bookId)
        mutation.withLock {
            if (isAvailable(account, bookId)) return@withLock
            if (availability is AppAvailability.Offline) throw BookDownloadNeedsLibraryException()
            mutableBusy.value = mutableBusy.value + key
            try {
                resolver.resolve(
                    ReaderBookAssetRequest(profile, profileId, bookId),
                    onDownloadStarted = {}
                )
                mutableRevision.value++
                sessions.cache(profile, profileId, bookId)
            } finally {
                mutableBusy.value = mutableBusy.value - key
            }
        }
    }

    suspend fun remove(account: AccountLocalScope, bookId: String) {
        val key = OfflineBookKey(account, bookId)
        mutation.withLock {
            mutableBusy.value = mutableBusy.value + key
            try {
                downloads.removeDownload(account, bookId)
                mutableRevision.value++
            } finally {
                mutableBusy.value = mutableBusy.value - key
            }
        }
    }

    suspend fun removeAll(account: AccountLocalScope) {
        mutation.withLock {
            downloads.removeAllDownloads(account)
            mutableRevision.value++
        }
    }
}
