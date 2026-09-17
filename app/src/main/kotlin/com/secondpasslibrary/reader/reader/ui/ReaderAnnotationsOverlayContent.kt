package com.secondpasslibrary.reader.reader.ui

import androidx.compose.runtime.Composable
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus

/** Wires current-session annotation mutation intent into the marginalia overlay. */
@Composable
internal fun ReaderAnnotationsOverlayContent(
    ready: ReaderState.Ready?,
    annotations: ReaderAnnotationsState,
    layers: ReaderMarginaliaLayersState,
    autoShowPrevious: Boolean,
    palette: ReaderPalette,
    dismiss: () -> Unit,
    onMarginaliaIntent: (ReaderMarginaliaIntent) -> Unit,
    annotationWritesAvailable: Boolean,
    sessionMetadataEditable: Boolean,
    mutationState: ReaderAnnotationMutationState,
    sessionMetadata: ReaderSessionMetadataState,
    onCreateBookmark: () -> Unit,
    onNavigateAnnotation: (ReaderAnnotation) -> Unit,
    onMutation: (ReaderAnnotationMutationIntent) -> Unit
) {
    ReaderAnnotationsOverlay(
        ready = ready,
        state = annotations,
        layers = layers,
        autoShowPrevious = autoShowPrevious,
        drawerState = rememberMarginaliaDrawerState(
            ready?.let { it.session.sessionId }.orEmpty(),
            layers
        ),
        palette = palette,
        onDismiss = dismiss,
        onMarginaliaIntent = onMarginaliaIntent,
        editable = annotationWritesAvailable,
        sessionMetadataEditable = sessionMetadataEditable &&
            ready?.let { it.session.status == ReaderSessionStatus.ACTIVE } == true,
        mutationState = mutationState,
        sessionMetadata = sessionMetadata,
        onCreateBookmark = onCreateBookmark,
        onNavigateAnnotation = onNavigateAnnotation,
        onEditHighlight = {
            onMutation(ReaderAnnotationMutationIntent.BeginEdit(it))
        },
        onDeleteAnnotation = {
            onMutation(ReaderAnnotationMutationIntent.RequestDelete(it))
        }
    )
}
