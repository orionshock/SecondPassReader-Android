package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions") // Thin facade exposes the Series child's bounded intents.
internal class LibrarySeriesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
) {
    private val delegate =
        PagedLibraryAxisController(
            clientProvider = clientProvider,
            coroutineScope = scope,
            defaultOrdering = SeriesOrdering.NAME,
            pageLoader = { client, request ->
                val options =
                    SeriesListOptions(
                        q = request.query.takeIf(String::isNotBlank),
                        tagSlug = request.tagSlug,
                        ordering = request.ordering,
                        page = request.page,
                        pageSize = request.pageSize,
                        previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT
                    )
                client.library.series.list(request.scope, options)
            },
            detailLoader = { client, id ->
                client.library.series.getSeries(
                    id,
                    LibraryEntityDetailOptions(previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT)
                )
            }
        )

    val state = delegate.state
    val connectionEvents = delegate.connectionEvents

    fun prepare(profile: ConnectionProfile, scope: LibraryScope, tagSlug: String? = null) =
        delegate.prepare(profile, scope, tagSlug)

    fun activate() = delegate.activate()

    fun selectScope(scope: LibraryScope, activate: Boolean, tagSlug: String? = null) =
        delegate.selectScope(scope, activate, tagSlug)

    fun selectTag(tagSlug: String?, activate: Boolean) = delegate.selectTag(tagSlug, activate)

    fun commitSearch(query: String) = delegate.commitSearch(query)

    fun changeOrdering(ordering: SeriesOrdering) = delegate.changeOrdering(ordering)

    fun loadNextPage() = delegate.loadNextPage()

    fun retry() = delegate.retry()

    fun selectSeries(seriesId: String) = delegate.select(seriesId)

    fun retryDetail() = delegate.retryDetail()

    fun clearSelection() = delegate.clearSelection()

    fun close() = delegate.close()
}

internal typealias LibrarySeriesState = PagedLibraryAxisState<LibrarySeries, SeriesOrdering>
