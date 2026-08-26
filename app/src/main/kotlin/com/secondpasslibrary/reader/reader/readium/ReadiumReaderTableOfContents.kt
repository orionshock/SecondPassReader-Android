package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.toc.ReaderTableOfContents
import com.secondpasslibrary.reader.reader.toc.ReaderTocEntry
import org.readium.r2.shared.publication.Link

internal class ReadiumReaderTableOfContents(
    links: List<Link>,
    readingOrder: List<Link>,
    private val binding: ReadiumPublicationNavigatorBinding
) : ReaderTableOfContents {
    private val targets = mutableMapOf<ReaderPublicationTarget, Link>()
    private val readingOrderHrefs = readingOrder.mapNotNullTo(mutableSetOf()) {
        it.href.normalizedReadingOrderHref()
    }

    override val entries: List<ReaderTocEntry> = links.mapNotNull(::mapEntry)

    override suspend fun goTo(target: ReaderPublicationTarget): ReaderPublicationNavigationResult {
        val link = targets[target] ?: return ReaderPublicationNavigationResult.REJECTED
        return if (binding.goTo(link)) {
            ReaderPublicationNavigationResult.NAVIGATED
        } else {
            ReaderPublicationNavigationResult.UNAVAILABLE
        }
    }

    private fun mapEntry(link: Link): ReaderTocEntry? {
        val children = link.children.mapNotNull(::mapEntry)
        val title = link.title?.trim().orEmpty().ifBlank {
            if (children.isEmpty()) return null else UNTITLED_SECTION
        }
        val target = link.toPublicationTarget()?.also { targets[it] = link }
        return ReaderTocEntry(title = title, target = target, children = children)
    }

    private fun Link.toPublicationTarget(): ReaderPublicationTarget? {
        val raw = href.toString().trim()
        val resourceHref = href.normalizedReadingOrderHref()
        val isInternalReadingTarget = raw.isNotEmpty() && !raw.hasExternalScheme() &&
            resourceHref != null && resourceHref in readingOrderHrefs
        return if (isInternalReadingTarget) {
            runCatching { ReaderPublicationTarget(raw) }.getOrNull()
        } else {
            null
        }
    }
}

private fun Any.normalizedReadingOrderHref(): String? =
    runCatching { normalizeEpubHref(toString()) }.getOrNull()?.takeIf(String::isNotBlank)

private fun String.hasExternalScheme(): Boolean =
    startsWith("//") || URI_SCHEME.containsMatchIn(this)

private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private const val UNTITLED_SECTION = "Untitled section"
