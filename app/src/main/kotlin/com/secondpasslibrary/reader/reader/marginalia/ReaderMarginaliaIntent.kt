package com.secondpasslibrary.reader.reader.marginalia

internal sealed interface ReaderMarginaliaIntent {
    data object RetryCurrentAnnotations : ReaderMarginaliaIntent
    data class LoadPreviousLayer(val sessionId: String) : ReaderMarginaliaIntent
    data class SetPreviousLayerVisible(val sessionId: String, val visible: Boolean) :
        ReaderMarginaliaIntent
    data object ShowAllPreviousLayers : ReaderMarginaliaIntent
    data object HideAllPreviousLayers : ReaderMarginaliaIntent
    data class SetAutoShowPrevious(val enabled: Boolean) : ReaderMarginaliaIntent
    data object LoadMoreLayers : ReaderMarginaliaIntent
    data object RetryLayerHistory : ReaderMarginaliaIntent
    data object EditCurrentSessionMetadata : ReaderMarginaliaIntent
    data class ChangeCurrentSessionName(val name: String) : ReaderMarginaliaIntent
    data class ChangeCurrentSessionNotes(val notes: String) : ReaderMarginaliaIntent
    data object SaveCurrentSessionMetadata : ReaderMarginaliaIntent
    data object DismissCurrentSessionMetadataEditor : ReaderMarginaliaIntent
}
