package com.secondpasslibrary.client.internal.transport

import java.net.URI
import java.util.Locale

internal fun requireLibraryBaseUrl(value: String?, context: String): String {
    val candidate = value?.takeIf { it.isNotBlank() && it == it.trim() } ?: invalidProtocol(context)
    val uri = runCatching { URI(candidate) }.getOrNull() ?: invalidProtocol(context)
    if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) invalidProtocol(context)
    if (uri.host.isNullOrBlank() || uri.userInfo != null) invalidProtocol(context)
    if (uri.rawPath !in setOf("", "/") || uri.rawQuery != null || uri.rawFragment != null) {
        invalidProtocol(context)
    }
    return candidate
}

internal fun apiRootFromLibraryBaseUrl(libraryBaseUrl: String): String {
    val uri = URI(requireLibraryBaseUrl(libraryBaseUrl, "Library base URL"))
    return "${uri.scheme}://${uri.rawAuthority}/api/v1/"
}
