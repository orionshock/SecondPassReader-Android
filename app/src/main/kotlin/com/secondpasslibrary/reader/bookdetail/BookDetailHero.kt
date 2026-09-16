package com.secondpasslibrary.reader.bookdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

private const val BOOK_COVER_ASPECT_RATIO = 2f / 3f

@Composable
internal fun BookDetailCover(book: LibraryBookDetail, modifier: Modifier = Modifier) {
    PublicBookCover(
        reference = book.cover,
        title = book.title,
        modifier = modifier.aspectRatio(BOOK_COVER_ASPECT_RATIO),
        contentScale = ContentScale.Fit
    )
}

@Composable
internal fun BookDetailMetadata(
    book: LibraryBookDetail,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = book.toPresentation()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(presentation.title, style = MaterialTheme.typography.headlineLarge)
        presentation.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        presentation.seriesLabel?.let { value ->
            MetadataField(
                "Series",
                value,
                presentation.seriesNavigationId?.let { id -> { onSeriesSelected(id) } }
            )
        }
        presentation.authorsLabel?.let { value ->
            MetadataField(
                "Author",
                value,
                presentation.authorNavigationId?.let { id -> { onAuthorSelected(id) } }
            )
        }
        book.publisher?.takeIf(String::isNotBlank)?.let { MetadataField("Publisher", it) }
        book.language?.takeIf(String::isNotBlank)?.let { MetadataField("Language", it) }
        presentation.publicationLabel?.let { MetadataField("Published", it) }
        presentation.fileLabel?.let { MetadataField("Format", it) }
    }
}

@Composable
private fun MetadataField(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier
                .defaultMinSize(minHeight = 32.dp)
                .then(
                    if (onClick == null) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Open $label",
                            onClick = onClick
                        )
                    }
                )
                .padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (onClick == null) {
                    TextDecoration.None
                } else {
                    TextDecoration.Underline
                }
            ),
            color = if (onClick == null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
internal fun BookDetailSupportingContent(
    book: LibraryBookDetail,
    onTagSelected: (String, String) -> Unit
) {
    val presentation = book.toPresentation()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (book.catalogTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.catalogTags.forEach { tag ->
                    AssistChip(
                        onClick = { onTagSelected(tag.id, tag.slug) },
                        label = { Text(tag.name) }
                    )
                }
            }
        }
        BookDescription(presentation.description)
    }
}

internal enum class BookDetailActionLayout { VERTICAL, HORIZONTAL }

internal enum class BookDetailActionKind { READ_BOOK, READING_SESSIONS, ADD_TO_SHELF }

internal data class BookDetailActionPresentation(
    val kind: BookDetailActionKind,
    val label: String,
    val icon: AppIcon,
    val enabled: Boolean
)

internal fun bookDetailActionPresentations(
    readBookEnabled: Boolean = false,
    serverActionsAvailable: Boolean = true
) = listOf(
    BookDetailActionPresentation(
        BookDetailActionKind.READ_BOOK,
        "Read Book",
        AppIcon.Book,
        readBookEnabled
    ),
    BookDetailActionPresentation(
        BookDetailActionKind.READING_SESSIONS,
        "Reading Sessions",
        AppIcon.ReadingHistory,
        serverActionsAvailable
    ),
    BookDetailActionPresentation(
        BookDetailActionKind.ADD_TO_SHELF,
        "Add to Shelf",
        AppIcon.Shelf,
        serverActionsAvailable
    )
)

@Composable
internal fun BookDetailActions(
    onReadBook: () -> Unit,
    onReadingSessions: () -> Unit,
    onAddToShelf: () -> Unit,
    readBookEnabled: Boolean,
    serverActionsAvailable: Boolean,
    layout: BookDetailActionLayout,
    modifier: Modifier = Modifier
) {
    val callbacks = mapOf(
        BookDetailActionKind.READ_BOOK to onReadBook,
        BookDetailActionKind.READING_SESSIONS to onReadingSessions,
        BookDetailActionKind.ADD_TO_SHELF to onAddToShelf
    )
    val actions = bookDetailActionPresentations(readBookEnabled, serverActionsAvailable)
    if (layout == BookDetailActionLayout.VERTICAL) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEach { action ->
                BookDetailActionTile(
                    action,
                    requireNotNull(callbacks[action.kind]),
                    Modifier.fillMaxWidth(),
                    height = 72.dp
                )
            }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { action ->
                BookDetailActionTile(
                    action,
                    requireNotNull(callbacks[action.kind]),
                    Modifier.weight(1f),
                    height = 88.dp
                )
            }
        }
    }
}

@Composable
private fun BookDetailActionTile(
    action: BookDetailActionPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp
) {
    OutlinedButton(
        onClick = onClick,
        enabled = action.enabled,
        modifier = modifier.height(height).semantics {
            if (!action.enabled) contentDescription = "${action.label}, unavailable"
        },
        contentPadding = PaddingValues(8.dp)
    ) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconGraphic(action.icon, null, Modifier.size(24.dp))
            Text(
                action.label,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
