package com.secondpasslibrary.reader.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsFailure
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarks
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderReadOnlyHighlightDetail
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationIntent
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderHighlightEditDraft
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderPendingHighlight
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatusScope
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaIntent
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_CURRENT_SESSION_EDIT_TAG
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_SESSION_NAME_FIELD_TAG
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_SESSION_NOTES_FIELD_TAG
import com.secondpasslibrary.reader.reader.marginalia.ui.READER_SESSION_SAVE_TAG
import com.secondpasslibrary.reader.reader.navigation.ReaderNavigationIntent
import com.secondpasslibrary.reader.reader.presentation.ReaderMarginaliaPresentationState
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadata
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.READER_TOC_BODY_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_CLOSE_BOOK_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_CLOSE_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_EYEBROW_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_FOOTER_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_HEADER_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_TITLE_TAG
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.toc.ReaderTocEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal abstract class ReaderUiIntegrationTestSupport {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected fun testBookmark(id: String, label: String) = ReaderAnnotation.Bookmark(
        id = id,
        clientId = "client-$id",
        cfi = TEST_ANNOTATION_CFI,
        locationLabel = label,
        updatedAt = "2026-08-28T00:00:00Z"
    )
}
