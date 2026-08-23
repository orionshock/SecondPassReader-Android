package com.secondpasslibrary.reader.marginalia.books

import com.secondpasslibrary.client.MarginaliaBookSummary
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.history.MarginaliaLoadError

internal data class MarginaliaBooksState(
    val committedQuery: String = "",
    val books: List<MarginaliaBookSummary> = emptyList(),
    val totalCount: Int = 0,
    val pageSize: Int = 20,
    val currentPage: Int = 0,
    val hasNext: Boolean = false,
    val initialLoading: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: MarginaliaLoadError? = null
)

internal fun MarginaliaBooksState.initialFailure(): MarginaliaFailure? =
    error?.takeIf { books.isEmpty() }?.failure
