package com.secondpasslibrary.reader.reader.cfi

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

private const val CONTAINER_PATH = "META-INF/container.xml"
private const val PACKAGE_MEDIA_TYPE = "application/oebps-package+xml"
private const val FIXED_LAYOUT = "pre-paginated"
private const val REFLOWABLE_LAYOUT = "reflowable"
private val FORBIDDEN_XML_DECLARATION = Regex(
    pattern = "<!\\s*(DOCTYPE|ENTITY)",
    option = RegexOption.IGNORE_CASE
)

internal fun interface EpubPackageResolver {
    fun resolve(file: File): EpubPackageDocument
}

internal class ZipEpubPackageResolver : EpubPackageResolver {
    override fun resolve(file: File): EpubPackageDocument = ZipFile(file).use { zip ->
        val container = zip.readRequiredEntry(CONTAINER_PATH)
        val packagePath = packagePath(parseXml(container))
        val packageBytes = zip.readRequiredEntry(packagePath)
        parsePackage(packagePath, packageBytes)
    }

    private fun packagePath(container: Document): String {
        val rootfiles = container.documentElement
            .descendants("rootfile")
            .filter {
                it.getAttribute("media-type").let { type ->
                    type.isBlank() ||
                        type == PACKAGE_MEDIA_TYPE
                }
            }
            .map { normalizeEpubHref(it.requiredAttribute("full-path")) }
            .distinct()
        require(rootfiles.size == 1) {
            "EPUB container must identify exactly one package document."
        }
        return rootfiles.single()
    }

    private fun parsePackage(packagePath: String, bytes: ByteArray): EpubPackageDocument {
        val document = parseXml(bytes)
        val packageRoot = document.documentElement
        require(packageRoot.localName == "package") { "EPUB package root is missing." }
        val packageLayout = packageRoot
            .descendants("meta")
            .firstOrNull { it.getAttribute("property") == "rendition:layout" }
            ?.textContent
            ?.trim()
            .toLayout(default = EpubLayout.REFLOWABLE)
        val manifest = parseManifest(packageRoot, packagePath)
        val spine = parseSpine(packageRoot, manifest, packageLayout)
        require(spine.isNotEmpty()) { "EPUB package spine is empty." }
        return EpubPackageDocument(
            packagePath = packagePath,
            packageXml = bytes.toString(StandardCharsets.UTF_8),
            manifest = manifest,
            spine = spine,
            layout = packageLayout
        )
    }

    private fun parseManifest(root: Element, packagePath: String): Map<String, EpubManifestItem> {
        val packageDirectory = packagePath.substringBeforeLast('/', missingDelimiterValue = "")
        val items = root.descendants("manifest").singleOrNull()
            ?.childElements("item")
            .orEmpty()
            .map { element ->
                val id = element.requiredAttribute("id")
                val href = element.requiredAttribute("href")
                EpubManifestItem(
                    id = id,
                    href = href,
                    resourceHref = normalizeEpubHref(
                        listOf(packageDirectory, href).filter(String::isNotBlank).joinToString("/")
                    ),
                    mediaType = element.requiredAttribute("media-type"),
                    properties = element.tokenAttribute("properties")
                )
            }
        require(items.isNotEmpty()) { "EPUB package manifest is empty." }
        require(items.map(EpubManifestItem::id).distinct().size == items.size) {
            "EPUB package contains duplicate manifest IDs."
        }
        return items.associateBy(EpubManifestItem::id)
    }

    private fun parseSpine(
        root: Element,
        manifest: Map<String, EpubManifestItem>,
        packageLayout: EpubLayout
    ): List<EpubSpineItem> {
        val spine = root.descendants("spine").singleOrNull()
            ?: error("EPUB package must contain exactly one spine.")
        return spine.childElements("itemref").mapIndexed { index, element ->
            val idref = element.requiredAttribute("idref")
            val manifestItem = requireNotNull(manifest[idref]) {
                "EPUB spine references unknown manifest item $idref."
            }
            EpubSpineItem(
                index = index,
                id = element.getAttribute("id").ifBlank { null },
                idref = idref,
                resourceHref = manifestItem.resourceHref,
                mediaType = manifestItem.mediaType,
                layout = element.tokenAttribute("properties").layoutOverride() ?: packageLayout
            )
        }
    }
}

private fun ZipFile.readRequiredEntry(path: String): ByteArray {
    val normalized = normalizeEpubHref(path)
    require(normalized == path.replace('\\', '/')) { "EPUB entry path is not canonical." }
    val entry = requireNotNull(getEntry(normalized)) { "EPUB entry $normalized is missing." }
    require(!entry.isDirectory) { "EPUB entry $normalized is not a file." }
    return getInputStream(entry).use { it.readBytes() }
}

private fun parseXml(bytes: ByteArray): Document {
    require(!bytes.containsForbiddenXmlDeclaration()) {
        "EPUB XML document type and entity declarations are forbidden."
    }
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setExpandEntityReferences(false)
    }
    val builder = factory.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> throw SAXException("External XML entities are forbidden.") }
    }
    return builder.parse(ByteArrayInputStream(bytes))
}

private fun ByteArray.containsForbiddenXmlDeclaration(): Boolean {
    val ascii = filterNot { it == 0.toByte() }
        .toByteArray()
        .toString(StandardCharsets.ISO_8859_1)
    return FORBIDDEN_XML_DECLARATION.containsMatchIn(ascii)
}

private fun Element.descendants(localName: String): List<Element> {
    val nodes = getElementsByTagNameNS("*", localName)
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

private fun Element.childElements(localName: String): List<Element> = childNodes.asSequence()
    .filterIsInstance<Element>()
    .filter { it.localName == localName }
    .toList()

private fun Element.requiredAttribute(name: String): String =
    getAttribute(name).also { require(it.isNotBlank()) { "EPUB $localName is missing $name." } }

private fun Element.tokenAttribute(name: String): Set<String> = getAttribute(name)
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .toSet()

private fun Set<String>.layoutOverride(): EpubLayout? = when {
    "rendition:layout-pre-paginated" in this -> EpubLayout.FIXED
    "rendition:layout-reflowable" in this -> EpubLayout.REFLOWABLE
    else -> null
}

private fun String?.toLayout(default: EpubLayout): EpubLayout = when (this) {
    FIXED_LAYOUT -> EpubLayout.FIXED
    REFLOWABLE_LAYOUT, null, "" -> default
    else -> default
}

private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> = sequence {
    for (index in 0 until length) yield(item(index))
}
