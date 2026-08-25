package com.secondpasslibrary.reader.reader.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

@RunWith(AndroidJUnit4::class)
class ReadiumReaderTableOfContentsTest {
    @Test
    fun mapsNestedInternalLinksAndRejectsExternalTargets() {
        val chapterOne = link("text/chapter-1.xhtml", "Chapter One")
        val chapterTwo = link("text/chapter-2.xhtml", "Chapter Two")
        val toc = ReadiumReaderTableOfContents(
            links = listOf(
                link(
                    href = "text/chapter-1.xhtml#part",
                    title = "Part One",
                    children = listOf(
                        link("text/chapter-2.xhtml#section", "Chapter Two"),
                        link("https://example.com/help", "External")
                    )
                )
            ),
            readingOrder = listOf(chapterOne, chapterTwo),
            binding = ReadiumPublicationNavigatorBinding()
        )

        val parent = toc.entries.single()
        assertEquals("Part One", parent.title)
        assertEquals("text/chapter-1.xhtml#part", parent.target?.reference)
        assertEquals("Chapter Two", parent.children[0].title)
        assertEquals("text/chapter-2.xhtml#section", parent.children[0].target?.reference)
        assertEquals("External", parent.children[1].title)
        assertNull(parent.children[1].target)
        assertTrue(parent.children[0].children.isEmpty())
    }

    private fun link(href: String, title: String, children: List<Link> = emptyList()) = Link(
        href = requireNotNull(Url(href)),
        title = title,
        children = children
    )
}
