package com.secondpasslibrary.reader.reader.navigation

import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationNavigationResult
import com.secondpasslibrary.reader.reader.annotations.navigateToReaderAnnotation
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal sealed interface ReaderNavigationIntent {
    data class GoToAnnotation(val annotation: ReaderAnnotation) : ReaderNavigationIntent

    data class GoToBookmark(val bookmark: ReaderAnnotation.Bookmark) : ReaderNavigationIntent

    data class GoToPublicationTarget(val target: ReaderPublicationTarget) : ReaderNavigationIntent
}

internal enum class ReaderNavigationResult {
    NAVIGATED,
    INVALID_TARGET,
    UNAVAILABLE,
    REJECTED
}

internal data class ReaderNavigationEvent(
    val intent: ReaderNavigationIntent,
    val result: ReaderNavigationResult
)

/** Routes Reader UI navigation intents through renderer-neutral navigation contracts. */
internal class ReaderNavigationController(
    private val state: StateFlow<ReaderState>,
    private val scope: CoroutineScope
) {
    private val eventChannel = Channel<ReaderNavigationEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun accept(intent: ReaderNavigationIntent) {
        val ready = state.value as? ReaderState.Ready
        if (ready == null) {
            eventChannel.trySend(ReaderNavigationEvent(intent, ReaderNavigationResult.UNAVAILABLE))
            return
        }
        scope.launch {
            val result = when (intent) {
                is ReaderNavigationIntent.GoToAnnotation ->
                    navigateToReaderAnnotation(
                        intent.annotation,
                        ready.engine.cfiNavigator
                    ).toResult()

                is ReaderNavigationIntent.GoToBookmark ->
                    navigateToReaderAnnotation(
                        intent.bookmark,
                        ready.engine.cfiNavigator
                    ).toResult()

                is ReaderNavigationIntent.GoToPublicationTarget ->
                    ready.engine.tableOfContents.goTo(intent.target).toResult()
            }
            eventChannel.trySend(ReaderNavigationEvent(intent, result))
        }
    }
}

private fun ReaderAnnotationNavigationResult.toResult() = when (this) {
    ReaderAnnotationNavigationResult.NAVIGATED -> ReaderNavigationResult.NAVIGATED
    ReaderAnnotationNavigationResult.INVALID_CFI -> ReaderNavigationResult.INVALID_TARGET
    ReaderAnnotationNavigationResult.UNAVAILABLE -> ReaderNavigationResult.UNAVAILABLE
}

private fun ReaderPublicationNavigationResult.toResult() = when (this) {
    ReaderPublicationNavigationResult.NAVIGATED -> ReaderNavigationResult.NAVIGATED
    ReaderPublicationNavigationResult.UNAVAILABLE -> ReaderNavigationResult.UNAVAILABLE
    ReaderPublicationNavigationResult.REJECTED -> ReaderNavigationResult.REJECTED
}
