package com.secondpasslibrary.reader.reader.marginalia

internal sealed interface ReaderMarginaliaIntent {
    data object RetryCurrentAnnotations : ReaderMarginaliaIntent
    data class LoadPreviousLayer(val sessionId: String) : ReaderMarginaliaIntent
    data class SetPreviousLayerVisible(val sessionId: String, val visible: Boolean) :
        ReaderMarginaliaIntent
    data object LoadMoreLayers : ReaderMarginaliaIntent
    data object RetryLayerHistory : ReaderMarginaliaIntent
}
