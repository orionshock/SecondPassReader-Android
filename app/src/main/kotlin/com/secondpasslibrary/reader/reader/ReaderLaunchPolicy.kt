package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import javax.inject.Inject

internal enum class ReaderLaunchDecision {
    ONLINE,
    LOCAL_READ_ONLY,
    OFFLINE_ASSET_UNAVAILABLE
}

/** Owns the one app-level decision about whether a Book may enter Reader while offline. */
internal fun interface ReaderLaunchAdmission {
    suspend fun decide(
        availability: AppAvailability,
        profile: ConnectionProfile,
        profileId: String,
        bookId: String
    ): ReaderLaunchDecision
}

internal class ReaderLaunchPolicy @Inject constructor(private val assets: ReaderBookAssetStore) :
    ReaderLaunchAdmission {
    override suspend fun decide(
        availability: AppAvailability,
        profile: ConnectionProfile,
        profileId: String,
        bookId: String
    ): ReaderLaunchDecision {
        if (availability !is AppAvailability.Offline) return ReaderLaunchDecision.ONLINE
        val account = ReaderAccountScope(profile.serverOrigin, profileId)
        return if (assets.findCompleted(account, bookId) != null) {
            ReaderLaunchDecision.LOCAL_READ_ONLY
        } else {
            ReaderLaunchDecision.OFFLINE_ASSET_UNAVAILABLE
        }
    }
}
