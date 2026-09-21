package com.secondpasslibrary.reader.home.projection

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import java.time.Instant

private const val HOME_RECENT_READING_LIMIT = 10

@JvmInline
internal value class HomeAccountScopeKey private constructor(val value: String) {
    companion object {
        fun from(serverId: String, profileId: String): HomeAccountScopeKey =
            from(AccountLocalScope.from(serverId, profileId))

        fun from(account: AccountLocalScope) = HomeAccountScopeKey(account.storageKey)
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
    suspend fun hasSnapshot(account: HomeAccountScopeKey): Boolean

    suspend fun purgeAccount(account: HomeAccountScopeKey)

    suspend fun purgeBook(account: HomeAccountScopeKey, bookId: String): Unit =
        error("Book cleanup is not implemented by this Home store.")

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
