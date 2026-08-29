package com.secondpasslibrary.reader.reader.annotations.bookmark

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/** Owns CURRENT-session rendered-page bookmark presence and stale-result rejection. */
internal class ReaderVisiblePageBookmarksController(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(ReaderVisiblePageBookmarks())
    private var owner: Owner? = null
    private var annotations = emptyList<ReaderAnnotation.Bookmark>()
    private var generation = 0L
    private var invalidationJob: Job? = null
    private var resolutionJob: Job? = null

    val state = mutableState.asStateFlow()

    fun select(sessionId: String, resolver: ReaderVisiblePageBookmarksResolver) {
        val next = Owner(sessionId, resolver)
        if (owner == next) return
        clearJobs()
        generation += 1
        owner = next
        annotations = emptyList()
        mutableState.value = ReaderVisiblePageBookmarks()
        invalidationJob = scope.launch {
            resolver.invalidations().onStart { emit(Unit) }.collect { refresh() }
        }
    }

    fun replace(sessionId: String, values: List<ReaderAnnotation>) {
        if (owner?.sessionId != sessionId) return
        val next = values.filterIsInstance<ReaderAnnotation.Bookmark>()
        if (annotations == next) return
        annotations = next
        refresh()
    }

    fun clear() {
        clearJobs()
        generation += 1
        owner = null
        annotations = emptyList()
        mutableState.value = ReaderVisiblePageBookmarks()
    }

    private fun refresh() {
        val activeOwner = owner ?: return
        val activeGeneration = ++generation
        val snapshot = annotations
        resolutionJob?.cancel()
        resolutionJob = scope.launch {
            val result = try {
                activeOwner.resolver.resolve(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReaderVisiblePageBookmarks()
            }
            if (owner == activeOwner && generation == activeGeneration) {
                mutableState.value = result
            }
        }
    }

    private fun clearJobs() {
        invalidationJob?.cancel()
        resolutionJob?.cancel()
        invalidationJob = null
        resolutionJob = null
    }

    private data class Owner(
        val sessionId: String,
        val resolver: ReaderVisiblePageBookmarksResolver
    )
}
