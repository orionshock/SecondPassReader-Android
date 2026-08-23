package com.secondpasslibrary.client.internal.transport

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal inline fun <reified T> Json.decodeProtocolBody(body: String, context: String): T = try {
    decodeFromString<T>(body)
} catch (_: SerializationException) {
    invalidProtocol(context)
} catch (_: IllegalArgumentException) {
    invalidProtocol(context)
} catch (_: IOException) {
    invalidProtocol(context)
}
