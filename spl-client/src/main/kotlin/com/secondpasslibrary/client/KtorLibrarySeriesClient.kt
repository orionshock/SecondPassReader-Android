package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.LibrarySeriesPageWire
import com.secondpasslibrary.client.internal.LibrarySeriesWire
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorLibrarySeriesClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibrarySeriesClient {
    override suspend fun listSeries(options: SeriesListOptions): LibraryPage<LibrarySeries> =
        listAt("library/series/", options)

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries {
        require(seriesId.isNotBlank()) { "Series ID must not be blank." }
        val response = requests.get(
            "library/series/${seriesId.encodeURLPathPart()}/",
            previewParameters(options.previewLimit)
        )
        return json.decodeLibrary<LibrarySeriesWire>(response.body(), "library series").toModel()
    }

    override suspend fun listGroupSeries(
        groupId: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> {
        require(groupId.isNotBlank()) { "Library group ID must not be blank." }
        return listAt("library/groups/${groupId.encodeURLPathPart()}/series/", options)
    }

    private suspend fun listAt(
        path: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            add("ordering" to options.ordering.queryValue)
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
            addAll(previewParameters(options.previewLimit))
        }
        val response = requests.get(path, parameters)
        return json.decodeLibrary<LibrarySeriesPageWire>(response.body(), "library series page")
            .toModel(options.page, options.pageSize)
    }
}
