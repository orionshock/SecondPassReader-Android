package com.secondpasslibrary.client

import java.net.URI
import java.util.Locale

@JvmInline
value class ServerOrigin private constructor(val value: String) {
    companion object {
        fun fromUserInput(input: String): ServerOrigin {
            val candidate = input.trim().takeIf(String::isNotEmpty) ?: invalidServerUrl()

            val withScheme = if (candidate.contains("://")) candidate else "https://$candidate"
            val uri = runCatching { URI(withScheme) }.getOrElse { invalidServerUrl() }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT)
            if (scheme !in setOf("http", "https") || host.isNullOrBlank() || uri.userInfo != null) {
                invalidServerUrl()
            }

            val defaultPort =
                (scheme == "http" && uri.port == HTTP_DEFAULT_PORT) ||
                    (scheme == "https" && uri.port == HTTPS_DEFAULT_PORT)
            val port = if (uri.port >= 0 && !defaultPort) ":${uri.port}" else ""
            return ServerOrigin("$scheme://$host$port")
        }

        private fun invalidServerUrl(): Nothing = throw SplClientException.InvalidServerUrl()

        private const val HTTP_DEFAULT_PORT = 80
        private const val HTTPS_DEFAULT_PORT = 443
    }

    fun endpoint(path: String): String = "$value/${path.trimStart('/')}"
}
