package com.secondpasslibrary.reader.home.projection

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

private const val HOME_RECENT_READING_LIMIT = 10

@JvmInline
internal value class HomeAccountScopeKey private constructor(val value: String) {
    companion object {
        fun from(serverOrigin: String, profileId: String): HomeAccountScopeKey {
            val origin = serverOrigin.trim().trimEnd('/').lowercase(Locale.ROOT)
            val account = profileId.trim()
            require(origin.isNotEmpty()) { "Server origin is required for Home cache scope." }
            require(account.isNotEmpty()) { "Profile ID is required for Home cache scope." }
            val digest =
                MessageDigest.getInstance("SHA-256")
                    .digest("$origin\u0000$account".toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte) }
            return HomeAccountScopeKey(digest)
        }
    }
}

internal enum class HomeRecentReadingVariant(
    val storageKey: String,
    val limit: Int,
    val includeClosed: Boolean
) {
    ActiveOnly("limit=10;includeClosed=false", HOME_RECENT_READING_LIMIT, false),
    IncludingClosed("limit=10;includeClosed=true", HOME_RECENT_READING_LIMIT, true)
}

internal enum class HomeShelfVariant(
    val storageKey: String,
    val page: Int,
    val pageSize: Int,
    val previewLimit: Int
) {
    FirstPageWithPreviews(
        storageKey = "page=1;pageSize=6;ordering=server;previewLimit=3",
        page = 1,
        pageSize = 6,
        previewLimit = 3
    )
}

internal data class HomeProjectionSnapshot<T>(val items: List<T>, val fetchedAt: Instant)

internal interface HomeProjectionStore {
    suspend fun readRecentReading(
        account: HomeAccountScopeKey,
        variant: HomeRecentReadingVariant
    ): HomeProjectionSnapshot<RecentReadingItem>?

    suspend fun replaceRecentReading(
        account: HomeAccountScopeKey,
        variant: HomeRecentReadingVariant,
        items: List<RecentReadingItem>,
        fetchedAt: Instant
    )

    suspend fun readShelves(
        account: HomeAccountScopeKey,
        variant: HomeShelfVariant
    ): HomeProjectionSnapshot<ShelfSummary>?

    suspend fun replaceShelves(
        account: HomeAccountScopeKey,
        variant: HomeShelfVariant,
        shelves: List<ShelfSummary>,
        fetchedAt: Instant
    )
}
