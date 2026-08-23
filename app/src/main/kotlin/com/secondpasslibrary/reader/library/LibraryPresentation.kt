package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.design.components.AppBarContextEmphasis
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.library.books.LibraryBooksMode
import com.secondpasslibrary.reader.library.chrome.countLabel
import com.secondpasslibrary.reader.library.chrome.label
import com.secondpasslibrary.reader.library.chrome.resultCount

internal fun LibraryState.appBarPresentation(): AppBarPresentation {
    val group = selectedGroup()
    val selectedEntity = selectedEntityName()
    val count = resultCount()?.let { "$it ${countLabel(it)}" }
    val metadata =
        if (axis == LibraryAxis.BOOKS && books.mode == LibraryBooksMode.BROAD_SEARCH) {
            listOfNotNull("Global results", count).joinToString(" · ")
        } else {
            count
        }
    return AppBarPresentation(
        navigation = AppBarNavigation.MENU,
        context = if (group == null) "Library" else "Library:",
        contextIcon = group?.semanticIcon,
        contextDetail = group?.name,
        contextEmphasis = AppBarContextEmphasis.TITLE,
        separator = " — ",
        title = listOfNotNull(axis.label, selectedEntity).joinToString(" › "),
        metadata = metadata
    )
}

private fun LibraryState.selectedEntityName(): String? = when (axis) {
    LibraryAxis.BOOKS -> null

    LibraryAxis.AUTHORS ->
        authors.selected?.let { selected ->
            selected.detail?.name ?: authors.items.firstOrNull { it.id == selected.id }?.name
        }

    LibraryAxis.SERIES ->
        series.selected?.let { selected ->
            selected.detail?.name ?: series.items.firstOrNull { it.id == selected.id }?.name
        }
}

private fun LibraryState.selectedGroup(): LibraryGroupSummary? {
    val groupId = (scope as? LibraryScope.Group)?.id ?: return null
    return groupSelector.groups.firstOrNull { it.id == groupId }
}

private val LibraryGroupSummary.semanticIcon: AppIcon
    get() = if (isPublicGroup) AppIcon.PublicGroup else AppIcon.Group
