package com.secondpasslibrary.reader.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.secondpasslibrary.reader.reader.ReaderFailure
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarks
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderReadOnlyHighlightDetail
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.navigateToReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderHighlightMutationDialogs
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderHighlightReadOnlyDialog
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderSelectionToolbar
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearancePanel
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.appearance.readerPalette
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.marginalia.ui.ReaderMarginaliaDrawer
import com.secondpasslibrary.reader.reader.marginalia.ui.ReaderMarginaliaDrawerState
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationResource
import com.secondpasslibrary.reader.reader.toc.ReaderTocDrawer
import com.secondpasslibrary.reader.reader.ui.hud.ReaderAmbientHud
import com.secondpasslibrary.reader.reader.ui.hud.ReaderHudPresentation
import com.secondpasslibrary.reader.reader.ui.hud.rememberReaderHudPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
// Root layout composes established Reader owners without owning their behavior.
@Suppress("LongMethod")
internal fun ReaderScreen(
    state: ReaderState,
    serverWritesAvailable: Boolean = true,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAppearanceChanged: (ReaderAppearance) -> Unit = {},
    annotations: ReaderAnnotationsState = ReaderAnnotationsState(),
    pageBookmarks: ReaderVisiblePageBookmarks = ReaderVisiblePageBookmarks(),
    marginaliaLayers: ReaderMarginaliaLayersState = ReaderMarginaliaLayersState(),
    autoShowPreviousMarginalia: Boolean = true,
    onMarginaliaIntent: (ReaderMarginaliaIntent) -> Unit = {},
    selection: ReaderSelection? = null,
    annotationMutations: ReaderAnnotationMutationState = ReaderAnnotationMutationState(),
    highlightDetail: ReaderReadOnlyHighlightDetail? = null,
    sessionMetadata: ReaderSessionMetadataState = ReaderSessionMetadataState(),
    onAnnotationMutation: (ReaderAnnotationMutationIntent) -> Unit = {},
    onCreateBookmark: () -> Unit = {},
    onNavigateBookmark: (ReaderAnnotation.Bookmark) -> Unit = {},
    onRemoveBookmark: (ReaderAnnotation.Bookmark) -> Unit = {},
    onDismissSelection: () -> Unit = {},
    onDismissHighlightDetail: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val ready = state as? ReaderState.Ready
    val appearance by remember(ready?.engine) { readyAppearance(ready) }.collectAsState()
    val palette = appearance.theme.readerPalette()
    val highlightSelection = writableSelection(
        ready,
        selection,
        annotationMutations,
        serverWritesAvailable
    )
    var overlayVisible by remember { mutableStateOf(false) }
    var bookmarkMenuVisible by remember { mutableStateOf(false) }
    val hud = rememberReaderHudPresentation(
        ready?.engine,
        selection != null || overlayVisible || bookmarkMenuVisible || highlightDetail != null
    )
    ReaderOverlayLayout(
        onExit = onBack,
        drawerScrimColor = palette.scrim,
        transientOverlayVisible = highlightSelection != null,
        onDismissTransientOverlay = onDismissSelection,
        onOverlayVisibilityChanged = { overlayVisible = it },
        tableOfContents = { dismiss ->
            ReaderTocDrawerContent(ready, palette, dismiss, scope, onBack)
        },
        appearance = { dismiss ->
            if (ready != null) ReaderAppearanceOverlay(appearance, onAppearanceChanged, dismiss)
        },
        annotations = { dismiss ->
            ReaderAnnotationsOverlayContent(
                ready,
                annotations,
                marginaliaLayers,
                autoShowPreviousMarginalia,
                palette,
                scope,
                dismiss,
                onMarginaliaIntent,
                serverWritesAvailable,
                annotationMutations,
                sessionMetadata,
                onCreateBookmark,
                onAnnotationMutation
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
            highlightDetail,
            hud,
            pageBookmarks,
            serverWritesAvailable,
            onBack,
            onRetry,
            onAnnotationMutation,
            onCreateBookmark,
            onNavigateBookmark,
            onRemoveBookmark,
            { bookmarkMenuVisible = it },
            onDismissSelection,
            onDismissHighlightDetail
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
    highlightDetail: ReaderReadOnlyHighlightDetail?,
    hud: ReaderHudPresentation,
    pageBookmarks: ReaderVisiblePageBookmarks,
    serverWritesAvailable: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMutation: (ReaderAnnotationMutationIntent) -> Unit,
    onCreateBookmark: () -> Unit,
    onNavigateBookmark: (ReaderAnnotation.Bookmark) -> Unit,
    onRemoveBookmark: (ReaderAnnotation.Bookmark) -> Unit,
    onBookmarkMenuVisibilityChanged: (Boolean) -> Unit,
    onDismissSelection: () -> Unit,
    onDismissHighlightDetail: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(palette.publicationBackground)) {
        when (state) {
            ReaderState.Resolving -> ReaderLoading("Preparing book…", palette)

            ReaderState.Downloading -> ReaderLoading("Downloading book…", palette)

            ReaderState.Opening -> ReaderLoading("Opening EPUB…", palette)

            is ReaderState.Ready -> Box(Modifier.fillMaxSize()) {
                state.engine.viewport.Content(
                    Modifier.fillMaxSize().statusBarsPadding()
                        .padding(top = READER_PUBLICATION_TOP_SAFE_INSET)
                        .navigationBarsPadding()
                        .padding(bottom = READER_PUBLICATION_BOTTOM_SAFE_INSET)
                )
            }

            is ReaderState.Failure -> ReaderFailureContent(state.kind, palette, onBack, onRetry)
        }
        ReaderAmbientHud(hud.visible, hud.readingStatus, palette)
        ReaderChromeLayer(
            title = ready?.title ?: "Reader",
            palette = palette,
            visible = hud.visible,
            onNavigationMenuRequested = {
                hud.reveal()
                overlays.openTableOfContents()
            },
            onAppearanceRequested = {
                hud.reveal()
                overlays.openAppearance()
            },
            bookmarks = pageBookmarks.bookmarks,
            bookmarksWritable = serverWritesAvailable &&
                ready?.session?.status == ReaderSessionStatus.ACTIVE,
            onCreateBookmark = onCreateBookmark,
            onNavigateBookmark = onNavigateBookmark,
            onRemoveBookmark = onRemoveBookmark,
            onBookmarkMenuVisibilityChanged = onBookmarkMenuVisibilityChanged,
            onAnnotationsRequested = {
                hud.reveal()
                overlays.openAnnotations()
            }
        )
        ReaderSelectionAnnotationOverlays(
            selection,
            mutationState,
            highlightDetail,
            palette,
            onMutation,
            onDismissSelection,
            onDismissHighlightDetail
        )
    }
}

@Composable
private fun ReaderSelectionAnnotationOverlays(
    selection: ReaderSelection?,
    mutationState: ReaderAnnotationMutationState,
    highlightDetail: ReaderReadOnlyHighlightDetail?,
    palette: ReaderPalette,
    onMutation: (ReaderAnnotationMutationIntent) -> Unit,
    onDismissSelection: () -> Unit,
    onDismissHighlightDetail: () -> Unit
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
        onRequestDelete = { annotation ->
            onMutation(ReaderAnnotationMutationIntent.RequestDelete(annotation))
        },
        onConfirmDelete = { onMutation(ReaderAnnotationMutationIntent.ConfirmDelete) },
        onDismiss = { onMutation(ReaderAnnotationMutationIntent.DismissTransient) }
    )
    highlightDetail?.let {
        ReaderHighlightReadOnlyDialog(it, palette, onDismissHighlightDetail)
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
internal fun ReaderAnnotationsOverlay(
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
    sessionMetadata: ReaderSessionMetadataState,
    onCreateBookmark: () -> Unit,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit
) {
    val session = ready?.session ?: return
    ReaderMarginaliaDrawer(
        currentSessionId = session.sessionId,
        currentAnnotations = state,
        layers = layers,
        autoShowPrevious = autoShowPrevious,
        drawerState = drawerState,
        palette = palette,
        onDismiss = onDismiss,
        onRetryCurrent = { onMarginaliaIntent(ReaderMarginaliaIntent.RetryCurrentAnnotations) },
        currentEditable = editable,
        onLoadLayer = { onMarginaliaIntent(ReaderMarginaliaIntent.LoadPreviousLayer(it)) },
        onSetLayerVisible = { sessionId, visible ->
            onMarginaliaIntent(
                ReaderMarginaliaIntent.SetPreviousLayerVisible(sessionId, visible)
            )
        },
        onShowAllPrevious = { onMarginaliaIntent(ReaderMarginaliaIntent.ShowAllPreviousLayers) },
        onHideAllPrevious = { onMarginaliaIntent(ReaderMarginaliaIntent.HideAllPreviousLayers) },
        onAutoShowPreviousChanged = {
            onMarginaliaIntent(ReaderMarginaliaIntent.SetAutoShowPrevious(it))
        },
        onLoadMoreLayers = { onMarginaliaIntent(ReaderMarginaliaIntent.LoadMoreLayers) },
        onRetryLayers = { onMarginaliaIntent(ReaderMarginaliaIntent.RetryLayerHistory) },
        mutationState = mutationState,
        sessionMetadata = sessionMetadata,
        onCreateBookmark = onCreateBookmark,
        onEditHighlight = onEditHighlight,
        onDeleteAnnotation = onDeleteAnnotation,
        onNavigateAnnotation = { annotation ->
            onDismiss()
            navigateToAnnotation(scope, ready, annotation)
        },
        onEditCurrentSessionMetadata = {
            onMarginaliaIntent(ReaderMarginaliaIntent.EditCurrentSessionMetadata)
        },
        onCurrentSessionNameChanged = {
            onMarginaliaIntent(ReaderMarginaliaIntent.ChangeCurrentSessionName(it))
        },
        onCurrentSessionNotesChanged = {
            onMarginaliaIntent(ReaderMarginaliaIntent.ChangeCurrentSessionNotes(it))
        },
        onSaveCurrentSessionMetadata = {
            onMarginaliaIntent(ReaderMarginaliaIntent.SaveCurrentSessionMetadata)
        },
        onDismissCurrentSessionMetadataEditor = {
            onMarginaliaIntent(ReaderMarginaliaIntent.DismissCurrentSessionMetadataEditor)
        }
    )
}

@Composable
private fun ReaderChromeLayer(
    title: String,
    palette: ReaderPalette,
    visible: Boolean,
    onNavigationMenuRequested: () -> Unit,
    onAppearanceRequested: () -> Unit,
    bookmarks: List<ReaderAnnotation.Bookmark>,
    bookmarksWritable: Boolean,
    onCreateBookmark: () -> Unit,
    onNavigateBookmark: (ReaderAnnotation.Bookmark) -> Unit,
    onRemoveBookmark: (ReaderAnnotation.Bookmark) -> Unit,
    onBookmarkMenuVisibilityChanged: (Boolean) -> Unit,
    onAnnotationsRequested: () -> Unit
) {
    ReaderChrome(
        title = title,
        palette = palette,
        visible = visible,
        onNavigationMenuRequested = onNavigationMenuRequested,
        onAppearanceRequested = onAppearanceRequested,
        bookmarks = bookmarks,
        bookmarksWritable = bookmarksWritable,
        onCreateBookmark = onCreateBookmark,
        onNavigateBookmark = onNavigateBookmark,
        onRemoveBookmark = onRemoveBookmark,
        onBookmarkMenuVisibilityChanged = onBookmarkMenuVisibilityChanged,
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
    val currentResource by remember(ready?.engine) {
        ready?.engine?.tableOfContents?.currentResource ?: EMPTY_READER_RESOURCE
    }.collectAsState()
    ReaderTocDrawer(
        bookTitle = ready?.title ?: "Reader",
        entries = ready?.engine?.tableOfContents?.entries.orEmpty(),
        currentResource = currentResource,
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
private val EMPTY_READER_RESOURCE = MutableStateFlow<ReaderPublicationResource?>(null)
private val READER_PUBLICATION_BOTTOM_SAFE_INSET = 28.dp

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
        ReaderFailure.OFFLINE_ASSET_UNAVAILABLE -> "This book is not available offline."
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
