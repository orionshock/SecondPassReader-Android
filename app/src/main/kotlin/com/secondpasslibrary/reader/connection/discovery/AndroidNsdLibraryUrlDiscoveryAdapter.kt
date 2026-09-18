package com.secondpasslibrary.reader.connection.discovery

import com.secondpasslibrary.client.ServerOrigin
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class AndroidNsdLibraryUrlDiscoveryAdapter(private val browser: NsdBrowser) :
    LanLibraryUrlDiscovery {
    override fun candidateUrls(): Flow<Set<String>> = callbackFlow {
        val presentServices = mutableSetOf<String>()
        val serviceUrls = mutableMapOf<String, CandidateUrl>()
        val listener =
            object : NsdBrowserListener {
                override fun onServiceFound(serviceKey: String) {
                    synchronized(serviceUrls) { presentServices += serviceKey }
                }

                override fun onServiceResolved(
                    serviceKey: String,
                    attributes: Map<String, ByteArray>
                ) {
                    val candidates = synchronized(serviceUrls) {
                        if (serviceKey !in presentServices) return@synchronized null
                        val candidate = attributes[URL_ATTRIBUTE]?.decodeCandidateUrl()
                        if (candidate == null) {
                            serviceUrls.remove(serviceKey)
                        } else {
                            serviceUrls[serviceKey] = candidate
                        }
                        serviceUrls.values.distinctCandidates()
                    }
                    candidates?.let { trySend(it) }
                }

                override fun onServiceLost(serviceKey: String) {
                    val candidates = synchronized(serviceUrls) {
                        presentServices -= serviceKey
                        serviceUrls.remove(serviceKey)
                        serviceUrls.values.distinctCandidates()
                    }
                    trySend(candidates)
                }
            }
        trySend(emptySet())
        val session = browser.browse(SERVICE_TYPE, listener)
        awaitClose { session.close() }
    }

    private companion object {
        const val SERVICE_TYPE = "_secondpass._tcp"
        const val URL_ATTRIBUTE = "url"
    }
}

private data class CandidateUrl(val value: String, val deduplicationKey: String)

private fun ByteArray.decodeCandidateUrl(): CandidateUrl? {
    val value = toString(Charsets.UTF_8).trim()
    val uri = runCatching { URI(value) }.getOrNull()
    val valid =
        uri?.isAbsolute == true &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.scheme.lowercase(Locale.ROOT) in setOf("http", "https")
    val key =
        if (valid) {
            runCatching { ServerOrigin.fromUserInput(value).value }.getOrNull()
        } else {
            null
        }
    return key?.let { CandidateUrl(value, it) }
}

private fun Collection<CandidateUrl>.distinctCandidates(): Set<String> =
    groupBy(CandidateUrl::deduplicationKey)
        .values
        .map { candidates -> candidates.minOf(CandidateUrl::value) }
        .toSortedSet()
