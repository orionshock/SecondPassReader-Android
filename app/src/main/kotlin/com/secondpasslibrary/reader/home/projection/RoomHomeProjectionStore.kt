package com.secondpasslibrary.reader.home.projection

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomHomeProjectionStore
@Inject
constructor(
    private val recentReadingDao: RecentReadingProjectionDao,
    private val shelfDao: ShelfProjectionDao,
    private val cleanupDao: HomeProjectionCleanupDao
) : HomeProjectionStore {
    override suspend fun hasSnapshot(account: HomeAccountScopeKey): Boolean =
        recentReadingDao.hasSnapshot(account.value) || shelfDao.hasSnapshot(account.value)

    override suspend fun purgeAccount(account: HomeAccountScopeKey) {
        cleanupDao.purgeAccount(account.value)
    }

    override suspend fun readRecentReading(
        account: HomeAccountScopeKey,
        variant: HomeRecentReadingVariant
    ): HomeProjectionSnapshot<RecentReadingItem>? {
        val snapshot =
            recentReadingDao.snapshot(account.value, variant.storageKey)
                ?: return null
        return HomeProjectionSnapshot(
            items = recentReadingDao.items(account.value, variant.storageKey).map { it.toModel() },
            fetchedAt = Instant.ofEpochMilli(snapshot.fetchedAtEpochMillis)
        )
    }

    override suspend fun replaceRecentReading(
        account: HomeAccountScopeKey,
        variant: HomeRecentReadingVariant,
        items: List<RecentReadingItem>,
        fetchedAt: Instant
    ) {
        recentReadingDao.replace(
            snapshot = snapshot(
                account,
                ProjectionKind.RECENT_READING,
                variant.storageKey,
                fetchedAt
            ),
            items = items.mapIndexed { position, item -> item.toEntity(account, variant, position) }
        )
    }

    override suspend fun readShelves(
        account: HomeAccountScopeKey,
        variant: HomeShelfVariant
    ): HomeProjectionSnapshot<ShelfSummary>? {
        val snapshot =
            shelfDao.snapshot(account.value, variant.storageKey) ?: return null
        val previews =
            shelfDao.previews(account.value, variant.storageKey).groupBy { it.shelfId }
        return HomeProjectionSnapshot(
            items =
                shelfDao.shelves(account.value, variant.storageKey).map { shelf ->
                    shelf.toModel(previews[shelf.shelfId].orEmpty())
                },
            fetchedAt = Instant.ofEpochMilli(snapshot.fetchedAtEpochMillis)
        )
    }

    override suspend fun replaceShelves(
        account: HomeAccountScopeKey,
        variant: HomeShelfVariant,
        shelves: List<ShelfSummary>,
        fetchedAt: Instant
    ) {
        shelfDao.replace(
            snapshot = snapshot(account, ProjectionKind.SHELVES, variant.storageKey, fetchedAt),
            shelves = shelves.mapIndexed { position, shelf ->
                shelf.toEntity(account, variant, position)
            },
            previews = shelves.flatMap { shelf -> shelf.previewEntities(account, variant) }
        )
    }

    private fun snapshot(
        account: HomeAccountScopeKey,
        kind: String,
        variant: String,
        fetchedAt: Instant
    ) = HomeProjectionSnapshotEntity(
        accountKey = account.value,
        projectionKind = kind,
        variantKey = variant,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli()
    )

    private object ProjectionKind {
        const val RECENT_READING = "recent_reading"
        const val SHELVES = "shelves"
    }
}
