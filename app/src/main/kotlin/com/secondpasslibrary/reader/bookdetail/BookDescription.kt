package com.secondpasslibrary.reader.bookdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.text.HtmlCompat
import java.util.Locale

private const val COLLAPSED_DESCRIPTION_LINES = 8
private val listTag = Regex("(?i)<\\s*(/?)\\s*(ol|ul|li)\\s*>")
private val fallbackTag = Regex("(?s)<[^>]*>")

@Composable
internal fun BookDescription(description: String?) {
    val rendered = remember(description) { renderBookDescription(description) } ?: return
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    var expandable by rememberSaveable(description) { mutableStateOf(false) }
    Text("Description", style = MaterialTheme.typography.titleMedium)
    Text(
        text = rendered,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { result ->
            if (!expanded && result.hasVisualOverflow) expandable = true
        }
    )
    if (expandable) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show less" else "Show more")
        }
    }
}

/** Interprets server-sanitized presentation markup without changing its persisted representation. */
internal fun renderBookDescription(description: String?): AnnotatedString? {
    if (description.isNullOrBlank()) return null
    val rendered = runCatching {
        AnnotatedString.fromHtml(description.withOrderedListMarkers())
    }.getOrElse {
        AnnotatedString(description.readableFallback())
    }
    return rendered.trimmedOrNull()
}

/* Android's native HTML parser supports unordered lists but not ordered-list numbering. */
private fun String.withOrderedListMarkers(): String {
    val lists = ArrayDeque<DescriptionList>()
    val items = ArrayDeque<DescriptionListKind>()
    return listTag.replace(this) { match ->
        val closing = match.groupValues[1].isNotEmpty()
        when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "ol" -> orderedListTag(closing, lists, match.value)
            "ul" -> unorderedListTag(closing, lists)
            else -> listItemTag(closing, lists, items, match.value)
        }
    }
}

private fun orderedListTag(
    closing: Boolean,
    lists: ArrayDeque<DescriptionList>,
    original: String
): String = if (closing) {
    if (lists.lastOrNull()?.kind == DescriptionListKind.ORDERED) {
        lists.removeLast()
        ""
    } else {
        original
    }
} else {
    lists.addLast(DescriptionList(DescriptionListKind.ORDERED))
    ""
}

private fun unorderedListTag(closing: Boolean, lists: ArrayDeque<DescriptionList>): String {
    if (closing) {
        if (lists.lastOrNull()?.kind == DescriptionListKind.UNORDERED) lists.removeLast()
    } else {
        lists.addLast(DescriptionList(DescriptionListKind.UNORDERED))
    }
    return ""
}

private fun listItemTag(
    closing: Boolean,
    lists: ArrayDeque<DescriptionList>,
    items: ArrayDeque<DescriptionListKind>,
    original: String
): String = if (closing) {
    when (items.removeLastOrNull()) {
        DescriptionListKind.ORDERED -> "</p>"
        DescriptionListKind.UNORDERED -> "</p>"
        null -> original
    }
} else {
    val list = lists.lastOrNull()
    val kind = list?.kind ?: DescriptionListKind.UNORDERED
    items.addLast(kind)
    if (kind == DescriptionListKind.ORDERED) {
        val number = checkNotNull(list).nextNumber++
        "<p>$number. "
    } else {
        "<p>\u2022 "
    }
}

private fun String.readableFallback(): String = runCatching {
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
}.getOrElse {
    replace(fallbackTag, "")
}

private fun AnnotatedString.trimmedOrNull(): AnnotatedString? {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return null
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, end)
}

private enum class DescriptionListKind { ORDERED, UNORDERED }

private data class DescriptionList(val kind: DescriptionListKind, var nextNumber: Int = 1)
