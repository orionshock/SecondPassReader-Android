package com.secondpasslibrary.reader.library.chrome

import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.reader.library.LibraryFailure

internal data class LibraryGroupSelectorState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val groups: List<LibraryGroupSummary> = emptyList(),
    val failure: LibraryFailure? = null
)

internal data class LibraryTagSelectorState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val tags: List<LibraryCatalogTag> = emptyList(),
    val failure: LibraryFailure? = null
)

internal data class LibraryFilterVocabularyState(
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState(),
    val tagSelector: LibraryTagSelectorState = LibraryTagSelectorState()
)
