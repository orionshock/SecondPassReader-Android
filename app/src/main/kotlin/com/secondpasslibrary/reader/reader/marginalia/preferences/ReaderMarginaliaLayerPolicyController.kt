package com.secondpasslibrary.reader.reader.marginalia.preferences

import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerLoadState
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerVisibility
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersController
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Applies local startup/visibility policy through the existing layer controller. */
internal class ReaderMarginaliaLayerPolicyController(
    private val preferenceStore: ReaderMarginaliaLayerPreferenceStore,
    private val visibilityStore: ReaderMarginaliaLayerVisibilityStore,
    private val layers: ReaderMarginaliaLayersController,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    concurrency: Int = AUTO_LAYER_LOAD_CONCURRENCY
) {
    private val loadPermits = Semaphore(concurrency)
    private val layerJobs = mutableMapOf<String, Job>()
    private val desiredVisibility = mutableMapOf<String, Boolean>()
    private var context: PolicyContext? = null
    private var authorityAvailable = true
    private var generation = 0L
    private var preferenceJob: Job? = null

    init {
        scope.launch { layers.state.collect(::layersChanged) }
    }

    fun select(connectionIdentity: AuthenticatedConnectionIdentity, bookId: String) {
        val next = PolicyContext(ReaderMarginaliaVisibilityScope(connectionIdentity, bookId))
        if (context?.scope == next.scope) return
        clearPolicyWork()
        context = next
        val activeGeneration = generation
        preferenceJob = scope.launch {
            val defaultVisible = runCatching { preferenceStore.readAutoShowPrevious() }
                .getOrDefault(DEFAULT_AUTO_SHOW_PREVIOUS)
            if (activeGeneration != generation) return@launch
            context = next.copy(defaultVisible = defaultVisible, ready = true)
            layersChanged(layers.state.value)
        }
    }

    fun setAuthorityAvailable(available: Boolean) {
        authorityAvailable = available
    }

    fun loadLayer(sessionId: String) = layers.loadLayer(sessionId)

    fun setVisible(sessionId: String, visible: Boolean) {
        val activeContext = context?.takeIf(PolicyContext::ready) ?: return
        if (layers.state.value.previousLayers.none { it.summary.sessionId == sessionId }) return
        desiredVisibility[sessionId] = visible
        scope.launch {
            runCatching {
                visibilityStore.write(activeContext.scope, sessionId, visible, clock.instant())
            }
            if (context != activeContext) return@launch
            if (visible) {
                ensureVisible(sessionId, generation)
            } else {
                layers.setLayerVisible(
                    sessionId,
                    false
                )
            }
        }
    }

    fun setAllVisible(visible: Boolean) {
        layers.state.value.previousLayers.forEach {
            setVisible(it.summary.sessionId, visible)
        }
    }

    fun clear() {
        clearPolicyWork()
        context = null
    }

    private fun layersChanged(state: ReaderMarginaliaLayersState) {
        val activeContext = context?.takeIf(PolicyContext::ready) ?: return
        state.previousLayers.forEach { layer ->
            val sessionId = layer.summary.sessionId
            if (sessionId !in desiredVisibility && sessionId !in layerJobs) {
                resolveStartupVisibility(activeContext, sessionId)
            } else if (
                desiredVisibility[sessionId] == true &&
                layer.loadState == ReaderMarginaliaLayerLoadState.LOADED &&
                layer.visibility == ReaderMarginaliaLayerVisibility.HIDDEN
            ) {
                layers.setLayerVisible(sessionId, true)
            }
        }
    }

    private fun resolveStartupVisibility(activeContext: PolicyContext, sessionId: String) {
        val activeGeneration = generation
        layerJobs[sessionId] = scope.launch {
            val cached = runCatching {
                visibilityStore.read(activeContext.scope, sessionId, clock.instant())
            }.getOrNull()
            if (activeGeneration != generation) return@launch
            val desired = cached ?: activeContext.defaultVisible
            desiredVisibility[sessionId] = desired
            if (desired && authorityAvailable) ensureVisible(sessionId, activeGeneration)
            layerJobs.remove(sessionId)
        }
    }

    private suspend fun ensureVisible(sessionId: String, activeGeneration: Long) {
        if (!authorityAvailable || activeGeneration != generation) return
        loadPermits.withPermit {
            val current = layers.state.value.previousLayers
                .find { it.summary.sessionId == sessionId } ?: return@withPermit
            if (current.loadState != ReaderMarginaliaLayerLoadState.LOADED) {
                layers.loadLayer(sessionId)
                layers.state.map { state ->
                    state.previousLayers.find { it.summary.sessionId == sessionId }
                }.first { layer -> layer == null || layer.loadState.isTerminal }
            }
        }
        if (shouldShow(sessionId, activeGeneration)) {
            layers.setLayerVisible(sessionId, true)
        }
    }

    private fun shouldShow(sessionId: String, activeGeneration: Long): Boolean {
        val layer = layers.state.value.previousLayers.find { it.summary.sessionId == sessionId }
        return activeGeneration == generation && authorityAvailable &&
            desiredVisibility[sessionId] == true &&
            layer?.loadState == ReaderMarginaliaLayerLoadState.LOADED
    }

    private fun clearPolicyWork() {
        generation += 1
        preferenceJob?.cancel()
        preferenceJob = null
        layerJobs.values.forEach(Job::cancel)
        layerJobs.clear()
        desiredVisibility.clear()
    }

    private data class PolicyContext(
        val scope: ReaderMarginaliaVisibilityScope,
        val defaultVisible: Boolean = DEFAULT_AUTO_SHOW_PREVIOUS,
        val ready: Boolean = false
    )
}

private val ReaderMarginaliaLayerLoadState.isTerminal: Boolean
    get() = this == ReaderMarginaliaLayerLoadState.LOADED ||
        this == ReaderMarginaliaLayerLoadState.FAILED

internal const val AUTO_LAYER_LOAD_CONCURRENCY = 3
