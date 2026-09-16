package com.secondpasslibrary.reader.library.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.library.LibraryState

@Composable
internal fun LibraryTagFilterButton(selectedTag: LibraryCatalogTag?, onClick: () -> Unit) {
    FilterChip(
        selected = selectedTag != null,
        onClick = onClick,
        label = {
            Text(
                selectedTag?.name ?: "Catalog Tags",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { AppIconGraphic(AppIcon.Tag, null) },
        trailingIcon = { AppIconGraphic(AppIcon.Expand, null) }
    )
}

@Composable
internal fun CatalogTagFilterSheet(
    state: LibraryState,
    onDismiss: () -> Unit,
    onTagSelected: (LibraryCatalogTag?) -> Unit,
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 16.dp)) {
            Surface(
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(max = 460.dp)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column {
                    TagSheetHeader(state.tagSelector.tags.size, onDismiss)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TagRows(state, onTagSelected, onRetry, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TagSheetHeader(tagCount: Int, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Catalog Tags", style = MaterialTheme.typography.titleMedium)
            if (tagCount > 0) {
                Text(
                    "$tagCount available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

@Composable
private fun TagRows(
    state: LibraryState,
    onTagSelected: (LibraryCatalogTag?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        item {
            TagRow(
                name = "All tags",
                countLabel = null,
                selected = state.selectedTag == null,
                onClick = { onTagSelected(null) }
            )
        }
        items(state.tagSelector.tags, key = LibraryCatalogTag::id) { tag ->
            TagRow(
                name = tag.name,
                countLabel = tag.bookCountLabel(),
                selected = state.selectedTag?.id == tag.id,
                onClick = { onTagSelected(tag) }
            )
        }
        when {
            state.tagSelector.loading -> item { TagLoading() }
            state.tagSelector.failure != null -> item { TagFailure(onRetry) }
            state.tagSelector.loaded && state.tagSelector.tags.isEmpty() -> item { TagEmpty() }
        }
    }
}

@Composable
private fun TagRow(name: String, countLabel: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconGraphic(AppIcon.Tag, null)
            Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            countLabel?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (selected) AppIconGraphic(AppIcon.Confirm, null)
        }
    }
}

@Composable
private fun TagLoading() {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Loading Catalog Tags", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TagFailure(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Couldn’t load Catalog Tags.", color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun TagEmpty() {
    Text(
        "No Catalog Tags in these results.",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal fun LibraryCatalogTag.bookCountLabel(): String =
    "$bookCount ${if (bookCount == 1) "book" else "books"}"
