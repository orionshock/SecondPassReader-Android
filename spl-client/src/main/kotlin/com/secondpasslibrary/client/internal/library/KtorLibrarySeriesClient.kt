package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.CatalogResultPage
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.previewParameters
import io.ktor.http.encodeURLPathPart

internal class KtorLibrarySeriesClient(private val requests: AuthenticatedRequestExecutor) :
    AuthenticatedLibrarySeriesClient {
    override suspend fun list(
        scope: LibraryScope,
        options: SeriesListOptions
    ): CatalogResultPage<LibrarySeries> = listAt(scope.path("series/"), options)

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries {
        require(seriesId.isNotBlank()) { "Series ID must not be blank." }
        return requests.getDecoded<LibrarySeriesWire>(
            "library/series/${seriesId.encodeURLPathPart()}/",
            previewParameters(options.previewLimit),
            "library series"
        ).toModel()
    }

    private suspend fun listAt(
        path: String,
        options: SeriesListOptions
    ): CatalogResultPage<LibrarySeries> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            options.tagSlug?.let { add("tag" to it) }
            add("ordering" to options.ordering.queryValue)
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
            addAll(previewParameters(options.previewLimit))
        }
        return requests.getDecoded<LibrarySeriesPageWire>(path, parameters, "library series page")
            .toModel(options.page, options.pageSize)
    }
}
