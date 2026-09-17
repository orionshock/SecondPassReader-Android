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
    val books = result.booksStateOrNull()
    val metadata =
        if (books?.offlineDownloadedOnly == true) {
            listOfNotNull("Downloaded", count).joinToString(" · ")
        } else if (
            result is LibraryResultState.Books && books?.mode == LibraryBooksMode.BROAD_SEARCH
        ) {
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

private fun LibraryState.selectedEntityName(): String? = when (val current = result) {
    is LibraryResultState.Books,
    is LibraryResultState.AuthorIndex,
    is LibraryResultState.SeriesIndex -> null

    is LibraryResultState.AuthorBooks -> current.author.detail?.name ?: current.indexEntry?.name

    is LibraryResultState.SeriesBooks -> current.series.detail?.name ?: current.indexEntry?.name
}

private fun LibraryState.selectedGroup(): LibraryGroupSummary? {
    val groupId = (scope as? LibraryScope.Group)?.id ?: return null
    return groupSelector.groups.firstOrNull { it.id == groupId }
}

private val LibraryGroupSummary.semanticIcon: AppIcon
    get() = if (isPublicGroup) AppIcon.PublicGroup else AppIcon.Group
