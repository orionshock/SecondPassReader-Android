package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projection.HomeShelfVariant
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import javax.inject.Inject
import javax.inject.Singleton

/** Destructive lifecycle boundary for the app's single retained account-local footprint. */
internal fun interface AccountLocalDataLifecycle {
    suspend fun purge(account: AccountLocalScope)
}

/** Cross-feature read boundary for Books that remain readable without server authority. */
internal fun interface AccountLocalBookCatalog {
    suspend fun downloadedBooks(account: AccountLocalScope): List<CompactBook>
}

internal data class AccountLocalDownload(val bookId: String, val title: String, val sizeBytes: Long)

/** Bounded download management; Reader-authored state remains with its existing owner. */
internal interface AccountLocalDownloadRepository {
    suspend fun downloads(account: AccountLocalScope): List<AccountLocalDownload>

    suspend fun removeDownload(account: AccountLocalScope, bookId: String)

    suspend fun removeAllDownloads(account: AccountLocalScope)
}

/** Coordinates account-local stores without making their feature-specific APIs public. */
@Singleton
internal class AccountLocalDataRepository @Inject constructor(
    private val home: HomeProjectionStore,
    private val reader: LocalReaderStateStore,
    private val readerSync: ReaderPendingSyncScheduler,
    private val assets: ReaderBookAssetStore,
    private val marginaliaVisibility: ReaderMarginaliaLayerVisibilityStore
) : AccountLocalDataLifecycle,
    AccountLocalBookCatalog,
    AccountLocalDownloadRepository {
    override suspend fun purge(account: AccountLocalScope) {
        val readerAccount = LocalReaderAccountKey.from(account)
        readerSync.cancel(readerAccount)
        home.purgeAccount(HomeAccountScopeKey.from(account))
        reader.purgeAccount(readerAccount)
        assets.purgeAccount(ReaderAccountScope.from(account))
        marginaliaVisibility.clearAccountState()
    }

    override suspend fun downloadedBooks(account: AccountLocalScope): List<CompactBook> {
        val homeAccount = HomeAccountScopeKey.from(account)
        val readerAccount = ReaderAccountScope.from(account)
        val candidates = buildList {
            assets.completedBooks(readerAccount).forEach { book ->
                add(CachedBook(book.bookId, book.title, cover = null))
            }
            HomeRecentReadingVariant.entries.forEach { variant ->
                home.readRecentReading(homeAccount, variant)?.items.orEmpty().forEach { item ->
                    add(CachedBook(item.book.id, item.book.title, item.book.cover))
                }
            }
            home.readShelves(homeAccount, HomeShelfVariant.FirstPageWithPreviews)
                ?.items.orEmpty()
                .flatMap { it.previewBooks.orEmpty() }
                .forEach { add(CachedBook(it.id, it.title, it.cover)) }
        }.distinctBy(CachedBook::id)
        return candidates.mapNotNull { book ->
            book.takeIf { assets.findCompleted(readerAccount, it.id) != null }?.toCompactBook()
        }.sortedBy { it.sortTitle.lowercase() }
    }

    override suspend fun downloads(account: AccountLocalScope): List<AccountLocalDownload> =
        assets.completedBooks(ReaderAccountScope.from(account))
            .map { AccountLocalDownload(it.bookId, it.title, it.sizeBytes) }
            .sortedBy { it.title.lowercase() }

    override suspend fun removeDownload(account: AccountLocalScope, bookId: String) {
        assets.removeCompleted(ReaderAccountScope.from(account), bookId)
    }

    override suspend fun removeAllDownloads(account: AccountLocalScope) {
        assets.purgeAccount(ReaderAccountScope.from(account))
    }
}

private data class CachedBook(
    val id: String,
    val title: String,
    val cover: PublicBookCoverReference?
)

private fun CachedBook.toCompactBook() = CompactBook(
    id = id,
    title = title,
    sortTitle = title,
    subtitle = "",
    authors = emptyList(),
    series = null,
    catalogTags = emptyList(),
    language = null,
    publisher = null,
    publishedYear = null,
    publishedMonth = null,
    publishedDay = null,
    publicationDatePrecision = PublicationDatePrecision.UNSPECIFIED,
    cover = cover,
    fileFormat = "epub"
)
