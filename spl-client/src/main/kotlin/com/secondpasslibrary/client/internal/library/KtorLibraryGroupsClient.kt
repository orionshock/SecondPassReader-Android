package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.decodeProtocolBody
import io.ktor.client.call.body
import kotlinx.serialization.json.Json

internal class KtorLibraryGroupsClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibraryGroupsClient {
    override suspend fun listGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> {
        val parameters =
            listOf(
                "ordering" to options.ordering.queryValue,
                "page" to options.page.toString(),
                "page_size" to options.pageSize.toString()
            )
        val response = requests.get("library/groups/", parameters)
        return json.decodeProtocolBody<LibraryGroupPageWire>(response.body(), "library group page")
            .toModel(options.page, options.pageSize)
    }
}
