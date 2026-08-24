package com.secondpasslibrary.reader.reader.cfi

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val ZIP_TIMESTAMP_MILLIS = 315_619_200_000L

internal object SyntheticEpubFixtureBuilder {
    fun create(directory: File, fileName: String = "second-pass-cfi-fixture.epub"): File {
        require('/' !in fileName && '\\' !in fileName) { "Fixture filename must be local." }
        check(directory.mkdirs() || directory.isDirectory) { "Could not create fixture directory." }

        val target = directory.resolve(fileName)
        try {
            ZipOutputStream(target.outputStream().buffered()).use { output ->
                output.writeStored("mimetype", SyntheticEpubCfiSources.MIMETYPE)
                output.writeDeflated("META-INF/container.xml", SyntheticEpubCfiSources.containerXml)
                output.writeDeflated(
                    SyntheticEpubCfiSources.PACKAGE_PATH,
                    SyntheticEpubCfiSources.packageDocument
                )
                output.writeDeflated(
                    SyntheticEpubCfiSources.CHAPTER_ONE_PATH,
                    SyntheticEpubCfiSources.chapterOneXhtml
                )
                output.writeDeflated(
                    SyntheticEpubCfiSources.CHAPTER_TWO_PATH,
                    SyntheticEpubCfiSources.chapterTwoXhtml
                )
                output.writeDeflated(
                    SyntheticEpubCfiSources.NAVIGATION_PATH,
                    SyntheticEpubCfiSources.navigationXhtml
                )
            }
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
        return target
    }
}

private fun ZipOutputStream.writeStored(name: String, content: String) {
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    val checksum = CRC32().apply { update(bytes) }
    val entry =
        ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = checksum.value
            time = ZIP_TIMESTAMP_MILLIS
        }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.writeDeflated(name: String, content: String) {
    putNextEntry(ZipEntry(name).apply { time = ZIP_TIMESTAMP_MILLIS })
    write(content.toByteArray(StandardCharsets.UTF_8))
    closeEntry()
}
