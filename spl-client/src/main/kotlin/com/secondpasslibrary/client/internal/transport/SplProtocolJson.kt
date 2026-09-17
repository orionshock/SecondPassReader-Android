package com.secondpasslibrary.client.internal.transport

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal val splProtocolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    exceptionsWithDebugInfo = false
}
