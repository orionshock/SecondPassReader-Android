package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.reader.connection.storage.WorkOfflineStore
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.sync.ReaderSyncWorkQueue
import com.secondpasslibrary.reader.storage.database.SecondPassLocalDatabase
import javax.inject.Inject

/** One-time pre-release discard; no URL-keyed data is decoded into the new namespace. */
internal fun interface LegacyAccountStateReset {
    suspend fun discard()
}

internal class LegacyAccountStateResetImpl @Inject constructor(
    private val database: SecondPassLocalDatabase,
    private val assets: ReaderBookAssetStore,
    private val visibility: ReaderMarginaliaLayerVisibilityStore,
    private val workOffline: WorkOfflineStore,
    private val workQueue: ReaderSyncWorkQueue
) : LegacyAccountStateReset {
    override suspend fun discard() {
        workQueue.cancelAll()
        database.clearAllTables()
        assets.purgeAll()
        visibility.clearAccountState()
        workOffline.clearAll()
    }
}
