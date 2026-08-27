package com.secondpasslibrary.reader.reader.marginalia.preferences

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryPage
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerLoadState
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerRole
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerSummary
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerVisibility
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersController
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderMarginaliaLayerPolicyControllerTest {
    @Test
    fun `global default and fresh cache resolve startup visibility`() = runTest {
        val fixture = fixture(globalDefault = true, cached = mapOf("hidden" to false))
        discover(fixture, "shown", "hidden")

        assertLayer(fixture, "shown", ReaderMarginaliaLayerLoadState.LOADED, visible = true)
        assertLayer(fixture, "hidden", ReaderMarginaliaLayerLoadState.NOT_LOADED, visible = false)

        val globalOff = fixture(globalDefault = false, cached = mapOf("shown" to true))
        discover(globalOff, "shown", "new")
        assertLayer(globalOff, "shown", ReaderMarginaliaLayerLoadState.LOADED, visible = true)
        assertLayer(globalOff, "new", ReaderMarginaliaLayerLoadState.NOT_LOADED, visible = false)
    }

    @Test
    fun `newly appended Session inherits current global default`() = runTest {
        val pages = mutableMapOf(
            1 to ReaderMarginaliaLayerHistoryPage(listOf(previous("first")), 1, hasMore = true),
            2 to ReaderMarginaliaLayerHistoryPage(listOf(previous("new")), 2, hasMore = false)
        )
        val fixture = fixture(globalDefault = true, pages = pages)
        discover(fixture)
        fixture.layers.loadMore()
        advanceUntilIdle()

        assertLayer(fixture, "new", ReaderMarginaliaLayerLoadState.LOADED, visible = true)
    }

    @Test
    fun `automatic loading is bounded and one failure does not block siblings`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val fixture = fixture(
            globalDefault = true,
            annotationsLoader = ReaderAnnotationsLoader { _, sessionId ->
                active += 1
                maximum = maxOf(maximum, active)
                gate.await()
                active -= 1
                if (sessionId == "bad") error("broken")
                emptyList()
            }
        )
        fixture.beginDiscovery("one", "two", "bad", "three", "four")
        runCurrent()

        assertEquals(AUTO_LAYER_LOAD_CONCURRENCY, maximum)
        gate.complete(Unit)
        advanceUntilIdle()
        assertLayer(fixture, "bad", ReaderMarginaliaLayerLoadState.FAILED, visible = false)
        assertLayer(fixture, "four", ReaderMarginaliaLayerLoadState.LOADED, visible = true)
    }

    @Test
    fun `individual load remains separate while visibility choices persist`() = runTest {
        val fixture = fixture(globalDefault = false)
        discover(fixture, "layer")

        fixture.layers.loadLayer("layer")
        advanceUntilIdle()
        assertTrue(fixture.visibility.writes.isEmpty())
        assertLayer(fixture, "layer", ReaderMarginaliaLayerLoadState.LOADED, visible = false)

        fixture.policy.setVisible("layer", true)
        advanceUntilIdle()
        assertEquals("layer" to true, fixture.visibility.writes.last())
        fixture.policy.setVisible("layer", false)
        advanceUntilIdle()
        assertEquals("layer" to false, fixture.visibility.writes.last())
        assertLayer(fixture, "layer", ReaderMarginaliaLayerLoadState.LOADED, visible = false)
    }

    @Test
    fun `bulk visibility affects only previous layers and not global preference`() = runTest {
        val fixture = fixture(globalDefault = false)
        discover(fixture, "one", "two")

        fixture.policy.setAllVisible(true)
        advanceUntilIdle()
        assertTrue(fixture.layer("one").isVisible)
        assertTrue(fixture.layer("two").isVisible)
        fixture.policy.setAllVisible(false)
        advanceUntilIdle()

        assertFalse(fixture.layer("one").isVisible)
        assertFalse(fixture.layer("two").isVisible)
        assertTrue(fixture.layers.state.value.currentLayer != null)
        assertEquals(0, fixture.preferences.writeCount)
    }

    @Test
    fun `auto show setting changes future default without changing current visibility`() = runTest {
        val pages = mutableMapOf(
            1 to ReaderMarginaliaLayerHistoryPage(listOf(previous("existing")), 1, hasMore = true),
            2 to ReaderMarginaliaLayerHistoryPage(listOf(previous("later")), 2, hasMore = false)
        )
        val fixture = fixture(globalDefault = true, pages = pages)
        discover(fixture)
        assertTrue(fixture.layer("existing").isVisible)

        fixture.policy.setAutoShowPrevious(false)
        advanceUntilIdle()
        assertTrue(fixture.layer("existing").isVisible)
        assertEquals(listOf(false), fixture.preferences.writes)
        assertTrue(fixture.visibility.writes.isEmpty())

        fixture.layers.loadMore()
        advanceUntilIdle()
        assertLayer(fixture, "later", ReaderMarginaliaLayerLoadState.NOT_LOADED, visible = false)
    }

    @Test
    fun `authority loss leaves desired layer hidden without a server read`() = runTest {
        val fixture = fixture(globalDefault = true)
        fixture.policy.setAuthorityAvailable(false)
        discover(fixture, "layer")

        assertEquals(0, fixture.annotationRequests)
        assertLayer(fixture, "layer", ReaderMarginaliaLayerLoadState.NOT_LOADED, visible = false)
    }

    private fun TestScope.fixture(
        globalDefault: Boolean,
        cached: Map<String, Boolean> = emptyMap(),
        pages: Map<Int, ReaderMarginaliaLayerHistoryPage>? = null,
        annotationsLoader: ReaderAnnotationsLoader = ReaderAnnotationsLoader { _, _ -> emptyList() }
    ): Fixture {
        val preferences = FakePreferences(globalDefault)
        val visibility = FakeVisibility(cached.toMutableMap())
        val requested = mutableListOf<String>()
        val pageResults = pages?.toMutableMap() ?: mutableMapOf()
        val countedLoader = ReaderAnnotationsLoader { profile, sessionId ->
            requested += sessionId
            annotationsLoader.load(profile, sessionId)
        }
        val history = ReaderMarginaliaLayerHistoryLoader { _, _, page ->
            pageResults[page]
                ?: ReaderMarginaliaLayerHistoryPage(emptyList(), page, hasMore = false)
        }
        val layers = ReaderMarginaliaLayersController(history, countedLoader, this)
        val policyScope = CoroutineScope(coroutineContext + SupervisorJob())
        val policy = ReaderMarginaliaLayerPolicyController(
            preferences,
            visibility,
            layers,
            policyScope,
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
        return Fixture(layers, policy, preferences, visibility, requested, pageResults)
    }

    private suspend fun TestScope.discover(fixture: Fixture, vararg sessionIds: String) {
        fixture.beginDiscovery(*sessionIds)
        advanceUntilIdle()
    }

    private fun Fixture.beginDiscovery(vararg sessionIds: String) {
        if (sessionIds.isNotEmpty()) {
            pageResults[1] = ReaderMarginaliaLayerHistoryPage(
                sessionIds.map(::previous),
                page = 1,
                hasMore = false
            )
        }
        layers.select(PROFILE, BOOK_ID, CURRENT_SESSION)
        policy.select(PROFILE.authenticatedConnectionIdentity, BOOK_ID)
    }

    private fun assertLayer(
        fixture: Fixture,
        sessionId: String,
        loadState: ReaderMarginaliaLayerLoadState,
        visible: Boolean
    ) {
        val layer = fixture.layer(sessionId)
        assertEquals(loadState, layer.loadState)
        assertEquals(visible, layer.isVisible)
    }

    private fun Fixture.layer(sessionId: String) =
        layers.state.value.previousLayers.single { it.summary.sessionId == sessionId }

    private val ReaderPreviousMarginaliaLayer.isVisible
        get() = visibility == ReaderMarginaliaLayerVisibility.VISIBLE

    private data class Fixture(
        val layers: ReaderMarginaliaLayersController,
        val policy: ReaderMarginaliaLayerPolicyController,
        val preferences: FakePreferences,
        val visibility: FakeVisibility,
        val requests: MutableList<String>,
        val pageResults: MutableMap<Int, ReaderMarginaliaLayerHistoryPage>
    ) {
        val annotationRequests: Int get() = requests.size
    }

    private class FakePreferences(private val value: Boolean) :
        ReaderMarginaliaLayerPreferenceStore {
        var writeCount = 0
        val writes = mutableListOf<Boolean>()

        override suspend fun readAutoShowPrevious() = value

        override suspend fun writeAutoShowPrevious(enabled: Boolean) {
            writeCount += 1
            writes += enabled
        }
    }

    private class FakeVisibility(private val values: MutableMap<String, Boolean>) :
        ReaderMarginaliaLayerVisibilityStore {
        val writes = mutableListOf<Pair<String, Boolean>>()

        override suspend fun read(
            scope: ReaderMarginaliaVisibilityScope,
            sessionId: String,
            now: Instant
        ) = values[sessionId]

        override suspend fun write(
            scope: ReaderMarginaliaVisibilityScope,
            sessionId: String,
            visible: Boolean,
            touchedAt: Instant
        ) {
            values[sessionId] = visible
            writes += sessionId to visible
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-26T12:00:00Z")
        const val BOOK_ID = "book"
        val CURRENT_SESSION = ReaderSessionContext(
            "current",
            ReaderSessionStatus.ACTIVE,
            savedProgressCfi = null
        )
        val PROFILE = ConnectionProfile(
            serverOrigin = "https://library.example",
            serverBaseUrl = "https://library.example/",
            apiBaseUrl = "https://library.example/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "2026-08-26",
            clientSessionId = "account",
            clientName = "Reader",
            clientType = "reader"
        )

        fun previous(sessionId: String) = ReaderMarginaliaLayerSummary(
            sessionId,
            ReaderMarginaliaLayerRole.PREVIOUS,
            ReaderSessionStatus.CLOSED,
            sessionName = sessionId,
            startedAt = null,
            closedAt = null,
            lastActivityAt = null,
            annotationCount = 1
        )
    }
}
