package com.secondpasslibrary.reader.reader.annotations

/** Prepares interoperable quote context for an annotation write without changing read models. */
internal object ReaderQuoteContextPolicy {
    fun prepare(exact: String, prefix: String?, suffix: String?): ReaderMutationQuoteContext? {
        val contextBudget = if (exact.length <= SHORT_QUOTE_LENGTH) {
            SHORT_QUOTE_LENGTH - exact.length
        } else {
            minOf((exact.length * LONG_QUOTE_CONTEXT_RATIO).toInt(), MAX_TOTAL_CONTEXT_LENGTH)
        }
        val prefixBudget = contextBudget / 2
        val suffixBudget = contextBudget - prefixBudget
        val normalizedExact = exact.flattenJavascriptWhitespace()
        if (normalizedExact.isEmpty()) return null

        return ReaderMutationQuoteContext(
            exact = normalizedExact,
            prefix = prefix.orEmpty().takeLast(prefixBudget).flattenJavascriptWhitespace(),
            suffix = suffix.orEmpty().take(suffixBudget).flattenJavascriptWhitespace()
        )
    }
}

internal data class ReaderMutationQuoteContext(
    val exact: String,
    val prefix: String,
    val suffix: String
)

private fun String.flattenJavascriptWhitespace(): String = buildString(length) {
    var pendingSpace = false
    for (character in this@flattenJavascriptWhitespace) {
        if (character.isJavascriptWhitespace()) {
            pendingSpace = isNotEmpty()
        } else {
            if (pendingSpace) append(' ')
            append(character)
            pendingSpace = false
        }
    }
}

/** ECMAScript WhiteSpace and LineTerminator code points matched by JavaScript `\s`. */
private fun Char.isJavascriptWhitespace(): Boolean = when (this) {
    in '\u0009'..'\u000D',
    '\u0020',
    '\u00A0',
    '\u1680',
    in '\u2000'..'\u200A',
    '\u2028',
    '\u2029',
    '\u202F',
    '\u205F',
    '\u3000',
    '\uFEFF' -> true

    else -> false
}

private const val SHORT_QUOTE_LENGTH = 500
private const val MAX_TOTAL_CONTEXT_LENGTH = 500
private const val LONG_QUOTE_CONTEXT_RATIO = 0.1
