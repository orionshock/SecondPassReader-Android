package com.secondpasslibrary.reader.reader.ui

import androidx.compose.runtime.Composable
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CoroutineScope

/** Wires current-session annotation mutation intent into the marginalia overlay. */
@Composable
internal fun ReaderAnnotationsOverlayContent(
    ready: ReaderState.Ready?,
    annotations: ReaderAnnotationsState,
    layers: ReaderMarginaliaLayersState,
    autoShowPrevious: Boolean,
    palette: ReaderPalette,
    scope: CoroutineScope,
    dismiss: () -> Unit,
    onMarginaliaIntent: (ReaderMarginaliaIntent) -> Unit,
    mutationState: ReaderAnnotationMutationState,
    sessionMetadata: ReaderSessionMetadataState,
    onCreateBookmark: () -> Unit,
    onMutation: (ReaderAnnotationMutationIntent) -> Unit
) {
    ReaderAnnotationsOverlay(
        ready = ready,
        state = annotations,
        layers = layers,
        autoShowPrevious = autoShowPrevious,
        drawerState = rememberMarginaliaDrawerState(
            ready?.session?.sessionId.orEmpty(),
            layers
        ),
        palette = palette,
        scope = scope,
        onDismiss = dismiss,
        onMarginaliaIntent = onMarginaliaIntent,
        editable = ready?.session?.status == ReaderSessionStatus.ACTIVE,
        mutationState = mutationState,
        sessionMetadata = sessionMetadata,
        onCreateBookmark = onCreateBookmark,
        onEditHighlight = {
            onMutation(ReaderAnnotationMutationIntent.BeginEdit(it))
        },
        onDeleteAnnotation = {
            onMutation(ReaderAnnotationMutationIntent.RequestDelete(it))
        }
    )
}
