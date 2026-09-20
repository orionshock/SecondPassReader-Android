package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.readium.viewport.ReadiumPublicationNavigatorBinding
import com.secondpasslibrary.reader.reader.search.ReaderBookSearch
import com.secondpasslibrary.reader.reader.search.ReaderSearchResult
import com.secondpasslibrary.reader.reader.search.ReaderSearchTarget
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search

/** Keeps Readium's iterator, locators and publication identity inside the renderer boundary. */
@OptIn(ExperimentalReadiumApi::class)
internal class ReadiumReaderBookSearch(
    private val publication: Publication,
    private val binding: ReadiumPublicationNavigatorBinding
) : ReaderBookSearch {
    override fun search(query: String): Flow<List<ReaderSearchResult>> = flow {
        val iterator = publication.search(query)
            ?: throw IllegalStateException("This publication does not support text search")
        try {
            while (true) {
                coroutineContext.ensureActive()
                val page = iterator.next()
                page.failureOrNull()?.let {
                    throw IllegalStateException("Publication search failed: ${it.message}")
                }
                val locators = page.getOrNull()?.locators ?: break
                if (locators.isNotEmpty()) {
                    emit(
                        locators.map { locator ->
                            val text = locator.text
                            ReaderSearchResult(
                                target = ReadiumSearchTarget(publication, locator),
                                before = text.before.orEmpty().cleanSnippet(),
                                match = text.highlight.orEmpty().cleanSnippet(),
                                after = text.after.orEmpty().cleanSnippet(),
                                title = locator.title
                            )
                        }
                    )
                }
            }
        } finally {
            iterator.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun goTo(target: ReaderSearchTarget): Boolean {
        val readium = target as? ReadiumSearchTarget
        return readium != null && readium.publication === publication &&
            binding.goTo(readium.locator) == ReaderPublicationNavigationResult.NAVIGATED
    }
}

private class ReadiumSearchTarget(val publication: Publication, val locator: Locator) :
    ReaderSearchTarget

private fun String.cleanSnippet(): String = replace(Regex("\\s+"), " ").trim()
