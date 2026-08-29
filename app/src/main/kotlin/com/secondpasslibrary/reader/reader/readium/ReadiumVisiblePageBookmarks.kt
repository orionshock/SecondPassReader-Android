package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarks
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarksResolver
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumEpubCfiNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Keeps exact bookmark CFI resolution and rendered-page intersection adapter-local. */
internal class ReadiumVisiblePageBookmarks(
    private val navigator: ReadiumEpubCfiNavigator,
    private val hudEvents: ReadiumReaderHudEvents,
    private val movements: ReadiumViewportMovements
) : ReaderVisiblePageBookmarksResolver {
    override fun invalidations(): Flow<Unit> = merge(
        hudEvents.paginationChanges(),
        movements.settled().map { Unit }
    )

    override suspend fun resolve(
        bookmarks: List<ReaderAnnotation.Bookmark>
    ): ReaderVisiblePageBookmarks {
        val candidates = bookmarks.mapNotNull { bookmark ->
            runCatching { EpubCfi(bookmark.cfi) }.getOrNull()?.let { bookmark.id to it }
        }.toMap()
        val visibleIds = when (val outcome = navigator.visiblePointCfis(candidates)) {
            is EpubCfiOutcome.Failure -> emptySet()
            is EpubCfiOutcome.Success -> outcome.value
        }
        return ReaderVisiblePageBookmarks(bookmarks.filter { it.id in visibleIds })
    }
}
