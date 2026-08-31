package com.secondpasslibrary.reader.reader.annotations.bookmark

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal data class ReaderVisiblePageBookmarks(
    val bookmarks: List<ReaderAnnotation.Bookmark> = emptyList()
)

internal sealed interface ReaderBookmarkHudIntent {
    data object Create : ReaderBookmarkHudIntent

    data class Remove(val bookmark: ReaderAnnotation.Bookmark) : ReaderBookmarkHudIntent
}

/** Resolves disposable rendered-page presence without changing durable bookmark identity. */
internal interface ReaderVisiblePageBookmarksResolver {
    fun invalidations(): Flow<Unit>

    suspend fun resolve(bookmarks: List<ReaderAnnotation.Bookmark>): ReaderVisiblePageBookmarks
}

internal object EmptyReaderVisiblePageBookmarksResolver : ReaderVisiblePageBookmarksResolver {
    override fun invalidations(): Flow<Unit> = emptyFlow()

    override suspend fun resolve(
        bookmarks: List<ReaderAnnotation.Bookmark>
    ): ReaderVisiblePageBookmarks = ReaderVisiblePageBookmarks()
}
