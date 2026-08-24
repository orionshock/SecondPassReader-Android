package com.secondpasslibrary.reader.reader.cfi

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

private const val CONTAINER_PATH = "META-INF/container.xml"
private const val PACKAGE_MEDIA_TYPE = "application/oebps-package+xml"
private const val FIXED_LAYOUT = "pre-paginated"
private const val REFLOWABLE_LAYOUT = "reflowable"
private const val DEFAULT_MAX_CONTAINER_BYTES = 256 * 1024
private const val DEFAULT_MAX_PACKAGE_BYTES = 8 * 1024 * 1024
private const val DEFAULT_MAX_ZIP_ENTRY_COUNT = 65_536

internal fun interface EpubPackageResolver {
    fun resolve(file: File): EpubPackageDocument
}

internal data class EpubPackageLimits(
    val maxContainerBytes: Int = DEFAULT_MAX_CONTAINER_BYTES,
    val maxPackageBytes: Int = DEFAULT_MAX_PACKAGE_BYTES,
    val maxZipEntryCount: Int = DEFAULT_MAX_ZIP_ENTRY_COUNT
) {
    init {
        require(maxContainerBytes > 0)
        require(maxPackageBytes > 0)
        require(maxZipEntryCount > 0)
    }
}

internal class ZipEpubPackageResolver(private val limits: EpubPackageLimits = EpubPackageLimits()) :
    EpubPackageResolver {
    override fun resolve(file: File): EpubPackageDocument = ZipFile(file).use { zip ->
        zip.validateEntries(limits.maxZipEntryCount)
        val container = zip.readRequiredEntry(CONTAINER_PATH, limits.maxContainerBytes)
        val packagePath = packagePath(parseXml(container))
        val packageBytes = zip.readRequiredEntry(packagePath, limits.maxPackageBytes)
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
            packageXml = document.toPortableXml(),
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
        val items = spine.childElements("itemref").mapIndexed { index, element ->
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
        val itemrefIds = items.mapNotNull(EpubSpineItem::id)
        require(itemrefIds.distinct().size == itemrefIds.size) {
            "EPUB package contains duplicate spine itemref IDs."
        }
        return items
    }
}

private fun ZipFile.validateEntries(maxEntryCount: Int) {
    val names = mutableSetOf<String>()
    var count = 0
    entries().asSequence().forEach { entry ->
        count += 1
        require(count <= maxEntryCount) {
            "EPUB archive contains more than $maxEntryCount entries."
        }
        val normalizedSeparators = entry.name.replace('\\', '/')
        require(names.add(normalizedSeparators)) {
            "EPUB archive contains duplicate entry $normalizedSeparators."
        }
    }
}

private fun ZipFile.readRequiredEntry(path: String, maxBytes: Int): ByteArray {
    val normalized = normalizeEpubHref(path)
    require(normalized == path.replace('\\', '/')) { "EPUB entry path is not canonical." }
    val entry = requireNotNull(getEntry(normalized)) { "EPUB entry $normalized is missing." }
    require(!entry.isDirectory) { "EPUB entry $normalized is not a file." }
    require(entry.size < 0 || entry.size <= maxBytes.toLong()) {
        "EPUB entry $normalized exceeds the $maxBytes byte limit."
    }
    return getInputStream(entry).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) {
                "EPUB entry $normalized exceeds the $maxBytes byte limit."
            }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

private fun parseXml(bytes: ByteArray): Document {
    require(!EpubXmlSafety.containsForbiddenDeclaration(bytes)) {
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

private fun Document.toPortableXml(): String {
    val output = StringWriter()
    val transformer = TransformerFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }.newTransformer().apply {
        setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name())
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    }
    transformer.transform(DOMSource(this), StreamResult(output))
    return output.toString()
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
