package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projection.HomeShelfVariant
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaVisibilityScope
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import java.io.File
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

internal data class AccountLocalDownload(
    val bookId: String,
    val title: String,
    val sizeBytes: Long,
    val author: String? = null,
    val cover: File? = null,
    val epubBytes: Long = 0L,
    val coverBytes: Long = 0L,
    val localSessionCount: Int = 0,
    val pendingChangeCount: Int = 0
)

/** Bounded download management; Reader-authored state remains with its existing owner. */
internal interface AccountLocalDownloadRepository {
    suspend fun downloads(account: AccountLocalScope): List<AccountLocalDownload>

    suspend fun removeDownload(account: AccountLocalScope, bookId: String)

    suspend fun removeAllDownloads(account: AccountLocalScope)

    suspend fun clearBook(
        account: AccountLocalScope,
        bookId: String,
        identity: AuthenticatedConnectionIdentity
    ): Unit = error("Book cleanup is not implemented by this download repository.")
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
        }.groupBy(CachedBook::id).values.map { records ->
            val first = records.first()
            first.copy(cover = records.firstNotNullOfOrNull(CachedBook::cover))
        }
        return candidates.mapNotNull { book ->
            book.takeIf { assets.findCompleted(readerAccount, it.id) != null }?.toCompactBook()
        }.sortedBy { it.sortTitle.lowercase() }
    }

    override suspend fun downloads(account: AccountLocalScope): List<AccountLocalDownload> {
        val assetScope = ReaderAccountScope.from(account)
        val readerAccount = LocalReaderAccountKey.from(account)
        return assets.completedBooks(assetScope).map { book ->
            val cover = assets.findCover(assetScope, book.bookId)
            val summary = reader.bookSummary(readerAccount, book.bookId)
            AccountLocalDownload(
                bookId = book.bookId,
                title = book.title,
                sizeBytes = book.sizeBytes,
                cover = cover,
                epubBytes = book.sizeBytes - (cover?.length() ?: 0L),
                coverBytes = cover?.length() ?: 0L,
                localSessionCount = summary.sessionCount,
                pendingChangeCount = summary.pendingChangeCount
            )
        }.sortedBy { it.title.lowercase() }
    }

    override suspend fun removeDownload(account: AccountLocalScope, bookId: String) {
        assets.removeCompleted(ReaderAccountScope.from(account), bookId)
    }

    override suspend fun removeAllDownloads(account: AccountLocalScope) {
        assets.purgeAccount(ReaderAccountScope.from(account))
    }

    override suspend fun clearBook(
        account: AccountLocalScope,
        bookId: String,
        identity: AuthenticatedConnectionIdentity
    ) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        val readerAccount = LocalReaderAccountKey.from(account)
        readerSync.cancel(readerAccount)
        try {
            marginaliaVisibility.clearBookState(
                ReaderMarginaliaVisibilityScope(identity, bookId),
                reader.bookSessionIds(readerAccount, bookId)
            )
            // Room cascades Reader-owned rows. Other local owners are idempotent and retryable.
            reader.purgeBook(readerAccount, bookId)
            home.purgeBook(HomeAccountScopeKey.from(account), bookId)
            assets.removeCompleted(ReaderAccountScope.from(account), bookId)
        } finally {
            // Remaining Books' pending changes must retain their normal sync opportunity.
            readerSync.ensureEnqueued(readerAccount)
        }
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
