package com.secondpasslibrary.client.internal.transport

import com.secondpasslibrary.client.SplClientException
import java.net.URI
import java.util.Locale

private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443

internal fun String?.required(context: String): String =
    this?.trim()?.takeIf(String::isNotEmpty) ?: invalidProtocol(context)

internal fun String?.requiredOpaque(context: String): String =
    this?.takeIf(String::isNotBlank) ?: invalidProtocol(context)

internal fun String?.boundedOpaque(maxLength: Int, context: String): String =
    requiredOpaque(context).takeIf { it.length <= maxLength } ?: invalidProtocol(context)

internal fun String?.boundedNullable(maxLength: Int, context: String): String? =
    this?.takeIf { it.length <= maxLength } ?: if (this == null) null else invalidProtocol(context)

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

internal fun requireSameOriginHttpUrl(
    value: String,
    trustedBaseUrl: String,
    context: String
): String {
    val candidate = URI(requireAbsoluteHttpUrl(value, context))
    val trusted = URI(requireAbsoluteHttpUrl(trustedBaseUrl, context))
    if (candidate.scheme.equals(trusted.scheme, ignoreCase = true) &&
        candidate.host.equals(trusted.host, ignoreCase = true) &&
        candidate.effectivePort() == trusted.effectivePort()
    ) {
        return candidate.toString()
    }
    invalidProtocol(context)
}

private fun URI.effectivePort(): Int = when {
    port >= 0 -> port
    scheme.equals("https", ignoreCase = true) -> DEFAULT_HTTPS_PORT
    else -> DEFAULT_HTTP_PORT
}

internal fun invalidProtocol(context: String): Nothing =
    throw SplClientException.ProtocolInvalid(context)
