package com.secondpasslibrary.client.internal.transport

import com.secondpasslibrary.client.SplClientException

private val UUID_PATTERN =
    Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )

internal fun requireServerId(value: String?): String =
    value?.takeIf(UUID_PATTERN::matches) ?: throw SplClientException.NotSecondPassServer()

internal fun requireAuthenticatedServerId(value: String?): String =
    value?.takeIf(UUID_PATTERN::matches) ?: invalidProtocol("server info")
