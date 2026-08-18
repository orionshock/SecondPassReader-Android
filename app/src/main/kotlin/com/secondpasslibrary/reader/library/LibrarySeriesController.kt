package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CoroutineScope

internal class LibrarySeriesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
) {
    private val delegate =
        LibraryEntityController(
            clientProvider = clientProvider,
            coroutineScope = scope,
            defaultOrdering = SeriesOrdering.NAME,
            pageLoader = { client, request ->
                val options =
                    SeriesListOptions(
                        q = request.query.takeIf(String::isNotBlank),
                        ordering = request.ordering,
                        page = request.page,
                        pageSize = request.pageSize,
                        previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT
                    )
                when (val selectedScope = request.scope) {
                    LibraryScope.AllLibrary -> client.library.series.listSeries(options)

                    is LibraryScope.Group ->
                        client.library.series.listGroupSeries(selectedScope.id, options)
                }
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

    fun prepare(profile: ConnectionProfile, scope: LibraryScope) = delegate.prepare(profile, scope)

    fun activate() = delegate.activate()

    fun selectScope(scope: LibraryScope, activate: Boolean) = delegate.selectScope(scope, activate)

    fun commitSearch(query: String) = delegate.commitSearch(query)

    fun changeOrdering(ordering: SeriesOrdering) = delegate.changeOrdering(ordering)

    fun loadNextPage() = delegate.loadNextPage()

    fun retry() = delegate.retry()

    fun selectSeries(seriesId: String) = delegate.select(seriesId)

    fun retryDetail() = delegate.retryDetail()

    fun close() = delegate.close()
}

internal typealias LibrarySeriesState = LibraryEntityState<LibrarySeries, SeriesOrdering>
