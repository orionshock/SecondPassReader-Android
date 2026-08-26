package com.secondpasslibrary.reader.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.navigateToReaderAnnotation
import com.secondpasslibrary.reader.reader.domain.ReaderAppearance
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
    onRetryAnnotations: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ready = state as? ReaderState.Ready
    val appearanceFlow = remember(ready?.engine) { readyAppearance(ready) }
    val appearance by appearanceFlow.collectAsState()
    val chromeColors = appearance.theme.chromeColors()
    var overlay by remember { mutableStateOf(ReaderOverlay.NONE) }
    ReaderBackHandler(
        overlay = overlay,
        onDismissOverlay = { overlay = ReaderOverlay.NONE },
        drawerState = drawerState,
        scope = scope,
        onBack = onBack
    )
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ReaderTocDrawerContent(ready, drawerState, scope, onBack)
        }
    ) {
        Box(Modifier.fillMaxSize().background(chromeColors.background)) {
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
                colors = chromeColors,
                drawerState = drawerState,
                scope = scope,
                onOverlayChanged = { overlay = it }
            )
            if (overlay == ReaderOverlay.APPEARANCE && ready != null) {
                ReaderAppearanceOverlay(
                    appearance = appearance,
                    onAppearanceChanged = onAppearanceChanged,
                    onDismissRequest = { overlay = ReaderOverlay.NONE }
                )
            }
            ReaderAnnotationsOverlay(
                visible = overlay == ReaderOverlay.ANNOTATIONS,
                ready = ready,
                state = annotations,
                colors = chromeColors,
                scope = scope,
                onDismiss = { overlay = ReaderOverlay.NONE },
                onRetry = onRetryAnnotations
            )
        }
    }
}

private fun readyAppearance(ready: ReaderState.Ready?) =
    ready?.engine?.appearance?.appearance ?: DEFAULT_READER_APPEARANCE

@Composable
private fun ReaderBackHandler(
    overlay: ReaderOverlay,
    onDismissOverlay: () -> Unit,
    drawerState: DrawerState,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    BackHandler {
        when {
            overlay != ReaderOverlay.NONE -> onDismissOverlay()

            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open -> scope.launch { drawerState.close() }

            else -> onBack()
        }
    }
}

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
    visible: Boolean,
    ready: ReaderState.Ready?,
    state: ReaderAnnotationsState,
    colors: ReaderChromeColors,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (!visible || ready == null) return
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
    drawerState: DrawerState,
    scope: CoroutineScope,
    onOverlayChanged: (ReaderOverlay) -> Unit
) {
    ReaderChrome(
        title = title,
        colors = colors,
        onNavigationMenuRequested = {
            onOverlayChanged(ReaderOverlay.NONE)
            scope.launch { drawerState.open() }
        },
        onAppearanceRequested = {
            scope.launch {
                drawerState.close()
                onOverlayChanged(ReaderOverlay.APPEARANCE)
            }
        },
        onAnnotationsRequested = {
            scope.launch {
                drawerState.close()
                onOverlayChanged(ReaderOverlay.ANNOTATIONS)
            }
        }
    )
}

private enum class ReaderOverlay { NONE, APPEARANCE, ANNOTATIONS }

@Composable
private fun ReaderTocDrawerContent(
    ready: ReaderState.Ready?,
    drawerState: DrawerState,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    ReaderTocDrawer(
        bookTitle = ready?.title ?: "Reader",
        entries = ready?.engine?.tableOfContents?.entries.orEmpty(),
        onEntrySelected = { target ->
            scope.launch {
                drawerState.close()
                ready?.engine?.tableOfContents?.goTo(target)
            }
        },
        onReturnToBook = {
            scope.launch {
                drawerState.close()
                onBack()
            }
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
