package com.secondpasslibrary.reader.library.offline

import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projection.HomeShelfVariant
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import javax.inject.Inject

/** Builds the offline Library from cached book metadata, admitted by immutable EPUB truth. */
internal class OfflineLibraryCatalog @Inject constructor(
    private val projections: HomeProjectionStore,
    private val assets: ReaderBookAssetStore
) {
    suspend fun downloadedBooks(profile: ConnectionProfile, profileId: String): List<CompactBook> {
        val projectionScope = HomeAccountScopeKey.from(profile.serverOrigin, profileId)
        val account = ReaderAccountScope(profile.serverOrigin, profileId)
        val candidates = buildList {
            assets.completedBooks(account).forEach { book ->
                add(CachedBook(book.bookId, book.title, cover = null))
            }
            HomeRecentReadingVariant.entries.forEach { variant ->
                projections.readRecentReading(
                    projectionScope,
                    variant
                )?.items.orEmpty().forEach { item ->
                    add(CachedBook(item.book.id, item.book.title, item.book.cover))
                }
            }
            projections.readShelves(projectionScope, HomeShelfVariant.FirstPageWithPreviews)
                ?.items.orEmpty()
                .flatMap { it.previewBooks.orEmpty() }
                .forEach { add(CachedBook(it.id, it.title, it.cover)) }
        }.distinctBy(CachedBook::id)
        return candidates.mapNotNull { book ->
            book.takeIf { assets.findCompleted(account, it.id) != null }?.toCompactBook()
        }.sortedBy { it.sortTitle.lowercase() }
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
