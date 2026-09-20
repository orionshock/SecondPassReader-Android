package com.secondpasslibrary.reader.reader.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
@Suppress("CognitiveComplexMethod", "MagicNumber", "LongMethod")
internal fun ReaderSearchPanel(
    state: ReaderSearchState,
    palette: ReaderPalette,
    onQuery: (String) -> Unit,
    onSelect: (ReaderSearchResult) -> Unit,
    onLoadMore: () -> Unit,
    onClose: () -> Unit
) {
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.results.size, state.hasMore) {
        if (state.hasMore && state.results.isNotEmpty()) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            }.distinctUntilChanged().collect { last ->
                if (last >= state.results.lastIndex - 2) onLoadMore()
            }
        }
    }
    Column(
        Modifier.fillMaxHeight().widthIn(max = 400.dp).fillMaxWidth()
            .background(palette.panelSurface).statusBarsPadding()
            .testTag("reader_search_panel")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                AppIconGraphic(AppIcon.Back, "Close search")
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Search this Book") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.primaryForeground,
                    unfocusedTextColor = palette.primaryForeground,
                    focusedPlaceholderColor = palette.secondaryForeground,
                    unfocusedPlaceholderColor = palette.secondaryForeground,
                    focusedBorderColor = palette.primaryForeground,
                    unfocusedBorderColor = palette.border,
                    cursorColor = palette.primaryForeground
                ),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("reader_search_field")
            )
            Spacer(Modifier.padding(4.dp))
        }
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))

            state.error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Couldn’t search this Book.",
                    Modifier.padding(start = 16.dp),
                    color = palette.primaryForeground
                )
                TextButton(onClick = { onQuery(state.query) }) { Text("Retry") }
            }

            state.query.isNotBlank() && !state.hasMore && state.results.isEmpty() -> Text(
                "No matches",
                Modifier.padding(16.dp),
                color = palette.secondaryForeground
            )
        }
        if (state.results.isNotEmpty()) {
            Text(
                "${state.results.size} matches shown",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = palette.secondaryForeground
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(state.results) { result ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            if (state.selected === result.target) {
                                palette.selectedSurface
                            } else {
                                palette.panelSurface
                            }
                        )
                        .clickable {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onSelect(result)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    result.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = palette.secondaryForeground)
                    }
                    Text(result.snippet(), color = palette.primaryForeground)
                }
            }
        }
    }
}

@Suppress("MagicNumber")
private fun ReaderSearchResult.snippet(): AnnotatedString = buildAnnotatedString {
    append(before.takeLast(80))
    if (before.isNotBlank()) append(" ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match) }
    if (after.isNotBlank()) append(" ")
    append(after.take(100))
}
