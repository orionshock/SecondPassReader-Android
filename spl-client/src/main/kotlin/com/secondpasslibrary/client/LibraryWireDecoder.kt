package com.secondpasslibrary.client

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal inline fun <reified T> Json.decodeLibrary(body: String, context: String): T = try {
    decodeFromString<T>(body)
} catch (_: SerializationException) {
    invalidProtocol(context)
} catch (_: IllegalArgumentException) {
    invalidProtocol(context)
} catch (_: IOException) {
    invalidProtocol(context)
}

internal fun previewParameters(previewLimit: Int): List<Pair<String, String>> =
    if (previewLimit > 0) {
        listOf(
            "include_preview_books" to "true",
            "preview_limit" to previewLimit.toString()
        )
    } else {
        emptyList()
    }
