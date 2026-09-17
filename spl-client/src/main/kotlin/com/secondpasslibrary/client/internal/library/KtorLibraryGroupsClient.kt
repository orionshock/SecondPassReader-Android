package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor

internal class KtorLibraryGroupsClient(private val requests: AuthenticatedRequestExecutor) :
    AuthenticatedLibraryGroupsClient {
    override suspend fun listGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> {
        val parameters =
            listOf(
                "ordering" to options.ordering.queryValue,
                "page" to options.page.toString(),
                "page_size" to options.pageSize.toString()
            )
        return requests.getDecoded<LibraryGroupPageWire>(
            "library/groups/",
            parameters,
            "library group page"
        )
            .toModel(options.page, options.pageSize)
    }
}
