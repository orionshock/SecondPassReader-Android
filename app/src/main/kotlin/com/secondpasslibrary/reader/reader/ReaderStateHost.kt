package com.secondpasslibrary.reader.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderBookmarkHudIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.ui.ReaderScreen

@Composable
internal fun ReaderStateHost(
    profile: ConnectionProfile,
    profileId: String,
    bookId: String,
    existingSessionId: String?,
    titleHint: String?,
    availability: AppAvailability,
    onBack: () -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val pageBookmarks by viewModel.pageBookmarks.collectAsStateWithLifecycle()
    val marginaliaLayers by viewModel.marginaliaLayers.collectAsStateWithLifecycle()
    val autoShowPreviousMarginalia by
        viewModel.autoShowPreviousMarginalia.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val annotationMutations by viewModel.annotationMutationState.collectAsStateWithLifecycle()
    val highlightDetail by viewModel.highlightDetail.collectAsStateWithLifecycle()
    val sessionMetadata by viewModel.sessionMetadataState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.setAvailability(
                AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
            )
        }
    }
    LaunchedEffect(availability) {
        viewModel.setAvailability(availability)
    }
    LaunchedEffect(profile, profileId, bookId, existingSessionId, titleHint) {
        viewModel.initialize(profile, profileId, bookId, existingSessionId, titleHint, availability)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                ReaderConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.flushForBackground()
    }
    ReaderScreen(
        state = state,
        serverWritesAvailable = availability !is AppAvailability.Offline,
        onBack = { viewModel.flushThenExit(onBack) },
        onRetry = { viewModel.retry(availability) },
        onAppearanceChanged = viewModel::updateAppearance,
        annotations = annotations,
        pageBookmarks = pageBookmarks,
        marginaliaLayers = marginaliaLayers,
        autoShowPreviousMarginalia = autoShowPreviousMarginalia,
        onMarginaliaIntent = viewModel::acceptMarginalia,
        selection = selection,
        annotationMutations = annotationMutations,
        highlightDetail = highlightDetail,
        sessionMetadata = sessionMetadata,
        onAnnotationMutation = viewModel::mutateAnnotation,
        onCreateBookmark = { viewModel.acceptBookmark(ReaderBookmarkHudIntent.Create) },
        onNavigationIntent = viewModel.onNavigationIntent,
        onRemoveBookmark = { viewModel.acceptBookmark(ReaderBookmarkHudIntent.Remove(it)) },
        onDismissSelection = viewModel::dismissSelection,
        onDismissHighlightDetail = viewModel.dismissHighlightDetail
    )
}
