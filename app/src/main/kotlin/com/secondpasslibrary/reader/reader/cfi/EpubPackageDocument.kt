package com.secondpasslibrary.reader.reader.cfi

internal data class EpubPackageDocument(
    val packagePath: String,
    val packageXml: String,
    val manifest: Map<String, EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    val layout: EpubLayout
) {
    private val spineByHref = spine.associateBy { it.resourceHref }

    fun spineItemForHref(resourceHref: String): EpubSpineItem? =
        spineByHref[normalizeEpubHref(resourceHref)]
}

internal data class EpubManifestItem(
    val id: String,
    val href: String,
    val resourceHref: String,
    val mediaType: String,
    val properties: Set<String>
)

internal data class EpubSpineItem(
    val index: Int,
    val id: String?,
    val idref: String,
    val resourceHref: String,
    val mediaType: String,
    val layout: EpubLayout
)

internal enum class EpubLayout { REFLOWABLE, FIXED }

internal fun normalizeEpubHref(href: String): String {
    val path = href.substringBefore('#').substringBefore('?').replace('\\', '/')
    val segments = ArrayDeque<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit

            ".." -> {
                require(segments.isNotEmpty()) { "EPUB href escapes the publication root." }
                segments.removeLast()
            }

            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/")
}
