package com.secondpasslibrary.reader.library.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.library.LibraryAxis
import com.secondpasslibrary.reader.library.LibraryState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySelectorRow(
    state: LibraryState,
    onScopeSelected: (LibraryScope) -> Unit,
    onAxisSelected: (LibraryAxis) -> Unit,
    onRetryGroups: () -> Unit,
    tagControl: @Composable () -> Unit,
    orderingControl: @Composable () -> Unit,
    layoutControl: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val primary = @Composable {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                if (state.advancedGroupsEnabled) {
                    LibraryScopeMenu(state, onScopeSelected, onRetryGroups)
                }
                AxisChoices(state.axis, onAxisSelected)
            }
        }
        val secondary = @Composable {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tagControl()
                orderingControl()
                if (state.resultKind.supportsBookLayout) {
                    layoutControl()
                }
            }
        }
        if (maxWidth < 960.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                primary()
                secondary()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                primary()
                secondary()
            }
        }
    }
}

@Composable
private fun LibraryScopeMenu(
    state: LibraryState,
    onScopeSelected: (LibraryScope) -> Unit,
    onRetryGroups: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedGroup =
        (state.scope as? LibraryScope.Group)?.let { selected ->
            state.groupSelector.groups.firstOrNull { it.id == selected.id }
        }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            ScopeIcon(selectedGroup)
            Text(selectedGroup?.name ?: "All Library")
            if (state.groupSelector.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                AppIconGraphic(AppIcon.Expand, null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScopeMenuItem(
                "All Library",
                AppIcon.LibraryScope,
                state.scope == LibraryScope.Global
            ) {
                expanded = false
                onScopeSelected(LibraryScope.Global)
            }
            state.groupSelector.groups.forEach { group ->
                ScopeMenuItem(
                    group.name,
                    if (group.isPublicGroup) AppIcon.PublicGroup else AppIcon.Group,
                    state.scope == LibraryScope.Group(group.id)
                ) {
                    expanded = false
                    onScopeSelected(LibraryScope.Group(group.id))
                }
            }
            if (state.groupSelector.loading) {
                DropdownMenuItem(text = {
                    Text("Loading library groups…")
                }, onClick = {}, enabled = false)
            } else if (state.groupSelector.failure != null) {
                DropdownMenuItem(
                    text = { Text("Could not load groups · Retry") },
                    onClick = {
                        expanded = false
                        onRetryGroups()
                    }
                )
            }
        }
    }
}

@Composable
private fun ScopeMenuItem(label: String, icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { AppIconGraphic(icon, null) },
        trailingIcon = { if (selected) AppIconGraphic(AppIcon.Confirm, null) }
    )
}

@Composable
private fun ScopeIcon(group: LibraryGroupSummary?) {
    AppIconGraphic(
        when {
            group == null -> AppIcon.LibraryScope
            group.isPublicGroup -> AppIcon.PublicGroup
            else -> AppIcon.Group
        },
        null
    )
}

@Composable
private fun AxisChoices(selected: LibraryAxis, onSelected: (LibraryAxis) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AxisChip(LibraryAxis.BOOKS, AppIcon.Library, selected, onSelected)
        AxisChip(LibraryAxis.AUTHORS, AppIcon.Author, selected, onSelected)
        AxisChip(LibraryAxis.SERIES, AppIcon.Series, selected, onSelected)
    }
}

@Composable
private fun AxisChip(
    axis: LibraryAxis,
    icon: AppIcon,
    selected: LibraryAxis,
    onSelected: (LibraryAxis) -> Unit
) {
    FilterChip(
        selected = axis == selected,
        onClick = { onSelected(axis) },
        label = { Text(axis.name.lowercase().replaceFirstChar(Char::uppercase)) },
        leadingIcon = { AppIconGraphic(icon, null) }
    )
}
