package com.secondpasslibrary.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedLibraryHierarchyTest {
    @Test
    fun `authenticated client exposes one unambiguous Library hierarchy`() {
        val rootMethods = AuthenticatedSecondPassClient::class.java.methods.map { it.name }.toSet()
        val libraryMethods = AuthenticatedLibraryClient::class.java.methods.map { it.name }.toSet()

        assertTrue("Library parent is missing", "getLibrary" in rootMethods)
        assertFalse(
            "Axis operations must not remain duplicated on the authenticated root",
            rootMethods.any { it in FLAT_LIBRARY_METHODS }
        )
        assertTrue(
            libraryMethods.containsAll(setOf("getBooks", "getAuthors", "getSeries", "getGroups"))
        )
    }
}

private val FLAT_LIBRARY_METHODS =
    setOf(
        "listBooks",
        "searchLibrary",
        "listGroupBooks",
        "listAuthors",
        "getAuthor",
        "listGroupAuthors",
        "listSeries",
        "getSeries",
        "listGroupSeries",
        "listGroups"
    )
