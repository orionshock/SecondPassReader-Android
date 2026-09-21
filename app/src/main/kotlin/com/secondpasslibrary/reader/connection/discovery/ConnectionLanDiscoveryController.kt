package com.secondpasslibrary.reader.connection.discovery

import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.SecondPassClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ConnectionLanDiscoveryController(
    private val discovery: LanLibraryUrlDiscovery,
    private val client: SecondPassClient,
    private val scope: CoroutineScope
) {
    private val mutableSuggestions =
        MutableStateFlow<List<ConnectionLibrarySuggestion>>(emptyList())
    val suggestions: StateFlow<List<ConnectionLibrarySuggestion>> =
        mutableSuggestions.asStateFlow()
    private var discoveryJob: Job? = null
    private var validationJob: Job? = null
    private var generation = 0L
    private var validationRevision = 0L

    fun start() {
        if (discoveryJob?.isActive == true) return
        val activeGeneration = ++generation
        discoveryJob = scope.launch {
            discovery.candidateUrls().collect { urls ->
                validate(urls, activeGeneration)
            }
        }
    }

    fun stop() {
        generation += 1
        validationRevision += 1
        discoveryJob?.cancel()
        discoveryJob = null
        validationJob?.cancel()
        validationJob = null
        mutableSuggestions.value = emptyList()
    }

    private fun validate(urls: Set<String>, activeGeneration: Long) {
        validationJob?.cancel()
        val activeRevision = ++validationRevision
        validationJob = scope.launch {
            val validated = coroutineScope {
                urls.sorted().map { url -> async { validate(url) } }.awaitAll().filterNotNull()
            }
            if (generation == activeGeneration && validationRevision == activeRevision) {
                mutableSuggestions.value = validated.deduplicateServers()
            }
        }
    }

    private suspend fun validate(url: String): ConnectionLibrarySuggestion? = try {
        client.discoverServer(url).toSuggestion(url)
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        null
    }
}

private fun DiscoveredServer.toSuggestion(url: String) = ConnectionLibrarySuggestion(
    serverId = serverId,
    name = name,
    description = description,
    url = url
)

private fun List<ConnectionLibrarySuggestion>.deduplicateServers() =
    groupBy(ConnectionLibrarySuggestion::serverId)
        .values
        .map { suggestions -> suggestions.minBy(ConnectionLibrarySuggestion::url) }
        .sortedWith(compareBy(ConnectionLibrarySuggestion::name, ConnectionLibrarySuggestion::url))
