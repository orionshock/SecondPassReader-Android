package com.secondpasslibrary.reader.reader.presentation

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsController
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarks
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarksController
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderHighlightActivationController
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderReadOnlyHighlightDetail
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationController
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionController
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersController
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPolicyController
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataController
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Existing child state values, grouped for a single screen subscription. */
internal data class ReaderMarginaliaPresentationState(
    val annotations: ReaderAnnotationsState = ReaderAnnotationsState(),
    val pageBookmarks: ReaderVisiblePageBookmarks = ReaderVisiblePageBookmarks(),
    val marginaliaLayers: ReaderMarginaliaLayersState = ReaderMarginaliaLayersState(),
    val autoShowPreviousMarginalia: Boolean = true,
    val selection: ReaderSelection? = null,
    val annotationMutations: ReaderAnnotationMutationState = ReaderAnnotationMutationState(),
    val highlightDetail: ReaderReadOnlyHighlightDetail? = null,
    val sessionMetadata: ReaderSessionMetadataState = ReaderSessionMetadataState()
)

internal fun marginaliaPresentationState(
    scope: CoroutineScope,
    annotations: ReaderAnnotationsController,
    layers: ReaderMarginaliaLayersController,
    policy: ReaderMarginaliaLayerPolicyController,
    bookmarks: ReaderVisiblePageBookmarksController,
    selection: ReaderSelectionController,
    mutations: ReaderAnnotationMutationController,
    highlights: ReaderHighlightActivationController,
    metadata: ReaderSessionMetadataController
) = combine(
    combine(annotations.state, layers.state, policy.autoShowPrevious, bookmarks.state) {
            current,
            history,
            autoShow,
            visible
        ->
        ReaderMarginaliaPresentationState(
            annotations = current,
            marginaliaLayers = history,
            autoShowPreviousMarginalia = autoShow,
            pageBookmarks = visible
        )
    },
    selection.selection,
    mutations.state,
    highlights.detail,
    metadata.state
) { collections, selected, mutation, highlight, session ->
    collections.copy(
        selection = selected,
        annotationMutations = mutation,
        highlightDetail = highlight,
        sessionMetadata = session
    )
}.stateIn(scope, SharingStarted.Eagerly, ReaderMarginaliaPresentationState())
