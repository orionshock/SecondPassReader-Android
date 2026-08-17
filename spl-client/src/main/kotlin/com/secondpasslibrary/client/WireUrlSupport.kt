package com.secondpasslibrary.client

import java.net.URI
import java.util.Locale

internal fun String?.required(context: String): String =
    this?.trim()?.takeIf(String::isNotEmpty) ?: invalidProtocol(context)

internal fun requireAbsoluteHttpUrl(value: String?, context: String): String =
    absoluteHttpUrlOrNull(value) ?: invalidProtocol(context)

internal fun absoluteHttpUrlOrNull(value: String?): String? {
    val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(candidate) }.getOrNull()
    val scheme = uri?.scheme?.lowercase(Locale.ROOT)
    return candidate.takeIf {
        uri?.isAbsolute == true && scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }
}

internal fun resolveApiUrl(baseUrl: String, relativePath: String): String {
    val base = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
    return URI(base).resolve(relativePath.trimStart('/')).toString()
}

internal fun invalidProtocol(context: String): Nothing =
    throw SplClientException.ProtocolInvalid(context)
