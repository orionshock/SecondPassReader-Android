package com.secondpasslibrary.reader.reader.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ReaderSearchState(
    val active: Boolean = false,
    val query: String = "",
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val results: List<ReaderSearchResult> = emptyList(),
    val error: Boolean = false,
    val selected: ReaderSearchTarget? = null
)

/** Owns one publication's disposable search interaction and supersedes stale queries. */
internal class ReaderSearchController(
    private val search: ReaderBookSearch,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 300
) {
    private val mutableState = MutableStateFlow(ReaderSearchState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var navigationJob: Job? = null
    private var generation = 0L
    private var nextPage = Channel<Unit>(Channel.CONFLATED)

    fun open() {
        mutableState.value = mutableState.value.copy(active = true)
    }

    // Query generation, pagination and cancellation share one lifetime.
    @Suppress("CognitiveComplexMethod")
    fun query(value: String) {
        job?.cancel()
        nextPage.close()
        nextPage = Channel(Channel.CONFLATED)
        val requests = nextPage
        val current = ++generation
        val trimmed = value.trim()
        mutableState.value = mutableState.value.copy(
            query = value,
            loading = trimmed.isNotEmpty(),
            hasMore = trimmed.isNotEmpty(),
            results = emptyList(),
            error = false,
            selected = null
        )
        if (trimmed.isEmpty() || !mutableState.value.active) return
        job = scope.launch {
            delay(debounceMillis)
            try {
                var first = true
                search.search(trimmed).collect { page ->
                    if (!first) requests.receive()
                    first = false
                    if (current == generation && mutableState.value.active) {
                        mutableState.value = mutableState.value.copy(
                            results = mutableState.value.results + page,
                            loading = false,
                            hasMore = true
                        )
                    }
                }
                if (current == generation) {
                    mutableState.value = mutableState.value.copy(loading = false, hasMore = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) {
                    mutableState.value =
                        mutableState.value.copy(loading = false, hasMore = false, error = true)
                }
            }
        }
    }

    fun loadMore() {
        if (!mutableState.value.active || !mutableState.value.hasMore ||
            mutableState.value.loading
        ) {
            return
        }
        mutableState.value = mutableState.value.copy(loading = true)
        nextPage.trySend(Unit)
    }

    fun select(result: ReaderSearchResult) {
        if (!mutableState.value.active || result !in mutableState.value.results) return
        navigationJob?.cancel()
        val current = generation
        navigationJob = scope.launch {
            if (search.goTo(result.target) && current == generation && mutableState.value.active) {
                mutableState.value = mutableState.value.copy(selected = result.target)
            }
        }
    }

    fun close() {
        generation += 1
        job?.cancel()
        nextPage.close()
        navigationJob?.cancel()
        mutableState.value = ReaderSearchState()
    }
}
