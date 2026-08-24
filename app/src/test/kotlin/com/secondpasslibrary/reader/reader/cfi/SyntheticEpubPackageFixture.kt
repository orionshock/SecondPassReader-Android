package com.secondpasslibrary.reader.reader.cfi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class SyntheticManifestItem(
    val id: String,
    val href: String,
    val mediaType: String = "application/xhtml+xml",
    val properties: String? = null
)

internal data class SyntheticSpineItem(
    val idref: String,
    val id: String? = null,
    val properties: String? = null
)

internal fun syntheticPackageXml(
    manifest: List<SyntheticManifestItem>,
    spine: List<SyntheticSpineItem>,
    packageLayout: String? = null
): String {
    val layoutMetadata = packageLayout?.let { layout ->
        "<metadata><meta property=\"rendition:layout\">$layout</meta></metadata>"
    }.orEmpty()
    val manifestXml = manifest.joinToString(separator = "") { item ->
        val properties = item.properties?.let { " properties=\"$it\"" }.orEmpty()
        "<item id=\"${item.id}\" href=\"${item.href}\" " +
            "media-type=\"${item.mediaType}\"$properties/>"
    }
    val spineXml = spine.joinToString(separator = "") { item ->
        val id = item.id?.let { " id=\"$it\"" }.orEmpty()
        val properties = item.properties?.let { " properties=\"$it\"" }.orEmpty()
        "<itemref idref=\"${item.idref}\"$id$properties/>"
    }
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          $layoutMetadata
          <manifest>$manifestXml</manifest>
          <spine>$spineXml</spine>
        </package>
    """.trimIndent()
}

internal fun writeSyntheticEpub(
    packageXml: String,
    packagePath: String = "OPS/package.opf",
    containerXml: String = syntheticContainerXml(packagePath),
    additionalEntries: Map<String, String> = emptyMap()
) = Files.createTempFile("synthetic-package-", ".epub").toFile().apply {
    deleteOnExit()
    ZipOutputStream(outputStream()).use { zip ->
        zip.writeTextEntry("mimetype", "application/epub+zip")
        zip.writeTextEntry("META-INF/container.xml", containerXml)
        zip.writeTextEntry(packagePath, packageXml)
        additionalEntries.forEach { (path, contents) -> zip.writeTextEntry(path, contents) }
    }
}

internal fun syntheticContainerXml(packagePath: String): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
      <rootfiles>
        <rootfile full-path="$packagePath" media-type="application/oebps-package+xml"/>
      </rootfiles>
    </container>
""".trimIndent()

private fun ZipOutputStream.writeTextEntry(path: String, contents: String) {
    putNextEntry(ZipEntry(path))
    write(contents.toByteArray(StandardCharsets.UTF_8))
    closeEntry()
}
