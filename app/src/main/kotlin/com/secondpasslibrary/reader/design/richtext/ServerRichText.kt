package com.secondpasslibrary.reader.design.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import java.util.Locale

private val listTag = Regex("(?i)<\\s*(/?)\\s*(ol|ul|li)\\s*>")

/** Presents the server-sanitized descriptive-markup contract without changing its source value. */
@Composable
internal fun ServerRichText(
    value: String?,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = LocalContentColor.current,
    collapsedMaxLines: Int = Int.MAX_VALUE,
    expandOverflow: Boolean = false,
    moreLabel: String = "More",
    lessLabel: String = "Less"
) {
    val rendered = remember(value) { renderServerRichText(value) } ?: return
    var expanded by rememberSaveable(value) { mutableStateOf(false) }
    var canExpand by rememberSaveable(value) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = rendered,
            color = color,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (expandOverflow && !expanded) canExpand = result.hasVisualOverflow
            }
        )
        if (expandOverflow && canExpand) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) lessLabel else moreLabel)
            }
        }
    }
}

internal fun renderServerRichText(value: String?): AnnotatedString? {
    val sanitized = ServerRichTextSanitizer.sanitize(value) ?: return null
    val rendered = runCatching {
        AnnotatedString.fromHtml(sanitized.withOrderedListMarkers())
    }.getOrElse {
        AnnotatedString(sanitized.readablePlainText())
    }
    return rendered.trimmedOrNull()
}

internal fun serverRichTextPlainText(value: String?): String? = renderServerRichText(value)?.text

/* Android's native HTML parser supports unordered lists but not ordered-list numbering. */
private fun String.withOrderedListMarkers(): String {
    val lists = ArrayDeque<ServerRichTextList>()
    val items = ArrayDeque<ServerRichTextListKind>()
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
    lists: ArrayDeque<ServerRichTextList>,
    original: String
): String = if (closing) {
    if (lists.lastOrNull()?.kind == ServerRichTextListKind.ORDERED) {
        lists.removeLast()
        ""
    } else {
        original
    }
} else {
    lists.addLast(ServerRichTextList(ServerRichTextListKind.ORDERED))
    ""
}

private fun unorderedListTag(closing: Boolean, lists: ArrayDeque<ServerRichTextList>): String {
    if (closing) {
        if (lists.lastOrNull()?.kind == ServerRichTextListKind.UNORDERED) lists.removeLast()
    } else {
        lists.addLast(ServerRichTextList(ServerRichTextListKind.UNORDERED))
    }
    return ""
}

private fun listItemTag(
    closing: Boolean,
    lists: ArrayDeque<ServerRichTextList>,
    items: ArrayDeque<ServerRichTextListKind>,
    original: String
): String = if (closing) {
    when (items.removeLastOrNull()) {
        ServerRichTextListKind.ORDERED,
        ServerRichTextListKind.UNORDERED -> "</p>"

        null -> original
    }
} else {
    val list = lists.lastOrNull()
    val kind = list?.kind ?: ServerRichTextListKind.UNORDERED
    items.addLast(kind)
    if (kind == ServerRichTextListKind.ORDERED) {
        val number = checkNotNull(list).nextNumber++
        "<p>$number. "
    } else {
        "<p>\u2022 "
    }
}

private fun String.readablePlainText(): String = runCatching {
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
}.getOrDefault("")

private fun AnnotatedString.trimmedOrNull(): AnnotatedString? {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return null
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, end)
}

private enum class ServerRichTextListKind { ORDERED, UNORDERED }

private data class ServerRichTextList(val kind: ServerRichTextListKind, var nextNumber: Int = 1)
