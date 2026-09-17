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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.secondpasslibrary.reader.reader.navigation.ReaderNavigationIntent
import com.secondpasslibrary.reader.reader.presentation.ReaderMarginaliaPresentationState
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationResource
import com.secondpasslibrary.reader.reader.toc.ReaderTocDrawer
import com.secondpasslibrary.reader.reader.ui.hud.ReaderAmbientHud
import com.secondpasslibrary.reader.reader.ui.hud.ReaderHudPresentation
import com.secondpasslibrary.reader.reader.ui.hud.rememberReaderHudPresentation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

@Composable
// Root layout composes established Reader owners without owning their behavior.
@Suppress("LongMethod")
internal fun ReaderScreen(
    state: ReaderState,
    marginalia: ReaderMarginaliaPresentationState = ReaderMarginaliaPresentationState(),
    navigationFailures: Flow<Unit> = emptyFlow(),
    serverWritesAvailable: Boolean = true,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAppearanceChanged: (ReaderAppearance) -> Unit = {},
    onMarginaliaIntent: (ReaderMarginaliaIntent) -> Unit = {},
    onAnnotationMutation: (ReaderAnnotationMutationIntent) -> Unit = {},
    onCreateBookmark: () -> Unit = {},
    onNavigationIntent: (ReaderNavigationIntent) -> Unit = {},
    onRemoveBookmark: (ReaderAnnotation.Bookmark) -> Unit = {},
    onDismissSelection: () -> Unit = {},
    onDismissHighlightDetail: () -> Unit = {}
) {
    val ready = state as? ReaderState.Ready
    val navigationFailureHost = remember { SnackbarHostState() }
    LaunchedEffect(navigationFailures, navigationFailureHost) {
        navigationFailures.collect {
            navigationFailureHost.showSnackbar(
                message = READER_NAVIGATION_FAILURE_MESSAGE,
                duration = SnackbarDuration.Short
            )
        }
    }
    val appearance by remember(ready?.engine) { readyAppearance(ready) }.collectAsState()
    val palette = appearance.theme.readerPalette()
    val annotationWritesAvailable = ready?.let {
        it.session.status == ReaderSessionStatus.ACTIVE
    } == true
    val highlightSelection = writableSelection(
        ready,
        marginalia.selection,
        marginalia.annotationMutations,
        annotationWritesAvailable
    )
    var overlayVisible by remember { mutableStateOf(false) }
    var bookmarkMenuVisible by remember { mutableStateOf(false) }
    val hud = rememberReaderHudPresentation(
        ready?.engine,
        marginalia.selection != null || overlayVisible || bookmarkMenuVisible ||
            marginalia.highlightDetail != null
    )
    ReaderOverlayLayout(
        onExit = onBack,
        drawerScrimColor = palette.scrim,
        transientOverlayVisible = highlightSelection != null,
        onDismissTransientOverlay = onDismissSelection,
        onOverlayVisibilityChanged = { overlayVisible = it },
        tableOfContents = { dismiss ->
            ReaderTocDrawerContent(ready, palette, dismiss, onNavigationIntent, onBack)
        },
        appearance = { dismiss ->
            if (ready != null) ReaderAppearanceOverlay(appearance, onAppearanceChanged, dismiss)
        },
        annotations = { dismiss ->
            ReaderAnnotationsOverlayContent(
                ready,
                marginalia.annotations,
                marginalia.marginaliaLayers,
                marginalia.autoShowPreviousMarginalia,
                palette,
                dismiss,
                onMarginaliaIntent,
                annotationWritesAvailable,
                serverWritesAvailable,
                marginalia.annotationMutations,
                marginalia.sessionMetadata,
                onCreateBookmark,
                { onNavigationIntent(ReaderNavigationIntent.GoToAnnotation(it)) },
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
            marginalia.annotationMutations,
            marginalia.highlightDetail,
            hud,
            marginalia.pageBookmarks,
            annotationWritesAvailable,
            navigationFailureHost,
            onBack,
            onRetry,
            onAnnotationMutation,
            onCreateBookmark,
            { onNavigationIntent(ReaderNavigationIntent.GoToBookmark(it)) },
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
    annotationWritesAvailable: Boolean,
    navigationFailureHost: SnackbarHostState,
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
            ReaderState.Resolving -> ReaderLoading("Preparing Book", palette)

            ReaderState.Downloading -> ReaderLoading("Downloading Book", palette)

            ReaderState.Opening -> ReaderLoading("Opening Book", palette)

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
            visible = hud.visible && selection == null,
            onNavigationMenuRequested = {
                hud.reveal()
                overlays.openTableOfContents()
            },
            onAppearanceRequested = {
                hud.reveal()
                overlays.openAppearance()
            },
            bookmarks = pageBookmarks.bookmarks,
            bookmarksWritable = annotationWritesAvailable,
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
        SnackbarHost(
            navigationFailureHost,
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

internal const val READER_NAVIGATION_FAILURE_MESSAGE = "Couldn’t go to that location."

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
            onDismiss = onDismissSelection,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = READER_PUBLICATION_TOP_SAFE_INSET)
                .navigationBarsPadding()
                .padding(bottom = READER_PUBLICATION_BOTTOM_SAFE_INSET)
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

@Composable
internal fun ReaderAnnotationsOverlay(
    ready: ReaderState.Ready?,
    state: ReaderAnnotationsState,
    layers: ReaderMarginaliaLayersState,
    autoShowPrevious: Boolean,
    drawerState: ReaderMarginaliaDrawerState,
    palette: ReaderPalette,
    onDismiss: () -> Unit,
    onMarginaliaIntent: (ReaderMarginaliaIntent) -> Unit,
    editable: Boolean,
    sessionMetadataEditable: Boolean,
    mutationState: ReaderAnnotationMutationState,
    sessionMetadata: ReaderSessionMetadataState,
    onCreateBookmark: () -> Unit,
    onNavigateAnnotation: (ReaderAnnotation) -> Unit,
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
        currentMetadataEditable = sessionMetadataEditable,
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
            onNavigateAnnotation(annotation)
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
    onNavigate: (ReaderNavigationIntent) -> Unit,
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
            onNavigate(ReaderNavigationIntent.GoToPublicationTarget(target))
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
        ReaderFailure.DOWNLOAD -> "Couldn’t download this Book. Check your connection and retry."

        ReaderFailure.INTEGRITY ->
            "Couldn’t verify this Book download. Connect and try downloading it again."

        ReaderFailure.OPEN -> "Couldn’t open this Book. Retry or go back."

        ReaderFailure.NO_EPUB -> "This Book doesn’t include a supported EPUB."

        ReaderFailure.OFFLINE_ASSET_UNAVAILABLE ->
            "This Book isn’t available offline. Connect, then retry."

        ReaderFailure.SESSION -> "Couldn’t prepare the Reading Session. Retry or go back."
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
