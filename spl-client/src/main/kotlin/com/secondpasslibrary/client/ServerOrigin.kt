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

            val port = if (uri.port >= 0) ":${uri.port}" else ""
            return ServerOrigin("$scheme://$host$port")
        }

        private fun invalidServerUrl(): Nothing = throw SplClientException.InvalidServerUrl()
    }

    fun endpoint(path: String): String = "$value/${path.trimStart('/')}"
}
