package com.secondpasslibrary.reader.reader.navigation

import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationNavigationResult
import com.secondpasslibrary.reader.reader.annotations.navigateToReaderAnnotation
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal sealed interface ReaderNavigationIntent {
    data class GoToAnnotation(val annotation: ReaderAnnotation) : ReaderNavigationIntent

    data class GoToBookmark(val bookmark: ReaderAnnotation.Bookmark) : ReaderNavigationIntent

    data class GoToPublicationTarget(val target: ReaderPublicationTarget) : ReaderNavigationIntent
}

/** Routes Reader UI navigation intents through renderer-neutral navigation contracts. */
internal class ReaderNavigationController(
    private val state: StateFlow<ReaderState>,
    private val scope: CoroutineScope
) {
    private val mutableFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val failures = mutableFailures.asSharedFlow()
    private var operation: Job? = null

    fun accept(intent: ReaderNavigationIntent) {
        val ready = state.value as? ReaderState.Ready ?: return
        operation?.cancel()
        operation = scope.launch {
            val outcome = navigateWhileCurrent(intent, ready)
            if (outcome == CurrentNavigationOutcome.FAILED && ready.isCurrent()) {
                mutableFailures.emit(Unit)
            }
        }
    }

    fun close() {
        operation?.cancel()
        operation = null
    }

    private suspend fun navigateWhileCurrent(
        intent: ReaderNavigationIntent,
        ready: ReaderState.Ready
    ): CurrentNavigationOutcome = coroutineScope {
        val navigation = async {
            try {
                navigate(intent, ready)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CurrentNavigationOutcome.FAILED
            }
        }
        val engineLifetime = launch {
            state.first { current ->
                (current as? ReaderState.Ready)?.engine !== ready.engine
            }
            navigation.cancel()
        }
        try {
            navigation.await()
        } finally {
            engineLifetime.cancel()
        }
    }

    private suspend fun navigate(
        intent: ReaderNavigationIntent,
        ready: ReaderState.Ready
    ): CurrentNavigationOutcome = when (intent) {
        is ReaderNavigationIntent.GoToAnnotation ->
            navigateToReaderAnnotation(
                intent.annotation,
                ready.engine.cfiNavigator
            ).toOutcome()

        is ReaderNavigationIntent.GoToBookmark ->
            navigateToReaderAnnotation(
                intent.bookmark,
                ready.engine.cfiNavigator
            ).toOutcome()

        is ReaderNavigationIntent.GoToPublicationTarget ->
            ready.engine.tableOfContents.goTo(intent.target).toOutcome()
    }

    private fun ReaderState.Ready.isCurrent() =
        (state.value as? ReaderState.Ready)?.engine === engine
}

private enum class CurrentNavigationOutcome {
    SUCCEEDED,
    FAILED
}

private fun ReaderAnnotationNavigationResult.toOutcome() = when (this) {
    ReaderAnnotationNavigationResult.NAVIGATED -> CurrentNavigationOutcome.SUCCEEDED

    ReaderAnnotationNavigationResult.INVALID_CFI,
    ReaderAnnotationNavigationResult.UNAVAILABLE -> CurrentNavigationOutcome.FAILED
}

private fun ReaderPublicationNavigationResult.toOutcome() = when (this) {
    ReaderPublicationNavigationResult.NAVIGATED -> CurrentNavigationOutcome.SUCCEEDED

    ReaderPublicationNavigationResult.UNAVAILABLE,
    ReaderPublicationNavigationResult.REJECTED -> CurrentNavigationOutcome.FAILED
}
