package com.secondpasslibrary.reader.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.ReaderCfiProbe
import com.secondpasslibrary.reader.reader.ReaderFailure
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.navigateToReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderHighlightMutationDialogs
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderSelectionToolbar
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearancePanel
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.appearance.readerPalette
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.marginalia.ui.ReaderMarginaliaDrawer
import com.secondpasslibrary.reader.reader.marginalia.ui.ReaderMarginaliaDrawerState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.ReaderTocDrawer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
internal fun ReaderScreen(
    state: ReaderState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAppearanceChanged: (ReaderAppearance) -> Unit = {},
    annotations: ReaderAnnotationsState = ReaderAnnotationsState(),
    marginaliaLayers: ReaderMarginaliaLayersState = ReaderMarginaliaLayersState(),
    autoShowPreviousMarginalia: Boolean = true,
    onMarginaliaIntent: (ReaderMarginaliaIntent) -> Unit = {},
    selection: ReaderSelection? = null,
    annotationMutations: ReaderAnnotationMutationState = ReaderAnnotationMutationState(),
    onAnnotationMutation: (ReaderAnnotationMutationIntent) -> Unit = {},
    onCreateBookmark: () -> Unit = {},
    onDismissSelection: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val ready = state as? ReaderState.Ready
    val appearance by remember(ready?.engine) { readyAppearance(ready) }.collectAsState()
    val palette = appearance.theme.readerPalette()
    val currentSessionId = ready?.session?.sessionId.orEmpty()
    val marginaliaDrawerState = rememberMarginaliaDrawerState(currentSessionId, marginaliaLayers)
    val highlightSelection = writableSelection(ready, selection, annotationMutations)
    ReaderOverlayLayout(
        onExit = onBack,
        transientOverlayVisible = highlightSelection != null,
        onDismissTransientOverlay = onDismissSelection,
        tableOfContents = { dismiss ->
            ReaderTocDrawerContent(ready, palette, dismiss, scope, onBack)
        },
        appearance = { dismiss ->
            if (ready != null) {
                ReaderAppearanceOverlay(appearance, onAppearanceChanged, dismiss)
            }
        },
        annotations = { dismiss ->
            ReaderAnnotationsOverlay(
                ready = ready,
                state = annotations,
                layers = marginaliaLayers,
                autoShowPrevious = autoShowPreviousMarginalia,
                drawerState = marginaliaDrawerState,
                palette = palette,
                scope = scope,
                onDismiss = dismiss,
                onMarginaliaIntent = onMarginaliaIntent,
                editable = ready?.session?.status == ReaderSessionStatus.ACTIVE,
                mutationState = annotationMutations,
                onCreateBookmark = onCreateBookmark,
                onEditHighlight = {
                    onAnnotationMutation(ReaderAnnotationMutationIntent.BeginEdit(it))
                },
                onDeleteAnnotation = {
                    onAnnotationMutation(ReaderAnnotationMutationIntent.RequestDelete(it))
                }
            )
        }
    ) {
        ReaderReadingSurface(
            state,
            ready,
            it,
            palette,
            highlightSelection,
            annotationMutations,
            onBack,
            onRetry,
            onAnnotationMutation,
            onDismissSelection
        )
    }
}

@Composable
private fun ReaderReadingSurface(
    state: ReaderState,
    ready: ReaderState.Ready?,
    overlays: ReaderOverlayHost,
    palette: ReaderPalette,
    selection: ReaderSelection?,
    mutationState: ReaderAnnotationMutationState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMutation: (ReaderAnnotationMutationIntent) -> Unit,
    onDismissSelection: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(palette.publicationBackground)) {
        when (state) {
            ReaderState.Resolving -> ReaderLoading("Preparing book…", palette)

            ReaderState.Downloading -> ReaderLoading("Downloading book…", palette)

            ReaderState.Opening -> ReaderLoading("Opening EPUB…", palette)

            is ReaderState.Ready -> Box(Modifier.fillMaxSize()) {
                state.engine.viewport.Content(
                    Modifier.fillMaxSize().padding(top = READER_PUBLICATION_TOP_SAFE_INSET)
                )
                ReaderCfiProbe(state.engine, Modifier.align(Alignment.TopEnd))
            }

            is ReaderState.Failure -> ReaderFailureContent(state.kind, palette, onBack, onRetry)
        }
        ReaderChromeLayer(
            title = ready?.title ?: "Reader",
            palette = palette,
            onNavigationMenuRequested = overlays.openTableOfContents,
            onAppearanceRequested = overlays.openAppearance,
            onAnnotationsRequested = overlays.openAnnotations
        )
        ReaderSelectionAnnotationOverlays(
            selection,
            mutationState,
            palette,
            onMutation,
            onDismissSelection
        )
    }
}

@Composable
private fun ReaderSelectionAnnotationOverlays(
    selection: ReaderSelection?,
    mutationState: ReaderAnnotationMutationState,
    palette: ReaderPalette,
    onMutation: (ReaderAnnotationMutationIntent) -> Unit,
    onDismissSelection: () -> Unit
) {
    selection?.takeUnless { mutationState.createNoteEditorVisible }?.let {
        ReaderSelectionToolbar(
            selection = it,
            state = mutationState,
            palette = palette,
            onQuickHighlight = {
                onMutation(ReaderAnnotationMutationIntent.SubmitQuickCreate(it))
            },
            onNoteRequested = { onMutation(ReaderAnnotationMutationIntent.OpenCreateNote) },
            onDismiss = onDismissSelection
        )
    }
    ReaderHighlightMutationDialogs(
        state = mutationState,
        palette = palette,
        onCreateColorChanged = {
            onMutation(ReaderAnnotationMutationIntent.UpdateCreate(color = it))
        },
        onCreateNoteChanged = {
            onMutation(ReaderAnnotationMutationIntent.UpdateCreate(note = it))
        },
        onSaveCreate = { onMutation(ReaderAnnotationMutationIntent.SubmitCreate) },
        onCancelCreateNote = { onMutation(ReaderAnnotationMutationIntent.CancelCreateNote) },
        onEditColorChanged = {
            onMutation(ReaderAnnotationMutationIntent.UpdateEdit(color = it))
        },
        onEditNoteChanged = {
            onMutation(ReaderAnnotationMutationIntent.UpdateEdit(note = it))
        },
        onSaveEdit = { onMutation(ReaderAnnotationMutationIntent.SaveEdit) },
        onConfirmDelete = { onMutation(ReaderAnnotationMutationIntent.ConfirmDelete) },
        onDismiss = { onMutation(ReaderAnnotationMutationIntent.DismissTransient) }
    )
}

private fun readyAppearance(ready: ReaderState.Ready?) =
    ready?.engine?.appearance?.appearance ?: DEFAULT_READER_APPEARANCE

private fun navigateToAnnotation(
    scope: CoroutineScope,
    ready: ReaderState.Ready,
    annotation: ReaderAnnotation
) {
    scope.launch {
        navigateToReaderAnnotation(annotation, ready.engine.cfiNavigator)
    }
}

@Composable
private fun ReaderAnnotationsOverlay(
    ready: ReaderState.Ready?,
    state: ReaderAnnotationsState,
    layers: ReaderMarginaliaLayersState,
    autoShowPrevious: Boolean,
    drawerState: ReaderMarginaliaDrawerState,
    palette: ReaderPalette,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onMarginaliaIntent: (ReaderMarginaliaIntent) -> Unit,
    editable: Boolean,
    mutationState: ReaderAnnotationMutationState,
    onCreateBookmark: () -> Unit,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit
) {
    if (ready == null) return
    ReaderMarginaliaDrawer(
        currentSessionId = ready.session.sessionId,
        currentAnnotations = state,
        layers = layers,
        autoShowPrevious = autoShowPrevious,
        drawerState = drawerState,
        palette = palette,
        onDismiss = onDismiss,
        onRetryCurrent = {
            onMarginaliaIntent(ReaderMarginaliaIntent.RetryCurrentAnnotations)
        },
        currentEditable = editable,
        onLoadLayer = {
            onMarginaliaIntent(ReaderMarginaliaIntent.LoadPreviousLayer(it))
        },
        onSetLayerVisible = { sessionId, visible ->
            onMarginaliaIntent(
                ReaderMarginaliaIntent.SetPreviousLayerVisible(sessionId, visible)
            )
        },
        onShowAllPrevious = {
            onMarginaliaIntent(ReaderMarginaliaIntent.ShowAllPreviousLayers)
        },
        onHideAllPrevious = {
            onMarginaliaIntent(ReaderMarginaliaIntent.HideAllPreviousLayers)
        },
        onAutoShowPreviousChanged = {
            onMarginaliaIntent(ReaderMarginaliaIntent.SetAutoShowPrevious(it))
        },
        onLoadMoreLayers = {
            onMarginaliaIntent(ReaderMarginaliaIntent.LoadMoreLayers)
        },
        onRetryLayers = {
            onMarginaliaIntent(ReaderMarginaliaIntent.RetryLayerHistory)
        },
        mutationState = mutationState,
        onCreateBookmark = onCreateBookmark,
        onEditHighlight = onEditHighlight,
        onDeleteAnnotation = onDeleteAnnotation,
        onAnnotationSelected = { annotation ->
            onDismiss()
            navigateToAnnotation(scope, ready, annotation)
        }
    )
}

@Composable
private fun ReaderChromeLayer(
    title: String,
    palette: ReaderPalette,
    onNavigationMenuRequested: () -> Unit,
    onAppearanceRequested: () -> Unit,
    onAnnotationsRequested: () -> Unit
) {
    ReaderChrome(
        title = title,
        palette = palette,
        onNavigationMenuRequested = onNavigationMenuRequested,
        onAppearanceRequested = onAppearanceRequested,
        onAnnotationsRequested = onAnnotationsRequested
    )
}

@Composable
private fun ReaderTocDrawerContent(
    ready: ReaderState.Ready?,
    palette: ReaderPalette,
    dismiss: () -> Unit,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    ReaderTocDrawer(
        bookTitle = ready?.title ?: "Reader",
        entries = ready?.engine?.tableOfContents?.entries.orEmpty(),
        palette = palette,
        onEntrySelected = { target ->
            dismiss()
            scope.launch {
                ready?.engine?.tableOfContents?.goTo(target)
            }
        },
        onDismiss = dismiss,
        onCloseBook = {
            dismiss()
            onBack()
        }
    )
}

@Composable
private fun ReaderAppearanceOverlay(
    appearance: ReaderAppearance,
    onAppearanceChanged: (ReaderAppearance) -> Unit,
    onDismissRequest: () -> Unit
) {
    ReaderAppearancePanel(
        appearance = appearance,
        onAppearanceChanged = onAppearanceChanged,
        onDismissRequest = onDismissRequest
    )
}

private val DEFAULT_READER_APPEARANCE = MutableStateFlow(ReaderAppearance())

@Composable
private fun ReaderLoading(label: String, palette: ReaderPalette) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = palette.primaryForeground)
        Text(label, Modifier.padding(top = 12.dp), color = palette.primaryForeground)
    }
}

@Composable
private fun ReaderFailureContent(
    kind: ReaderFailure,
    palette: ReaderPalette,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val message = when (kind) {
        ReaderFailure.DOWNLOAD -> "Couldn’t download this book."
        ReaderFailure.OPEN -> "Couldn’t open this EPUB."
        ReaderFailure.NO_EPUB -> "This book does not have an EPUB file."
        ReaderFailure.SESSION -> "Couldn't prepare this reading session."
    }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = palette.primaryForeground)
        if (kind != ReaderFailure.NO_EPUB) TextButton(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
