package com.secondpasslibrary.reader.reader.cfi

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubPackageResolverTest {
    private val resolver = ZipEpubPackageResolver()

    @Test
    fun `resolves manifest and spine identity through canonical resource hrefs`() {
        val packageXml = syntheticPackageXml(
            manifest = listOf(
                SyntheticManifestItem("chapter-one", "text/chapter-1.xhtml"),
                SyntheticManifestItem("chapter-two", "../shared/chapter-2.xhtml")
            ),
            spine = listOf(
                SyntheticSpineItem("chapter-one", id = "itemref-one"),
                SyntheticSpineItem(
                    "chapter-two",
                    id = "itemref-two",
                    properties = "rendition:layout-pre-paginated"
                )
            )
        )

        val document = resolver.resolve(writeSyntheticEpub(packageXml))

        assertEquals("OPS/package.opf", document.packagePath)
        assertEquals(setOf("chapter-one", "chapter-two"), document.manifest.keys)
        assertEquals("OPS/text/chapter-1.xhtml", document.manifest["chapter-one"]?.resourceHref)
        assertEquals("shared/chapter-2.xhtml", document.manifest["chapter-two"]?.resourceHref)
        assertEquals(EpubLayout.REFLOWABLE, document.layout)
        assertEquals(
            EpubSpineItem(
                index = 0,
                id = "itemref-one",
                idref = "chapter-one",
                resourceHref = "OPS/text/chapter-1.xhtml",
                mediaType = "application/xhtml+xml",
                layout = EpubLayout.REFLOWABLE
            ),
            document.spineItemForHref("OPS/text/./chapter-1.xhtml?query=yes#fragment")
        )
        assertEquals(EpubLayout.FIXED, document.spine[1].layout)
    }

    @Test
    fun `preserves the exact itemref at the spine index encoded by package step 58`() {
        val manifest = (1..29).map { index ->
            SyntheticManifestItem(
                id = if (index == 29) "id38" else "chapter-$index",
                href = if (index == 29) {
                    "text/part0027.html"
                } else {
                    "text/fixture-${index.toString().padStart(2, '0')}.html"
                }
            )
        }
        val spine = manifest.mapIndexed { index, item ->
            SyntheticSpineItem(idref = item.id, id = "itemref-${index + 1}")
        }

        val document = resolver.resolve(
            writeSyntheticEpub(syntheticPackageXml(manifest = manifest, spine = spine))
        )

        val packageStep58Index = 58 / 2 - 1
        val target = document.spine[packageStep58Index]
        assertEquals(28, target.index)
        assertEquals("id38", target.idref)
        assertEquals("itemref-29", target.id)
        assertEquals("OPS/text/part0027.html", target.resourceHref)
    }

    @Test
    fun `returns no target when multiple spine items address the same href`() {
        val document = resolver.resolve(
            writeSyntheticEpub(
                syntheticPackageXml(
                    manifest = listOf(
                        SyntheticManifestItem("first", "text/reused.xhtml"),
                        SyntheticManifestItem("second", "text/reused.xhtml")
                    ),
                    spine = listOf(
                        SyntheticSpineItem("first"),
                        SyntheticSpineItem("second")
                    )
                )
            )
        )

        assertNull(document.spineItemForHref("OPS/text/reused.xhtml"))
    }

    @Test
    fun `honors package fixed layout and item reflowable override`() {
        val document = resolver.resolve(
            writeSyntheticEpub(
                syntheticPackageXml(
                    manifest = listOf(
                        SyntheticManifestItem("fixed", "fixed.xhtml"),
                        SyntheticManifestItem("reflowable", "reflowable.xhtml")
                    ),
                    spine = listOf(
                        SyntheticSpineItem("fixed"),
                        SyntheticSpineItem(
                            "reflowable",
                            properties = "rendition:layout-reflowable"
                        )
                    ),
                    packageLayout = "pre-paginated"
                )
            )
        )

        assertEquals(EpubLayout.FIXED, document.layout)
        assertEquals(EpubLayout.FIXED, document.spine[0].layout)
        assertEquals(EpubLayout.REFLOWABLE, document.spine[1].layout)
    }

    @Test
    fun `rejects ambiguous package documents`() {
        val container = """
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
                <rootfile full-path="OPS/alternate.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()
        val packageXml = oneChapterPackage()

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(writeSyntheticEpub(packageXml, containerXml = container))
        }
    }

    @Test
    fun `rejects duplicate rootfile declarations even when they name the same package`() {
        val container = """
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OPS/package.opf" media-type="$PACKAGE_MEDIA_TYPE"/>
                <rootfile full-path="OPS/package.opf" media-type="$PACKAGE_MEDIA_TYPE"/>
              </rootfiles>
            </container>
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(writeSyntheticEpub(oneChapterPackage(), containerXml = container))
        }
    }

    @Test
    fun `preserves package semantics across XML encodings`() {
        listOf(
            XmlEncoding(StandardCharsets.UTF_8, "UTF-8"),
            XmlEncoding(
                StandardCharsets.UTF_16LE,
                "UTF-16",
                byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            ),
            XmlEncoding(
                StandardCharsets.UTF_16BE,
                "UTF-16",
                byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            )
        ).forEach { encoding ->
            val packageXml = syntheticPackageXml(
                manifest = listOf(SyntheticManifestItem("chapter", "text/caf\u00e9.xhtml")),
                spine = listOf(SyntheticSpineItem("chapter"))
            ).replace("encoding=\"UTF-8\"", "encoding=\"${encoding.declaration}\"")

            val document = resolver.resolve(
                writeSyntheticEpub(
                    packageBytes = encoding.bom + packageXml.toByteArray(encoding.charset)
                )
            )

            assertEquals("OPS/text/caf\u00e9.xhtml", document.spine.single().resourceHref)
            assertFalse(encoding.charset.name(), document.packageXml.contains('\u0000'))
            assertFalse(encoding.charset.name(), document.packageXml.contains('\uFFFD'))
            assertEquals("text/caf\u00e9.xhtml", document.packageXmlManifestHref())
        }
    }

    @Test
    fun `bounds container package and archive entry counts`() {
        val packageXml = oneChapterPackage()
        val containerBytes = syntheticContainerXml("OPS/package.opf")
            .toByteArray(StandardCharsets.UTF_8)
        val packageBytes = packageXml.toByteArray(StandardCharsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            ZipEpubPackageResolver(
                EpubPackageLimits(maxContainerBytes = containerBytes.size - 1)
            ).resolve(writeSyntheticEpub(packageXml))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ZipEpubPackageResolver(
                EpubPackageLimits(maxPackageBytes = packageBytes.size - 1)
            ).resolve(writeSyntheticEpub(packageXml))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ZipEpubPackageResolver(
                EpubPackageLimits(maxZipEntryCount = 2)
            ).resolve(writeSyntheticEpub(packageXml))
        }
    }

    @Test
    fun `rejects duplicate ZIP entry names`() {
        val packageXml = oneChapterPackage()
        val epub = writeSyntheticEpub(
            packageXml = packageXml,
            packagePath = "OPS/first.opf",
            additionalEntries = mapOf("OPS/other.opf" to packageXml)
        ).replaceZipEntryName("OPS/other.opf", "OPS/first.opf")

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(epub)
        }
    }

    @Test
    fun `rejects package href traversal beyond the publication root`() {
        val packageXml = syntheticPackageXml(
            manifest = listOf(SyntheticManifestItem("chapter", "../../outside.xhtml")),
            spine = listOf(SyntheticSpineItem("chapter"))
        )

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(writeSyntheticEpub(packageXml))
        }
    }

    @Test
    fun `rejects duplicate manifest identity and unknown spine targets`() {
        val duplicateManifest = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="same" href="one.xhtml" media-type="application/xhtml+xml"/>
                <item id="same" href="two.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="same"/></spine>
            </package>
        """.trimIndent()
        val unknownSpineTarget = syntheticPackageXml(
            manifest = listOf(SyntheticManifestItem("known", "known.xhtml")),
            spine = listOf(SyntheticSpineItem("missing"))
        )

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(writeSyntheticEpub(duplicateManifest))
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(writeSyntheticEpub(unknownSpineTarget))
        }
    }

    @Test
    fun `rejects XML document type declarations before entity expansion`() {
        val maliciousContainer = """
            <?xml version="1.0"?>
            <!DOCTYPE container [<!ENTITY local SYSTEM "file:///definitely-not-readable">]>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile
                    full-path="&local;"
                    media-type="application/oebps-package+xml"
                />
              </rootfiles>
            </container>
        """.trimIndent()

        assertThrows(Exception::class.java) {
            resolver.resolve(
                writeSyntheticEpub(
                    packageXml = oneChapterPackage(),
                    containerXml = maliciousContainer
                )
            )
        }
    }

    private fun oneChapterPackage(): String = syntheticPackageXml(
        manifest = listOf(SyntheticManifestItem("chapter", "chapter.xhtml")),
        spine = listOf(SyntheticSpineItem("chapter"))
    )

    private data class XmlEncoding(
        val charset: Charset,
        val declaration: String,
        val bom: ByteArray = byteArrayOf()
    )

    private fun File.replaceZipEntryName(from: String, to: String): File {
        val source = from.toByteArray(StandardCharsets.US_ASCII)
        val replacement = to.toByteArray(StandardCharsets.US_ASCII)
        require(source.size == replacement.size)
        val archive = readBytes()
        var replacements = 0
        for (start in 0..archive.size - source.size) {
            if (source.indices.all { offset -> archive[start + offset] == source[offset] }) {
                replacement.copyInto(archive, destinationOffset = start)
                replacements += 1
            }
        }
        require(replacements >= 2)
        writeBytes(archive)
        return this
    }

    private fun EpubPackageDocument.packageXmlManifestHref(): String {
        val parser = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder()
        val packageDocument = parser.parse(
            ByteArrayInputStream(packageXml.toByteArray(StandardCharsets.UTF_8))
        )
        return packageDocument.getElementsByTagNameNS("*", "item")
            .item(0)
            .attributes
            .getNamedItem("href")
            .nodeValue
    }

    private companion object {
        const val PACKAGE_MEDIA_TYPE = "application/oebps-package+xml"
    }
}
