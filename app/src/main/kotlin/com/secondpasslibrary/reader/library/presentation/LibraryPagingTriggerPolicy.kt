package com.secondpasslibrary.reader.library.presentation

internal object LibraryPagingTriggerPolicy {
    fun shouldRequestNextPage(
        lastVisibleIndex: Int,
        itemCount: Int,
        prefetchDistance: Int = 6
    ): Boolean =
        itemCount > 0 && lastVisibleIndex >= (itemCount - prefetchDistance).coerceAtLeast(0)
}
