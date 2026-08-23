package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.internal.transport.invalidProtocol

internal inline fun <W, T> marginaliaPage(
    count: Int?,
    next: String?,
    previous: String?,
    results: List<W>?,
    page: Int,
    pageSize: Int,
    context: String,
    mapper: (W) -> T
): MarginaliaPage<T> = MarginaliaPage(
    totalCount = count.nonNegative(context),
    results = results?.map(mapper) ?: invalidProtocol(context),
    hasNext = next != null,
    hasPrevious = previous != null,
    page = page,
    pageSize = pageSize
)

internal fun Int?.nonNegative(context: String): Int {
    val count = this ?: invalidProtocol(context)
    return count.takeIf { it >= 0 } ?: invalidProtocol(context)
}
