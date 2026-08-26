package com.secondpasslibrary.reader.reader

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
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationCreateState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.ReaderSelection
import com.secondpasslibrary.reader.reader.annotations.ReaderSelectionToolbar
import com.secondpasslibrary.reader.reader.annotations.navigateToReaderAnnotation
import com.secondpasslibrary.reader.reader.domain.ReaderAppearance
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.ui.ReaderOverlayHost
import com.secondpasslibrary.reader.reader.ui.ReaderOverlayLayout
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
    onRetryAnnotations: () -> Unit = {},
    selection: ReaderSelection? = null,
    annotationCreate: ReaderAnnotationCreateState = ReaderAnnotationCreateState(),
    onHighlightColorChanged: (ReaderAnnotationColor) -> Unit = {},
    onHighlightNoteChanged: (String) -> Unit = {},
    onSubmitHighlight: () -> Unit = {},
    onDismissSelection: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val ready = state as? ReaderState.Ready
    val appearanceFlow = remember(ready?.engine) { readyAppearance(ready) }
    val appearance by appearanceFlow.collectAsState()
    val chromeColors = appearance.theme.chromeColors()
    val highlightSelection = selection?.takeIf {
        ready?.session?.status == ReaderSessionStatus.ACTIVE &&
            annotationCreate.pending?.selection?.cfi == it.cfi
    }
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
                colors = chromeColors,
                scope = scope,
                onDismiss = dismiss,
                onRetry = onRetryAnnotations
            )
        }
    ) {
        ReaderReadingSurface(
            state,
            ready,
            it,
            chromeColors,
            highlightSelection,
            annotationCreate,
            onBack,
            onRetry,
            onHighlightColorChanged,
            onHighlightNoteChanged,
            onSubmitHighlight,
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
    createState: ReaderAnnotationCreateState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onColorChanged: (ReaderAnnotationColor) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSubmit: () -> Unit,
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
                state = createState,
                colors = colors,
                onColorChanged = onColorChanged,
                onNoteChanged = onNoteChanged,
                onSubmit = onSubmit,
                onDismiss = onDismissSelection,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
            )
        }
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
    colors: ReaderChromeColors,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (ready == null) return
    ReaderAnnotationsDrawer(
        state = state,
        colors = colors,
        onDismiss = onDismiss,
        onRetry = onRetry,
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
