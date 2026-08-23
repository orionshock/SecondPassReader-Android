package com.secondpasslibrary.client.internal.transport

internal fun previewParameters(previewLimit: Int): List<Pair<String, String>> =
    if (previewLimit > 0) {
        listOf(
            "include_preview_books" to "true",
            "preview_limit" to previewLimit.toString()
        )
    } else {
        emptyList()
    }
