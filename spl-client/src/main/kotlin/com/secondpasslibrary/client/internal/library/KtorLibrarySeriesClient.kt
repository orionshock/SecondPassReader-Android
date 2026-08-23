package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesListOptions
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.decodeProtocolBody
import com.secondpasslibrary.client.internal.transport.previewParameters
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorLibrarySeriesClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibrarySeriesClient {
    override suspend fun list(
        scope: LibraryScope,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> = listAt(scope.path("series/"), options)

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries {
        require(seriesId.isNotBlank()) { "Series ID must not be blank." }
        val response = requests.get(
            "library/series/${seriesId.encodeURLPathPart()}/",
            previewParameters(options.previewLimit)
        )
        return json.decodeProtocolBody<LibrarySeriesWire>(
            response.body(),
            "library series"
        ).toModel()
    }

    private suspend fun listAt(
        path: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            options.tagSlug?.let { add("tag" to it) }
            add("ordering" to options.ordering.queryValue)
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
            addAll(previewParameters(options.previewLimit))
        }
        val response = requests.get(path, parameters)
        return json.decodeProtocolBody<LibrarySeriesPageWire>(
            response.body(),
            "library series page"
        )
            .toModel(options.page, options.pageSize)
    }
}
