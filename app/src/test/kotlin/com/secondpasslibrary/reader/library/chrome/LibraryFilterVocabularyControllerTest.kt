package com.secondpasslibrary.reader.library.chrome

import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedLibraryTagsClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.CatalogTagListOptions
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.FakeAuthenticatedLibraryClient
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.library.LibraryFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFilterVocabularyControllerTest {
    @Test
    fun `prepare loads complete group and global tag vocabulary`() = runTest {
        val client = FakeVocabularyClient().apply {
            groups = { options ->
                vocabularyPage(
                    options.page,
                    listOf(group("group-${options.page}")),
                    hasNext = options.page == 1
                )
            }
            tags = { _, options ->
                vocabularyPage(
                    options.page,
                    listOf(tag("tag-${options.page}")),
                    hasNext = options.page == 1
                )
            }
        }
        val controller = controller(client)

        controller.prepare(profile(), advancedGroupsEnabled = true, LibraryScope.Global)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), client.groupRequests.map { it.page })
        assertEquals(listOf("group-1", "group-2"), stateGroups(controller))
        assertEquals(listOf(1, 2), client.tagRequests.map { it.second.page })
        assertEquals(listOf("tag-1", "tag-2"), stateTags(controller))
    }

    @Test
    fun `disabled group capability never requests groups`() = runTest {
        val client = FakeVocabularyClient()
        val controller = controller(client)

        controller.prepare(profile(), advancedGroupsEnabled = false, LibraryScope.Global)
        advanceUntilIdle()

        assertTrue(client.groupRequests.isEmpty())
        assertFalse(controller.state.value.groupSelector.loading)
        assertTrue(controller.state.value.tagSelector.loaded)
    }

    @Test
    fun `scope change replaces tags with group vocabulary`() = runTest {
        val client = FakeVocabularyClient().apply {
            tags = { scope, options ->
                val slug = if (scope == LibraryScope.Global) "global" else "scoped"
                vocabularyPage(options.page, listOf(tag(slug)))
            }
        }
        val controller = controller(client)
        controller.prepare(profile(), advancedGroupsEnabled = false, LibraryScope.Global)
        advanceUntilIdle()

        controller.selectScope(LibraryScope.Group("group"))
        advanceUntilIdle()

        assertEquals(listOf("scoped"), stateTags(controller))
        assertEquals(
            listOf(LibraryScope.Global, LibraryScope.Group("group")),
            client.tagRequests.map { it.first }
        )
    }

    @Test
    fun `stale prior scope tag response cannot overwrite current scope`() = runTest {
        val globalStarted = CompletableDeferred<Unit>()
        val releaseGlobal = CompletableDeferred<Unit>()
        val client = FakeVocabularyClient().apply {
            tags = { scope, options ->
                if (scope == LibraryScope.Global) {
                    globalStarted.complete(Unit)
                    try {
                        releaseGlobal.await()
                    } catch (_: CancellationException) {
                        withContext(NonCancellable) { releaseGlobal.await() }
                    }
                    vocabularyPage(options.page, listOf(tag("stale")))
                } else {
                    vocabularyPage(options.page, listOf(tag("current")))
                }
            }
        }
        val controller = controller(client)
        controller.prepare(profile(), advancedGroupsEnabled = false, LibraryScope.Global)
        runCurrent()
        globalStarted.await()

        controller.selectScope(LibraryScope.Group("group"))
        runCurrent()
        assertEquals(listOf("current"), stateTags(controller))

        releaseGlobal.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("current"), stateTags(controller))
    }

    @Test
    fun `retry replaces vocabulary failure`() = runTest {
        var fail = true
        val client = FakeVocabularyClient().apply {
            tags = { _, options ->
                if (fail) throw SplClientException.ProtocolInvalid("tags")
                vocabularyPage(options.page, listOf(tag("recovered")))
            }
        }
        val controller = controller(client)
        controller.prepare(profile(), advancedGroupsEnabled = false, LibraryScope.Global)
        advanceUntilIdle()
        assertEquals(LibraryFailure.PROTOCOL_INVALID, controller.state.value.tagSelector.failure)

        fail = false
        controller.retryTags()
        advanceUntilIdle()

        assertEquals(listOf("recovered"), stateTags(controller))
        assertEquals(null, controller.state.value.tagSelector.failure)
    }

    @Test
    fun `authenticated connection change resets and reloads vocabulary`() = runTest {
        var response = "first"
        val client = FakeVocabularyClient().apply {
            tags = { _, options -> vocabularyPage(options.page, listOf(tag(response))) }
        }
        val controller = controller(client)
        controller.prepare(profile(), advancedGroupsEnabled = false, LibraryScope.Global)
        advanceUntilIdle()

        response = "second"
        controller.prepare(
            profile().copy(clientSessionId = "new-client-session"),
            advancedGroupsEnabled = false,
            LibraryScope.Global
        )
        advanceUntilIdle()

        assertEquals(listOf("second"), stateTags(controller))
        assertEquals(2, client.tagRequests.size)
    }

    @Test
    fun `vocabulary controller has no Books or Axis child dependency`() {
        val constructorTypes =
            LibraryFilterVocabularyController::class.java.declaredConstructors
                .flatMap { it.parameterTypes.toList() }

        assertFalse(constructorTypes.any { it.name.contains("library.books") })
        assertFalse(constructorTypes.any { it.name.contains("library.axis") })
    }

    private fun kotlinx.coroutines.test.TestScope.controller(client: FakeVocabularyClient) =
        LibraryFilterVocabularyController(FakeVocabularyClientProvider(client), this)

    private fun stateGroups(controller: LibraryFilterVocabularyController) =
        controller.state.value.groupSelector.groups.map { it.id }

    private fun stateTags(controller: LibraryFilterVocabularyController) =
        controller.state.value.tagSelector.tags.map { it.slug }
}

private class FakeVocabularyClient :
    AuthenticatedSecondPassClient,
    AuthenticatedLibraryGroupsClient,
    AuthenticatedLibraryTagsClient {
    override val library = FakeAuthenticatedLibraryClient(groups = this, tags = this)
    override val shelves = com.secondpasslibrary.reader.FakeAuthenticatedShelvesClient
    override val marginalia = com.secondpasslibrary.reader.FakeAuthenticatedMarginaliaClient

    val groupRequests = mutableListOf<LibraryGroupListOptions>()
    val tagRequests = mutableListOf<Pair<LibraryScope, CatalogTagListOptions>>()
    var groups: suspend (LibraryGroupListOptions) -> LibraryPage<LibraryGroupSummary> = {
        vocabularyPage(it.page, emptyList())
    }
    var tags: suspend (LibraryScope, CatalogTagListOptions) -> LibraryPage<LibraryCatalogTag> =
        { _, options -> vocabularyPage(options.page, emptyList()) }

    override suspend fun listGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> {
        groupRequests += options
        return groups(options)
    }

    override suspend fun list(
        scope: LibraryScope,
        options: CatalogTagListOptions
    ): LibraryPage<LibraryCatalogTag> {
        tagRequests += scope to options
        return tags(scope, options)
    }

    override suspend fun get(tagId: String): LibraryCatalogTag =
        error("Catalog tag detail is outside this vocabulary fixture.")
}

private class FakeVocabularyClientProvider(private val client: AuthenticatedSecondPassClient) :
    AuthenticatedClientProvider {
    override suspend fun forProfile(profile: ConnectionProfile) = client
}

private fun group(id: String) = LibraryGroupSummary(id, id, false)

private fun tag(slug: String) = LibraryCatalogTag(slug, slug, slug, 1)

private fun <T> vocabularyPage(page: Int, items: List<T>, hasNext: Boolean = false) =
    LibraryPage(items.size, items, hasNext, page > 1, page, 200)

private fun profile() = ConnectionProfile(
    serverOrigin = "https://library.example",
    serverBaseUrl = "https://library.example/",
    apiBaseUrl = "https://library.example/api/v1/",
    serverName = "Library",
    serverDescription = "",
    serverVersion = "1",
    serverReleaseDate = "",
    clientSessionId = "client-session",
    clientName = "Tablet",
    clientType = "second-pass-android-client"
)
