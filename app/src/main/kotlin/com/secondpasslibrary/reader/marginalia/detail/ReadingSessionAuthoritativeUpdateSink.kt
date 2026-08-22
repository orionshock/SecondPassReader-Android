package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.client.ReadingSessionDetailResult

internal fun interface ReadingSessionAuthoritativeUpdateSink {
    fun onReadingSessionUpdated(detail: ReadingSessionDetailResult)
}
