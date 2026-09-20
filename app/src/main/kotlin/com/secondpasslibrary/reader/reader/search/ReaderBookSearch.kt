package com.secondpasslibrary.reader.reader.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Ephemeral locations belong to one open publication, never to saved reader progress. */
internal interface ReaderSearchTarget

internal data class ReaderSearchResult(
    val target: ReaderSearchTarget,
    val before: String,
    val match: String,
    val after: String,
    val title: String?
)

internal interface ReaderBookSearch {
    fun search(query: String): Flow<List<ReaderSearchResult>>
    suspend fun goTo(target: ReaderSearchTarget): Boolean
}

internal object EmptyReaderBookSearch : ReaderBookSearch {
    override fun search(query: String): Flow<List<ReaderSearchResult>> = emptyFlow()
    override suspend fun goTo(target: ReaderSearchTarget): Boolean = false
}
