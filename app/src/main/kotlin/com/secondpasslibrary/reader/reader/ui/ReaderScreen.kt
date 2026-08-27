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
    val chromeColors = appearance.theme.chromeColors()
    val currentSessionId = ready?.session?.sessionId.orEmpty()
    val marginaliaDrawerState = rememberMarginaliaDrawerState(currentSessionId, marginaliaLayers)
    val highlightSelection = writableSelection(ready, selection, annotationMutations)
    ReaderOverlayLayout(
        onExit = onBack,
        transientOverlayVisible = highlightSelection != null,
        onDismissTransientOverlay = onDismissSelection,
        tableOfContents = { dismiss ->
            ReaderTocDrawerContent(ready, dismiss, scope, onBack)
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
                drawerState = marginaliaDrawerState,
                colors = chromeColors,
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
            chromeColors,
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
    colors: ReaderChromeColors,
    selection: ReaderSelection?,
    mutationState: ReaderAnnotationMutationState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMutation: (ReaderAnnotationMutationIntent) -> Unit,
    onDismissSelection: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (state) {
            ReaderState.Resolving -> ReaderLoading("Preparing book…")

            ReaderState.Downloading -> ReaderLoading("Downloading book…")

            ReaderState.Opening -> ReaderLoading("Opening EPUB…")

            is ReaderState.Ready -> Box(Modifier.fillMaxSize()) {
                state.engine.viewport.Content(Modifier.fillMaxSize())
                ReaderCfiProbe(state.engine, Modifier.align(Alignment.TopEnd))
            }

            is ReaderState.Failure -> ReaderFailureContent(state.kind, onBack, onRetry)
        }
        ReaderChromeLayer(
            title = ready?.title ?: "Reader",
            colors = colors,
            onNavigationMenuRequested = overlays.openTableOfContents,
            onAppearanceRequested = overlays.openAppearance,
            onAnnotationsRequested = overlays.openAnnotations
        )
        selection?.let {
            ReaderSelectionToolbar(
                selection = it,
                state = mutationState,
                colors = colors,
                onColorChanged = {
                    onMutation(ReaderAnnotationMutationIntent.UpdateCreate(color = it))
                },
                onNoteChanged = {
                    onMutation(ReaderAnnotationMutationIntent.UpdateCreate(note = it))
                },
                onSubmit = { onMutation(ReaderAnnotationMutationIntent.SubmitCreate) },
                onDismiss = onDismissSelection,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
            )
        }
        ReaderHighlightMutationDialogs(
            state = mutationState,
            colors = colors,
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
    drawerState: ReaderMarginaliaDrawerState,
    colors: ReaderChromeColors,
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
        drawerState = drawerState,
        colors = colors,
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
    colors: ReaderChromeColors,
    onNavigationMenuRequested: () -> Unit,
    onAppearanceRequested: () -> Unit,
    onAnnotationsRequested: () -> Unit
) {
    ReaderChrome(
        title = title,
        colors = colors,
        onNavigationMenuRequested = onNavigationMenuRequested,
        onAppearanceRequested = onAppearanceRequested,
        onAnnotationsRequested = onAnnotationsRequested
    )
}

@Composable
private fun ReaderTocDrawerContent(
    ready: ReaderState.Ready?,
    dismiss: () -> Unit,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    ReaderTocDrawer(
        bookTitle = ready?.title ?: "Reader",
        entries = ready?.engine?.tableOfContents?.entries.orEmpty(),
        onEntrySelected = { target ->
            dismiss()
            scope.launch {
                ready?.engine?.tableOfContents?.goTo(target)
            }
        },
        onReturnToBook = {
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
private fun ReaderLoading(label: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(label, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReaderFailureContent(kind: ReaderFailure, onBack: () -> Unit, onRetry: () -> Unit) {
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
        Text(message)
        if (kind != ReaderFailure.NO_EPUB) TextButton(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
