package com.secondpasslibrary.reader.connection.discovery

import kotlinx.coroutines.flow.Flow

internal fun interface LanLibraryUrlDiscovery {
    fun candidateUrls(): Flow<Set<String>>
}
