package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionSummary
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetRequest
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.cfi.ZipEpubPackageResolver
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

private const val REAL_SPL_CFI_ARGUMENT = "reader.realSplCfiInterop"
private const val INTEROP_PAGE_SIZE = 50
private const val MAX_INTEROP_PAGES = 100
private const val EXPECTED_TEXT_LIMIT = 48

/**
 * Read-only proof against the explicitly opted-in paired development account.
 *
 * Enable with `-Pandroid.testInstrumentationRunnerArguments.reader.realSplCfiInterop=true`.
 * The test deliberately calls no Reading Session or annotation mutation capability.
 */
@RunWith(AndroidJUnit4::class)
class RealSplEpubCfiInteropTest {
    @Test
    fun storedRangeAndOptionalProgressRoundTripWithoutServerWrites() = runBlocking {
        assumeTrue("Real SPL CFI proof was not requested.", isRealInteropEnabled())
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val owners = targetContext.realSplInteropOwners()
        val profile = owners.connectionProfileStore().read()
        val account = owners.persistedAccountContextStore().read()
        assumeNotNull(profile, account)
        requireNotNull(profile)
        requireNotNull(account)
        assumeTrue(
            "The paired account descriptor does not match the connection.",
            account.matches(profile)
        )

        val client = owners.authenticatedClientProvider().forProfile(profile)
        val subject = findReadableRangeSubject(
            client = client,
            owners = owners,
            profile = profile,
            profileId = account.profileId
        )
        assumeNotNull(subject)
        requireNotNull(subject)
        val sessionId = subject.candidate.session.session.id

        val intent = Intent(targetContext, ReadiumCfiTestActivity::class.java).apply {
            putExtra(ReadiumCfiTestActivity.EXTRA_EPUB_PATH, subject.book.file.absolutePath)
        }
        ActivityScenario.launch<ReadiumCfiTestActivity>(intent).use { scenario ->
            val engine = scenario.awaitReadyEngine()
            verifyStoredRange(engine, subject.candidate)
            val initialPoint = capturePoint(engine)
            val initialResolution = engine.cfiNavigator.resolve(initialPoint)
                .requireSuccess("resolve initial point")
            val generatedSelection = captureRealSelection(scenario, engine)
            val detourCfi = captureCrossSpinePoint(
                scenario = scenario,
                engine = engine,
                epubFile = subject.book.file,
                currentResourceHref = initialResolution.resourceHref
            )

            verifyGeneratedRoundTrips(
                engine = engine,
                initialPoint = initialPoint,
                initialResourceHref = initialResolution.resourceHref,
                detourCfi = detourCfi,
                selection = generatedSelection
            )

            scenario.recreate()
            val recreatedEngine = scenario.awaitReadyEngine()
            assertSame(engine, recreatedEngine)
            verifyGeneratedRoundTrips(
                engine = recreatedEngine,
                initialPoint = initialPoint,
                initialResourceHref = initialResolution.resourceHref,
                detourCfi = detourCfi,
                selection = generatedSelection
            )
            subject.progressCfi?.let { verifyStoredProgress(engine, it) }
        }

        assertEquals(
            "Read-only CFI proof changed the server-side Session snapshot.",
            subject.before,
            captureSession(client, sessionId).snapshot
        )
    }
}

private fun isRealInteropEnabled(): Boolean =
    InstrumentationRegistry.getArguments().getString(REAL_SPL_CFI_ARGUMENT).toBoolean()

private fun Context.realSplInteropOwners(): RealSplCfiInteropEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, RealSplCfiInteropEntryPoint::class.java)

private suspend fun findReadableRangeSubject(
    client: AuthenticatedSecondPassClient,
    owners: RealSplCfiInteropEntryPoint,
    profile: ConnectionProfile,
    profileId: String
): RealInteropSubject? {
    var pageNumber = 1
    val visitedSessions = mutableSetOf<String>()
    do {
        val page = client.marginalia.sessions.list(
            ReadingSessionListOptions(
                hasAnnotations = true,
                page = pageNumber,
                pageSize = INTEROP_PAGE_SIZE
            )
        )
        val newSessions = page.results.filter { visitedSessions.add(it.session.id) }
        if (newSessions.isEmpty()) return null
        newSessions.asSequence()
            .filter { it.book.canOpen }
            .forEach { session ->
                val annotations = client.marginalia.sessions.listAnnotations(session.session.id)
                val range = annotations
                    .filterIsInstance<MarginaliaAnnotation.Highlight>()
                    .firstOrNull {
                        it.body.text.isNotBlank() && it.location.cfi.looksLikeRangeCfi()
                    }
                if (range != null) {
                    val candidate = RealRangeCandidate(
                        session = session,
                        annotation = range
                    )
                    val before = captureSession(client, session.session.id)
                    val book = resolveReaderBook(owners, profile, profileId, candidate)
                    if (book != null) {
                        return RealInteropSubject(
                            candidate = candidate,
                            book = book,
                            progressCfi = before.progressCfi,
                            before = before.snapshot
                        )
                    }
                }
            }
        if (!page.hasNext) return null
        pageNumber += 1
    } while (pageNumber <= MAX_INTEROP_PAGES)
    return null
}

private suspend fun resolveReaderBook(
    owners: RealSplCfiInteropEntryPoint,
    profile: ConnectionProfile,
    profileId: String,
    candidate: RealRangeCandidate
): ResolvedReaderBook? = try {
    owners.readerBookAssetResolver().resolve(
        ReaderBookAssetRequest(profile, profileId, candidate.session.book.id),
        onDownloadStarted = {}
    )
} catch (_: ReaderEpubUnavailableException) {
    null
}

private suspend fun ActivityScenario<ReadiumCfiTestActivity>.awaitReadyEngine(): ReaderEngine {
    lateinit var activity: ReadiumCfiTestActivity
    onActivity { activity = it }
    val engine = withTimeout(45.seconds) {
        activity.hostState.first { it !is ReadiumCfiTestHostState.Loading }
    }
    check(engine is ReadiumCfiTestHostState.Ready) { "The real EPUB could not be opened." }
    withTimeout(20.seconds) {
        activity.navigatorGeneration.first { it > 0 }
    }
    return engine.engine
}

private suspend fun verifyStoredRange(engine: ReaderEngine, candidate: RealRangeCandidate) {
    val cfi = EpubCfi(candidate.annotation.location.cfi)
    engine.cfiNavigator.goTo(cfi).requireSuccess("navigate to stored range")
    val resolution = engine.cfiNavigator.resolve(cfi).requireSuccess("resolve stored range")
    assertEquals(EpubCfiTargetKind.RANGE, resolution.kind)
    assertSemanticTextMatches(candidate.annotation.body.text, resolution)
}

private suspend fun capturePoint(engine: ReaderEngine): EpubCfi =
    engine.cfiNavigator.currentPosition().requireSuccess("capture visible point")

private suspend fun captureCrossSpinePoint(
    scenario: ActivityScenario<ReadiumCfiTestActivity>,
    engine: ReaderEngine,
    epubFile: File,
    currentResourceHref: String
): EpubCfi {
    val packageDocument = withContext(Dispatchers.IO) {
        ZipEpubPackageResolver().resolve(epubFile)
    }
    lateinit var activity: ReadiumCfiTestActivity
    scenario.onActivity { activity = it }
    val navigator = requireNotNull(activity.currentNavigator())
    packageDocument.spine
        .asSequence()
        .filter { it.layout == EpubLayout.REFLOWABLE }
        .filter { it.resourceHref != currentResourceHref }
        .forEach { item ->
            val link = Link(
                href = requireNotNull(Url(item.resourceHref)),
                mediaType = requireNotNull(MediaType(item.mediaType))
            )
            val moved = withContext(Dispatchers.Main) { navigator.go(link) }
            if (!moved) return@forEach
            val point = withTimeoutOrNull(10.seconds) {
                var target: EpubCfi? = null
                while (target == null) {
                    delay(100)
                    val captured = engine.cfiNavigator.currentPosition()
                    if (captured is EpubCfiOutcome.Success) {
                        val resolved = engine.cfiNavigator.resolve(captured.value)
                        if (resolved is EpubCfiOutcome.Success &&
                            resolved.value.resourceHref == item.resourceHref
                        ) {
                            target = captured.value
                        }
                    }
                }
                target
            }
            if (point != null) return point
        }
    error("The real EPUB has no capturable cross-spine CFI verification target.")
}

private suspend fun captureRealSelection(
    scenario: ActivityScenario<ReadiumCfiTestActivity>,
    engine: ReaderEngine
): EpubCfiSelection {
    lateinit var activity: ReadiumCfiTestActivity
    scenario.onActivity { activity = it }
    withContext(Dispatchers.Main) {
        requireNotNull(activity.currentNavigator()).evaluateJavascript(REAL_PHRASE_SELECTION_SCRIPT)
    }
    return requireNotNull(
        engine.cfiNavigator.currentSelection().requireSuccess("capture real selection")
    )
}

private suspend fun verifyGeneratedRoundTrips(
    engine: ReaderEngine,
    initialPoint: EpubCfi,
    initialResourceHref: String,
    detourCfi: EpubCfi,
    selection: EpubCfiSelection
) {
    engine.cfiNavigator.goTo(detourCfi).requireSuccess("navigate away from visible point")
    engine.cfiNavigator.goTo(initialPoint).requireSuccess("restore visible point")
    val pointResolution = engine.cfiNavigator.resolve(initialPoint)
        .requireSuccess("resolve restored visible point")
    assertEquals(EpubCfiTargetKind.POINT, pointResolution.kind)
    assertEquals(initialResourceHref, pointResolution.resourceHref)

    engine.cfiNavigator.goTo(detourCfi).requireSuccess("navigate away from selection")
    engine.cfiNavigator.goTo(selection.cfi).requireSuccess("restore real selection")
    val selectionResolution = engine.cfiNavigator.resolve(selection.cfi)
        .requireSuccess("resolve real selection")
    assertEquals(EpubCfiTargetKind.RANGE, selectionResolution.kind)
    assertEquals(selection.selectedText, selectionResolution.selectedText)
    assertEquals(selection.prefix, selectionResolution.prefix)
    assertEquals(selection.suffix, selectionResolution.suffix)
}

private suspend fun verifyStoredProgress(engine: ReaderEngine, progressCfi: String) {
    val cfi = EpubCfi(progressCfi)
    engine.cfiNavigator.goTo(cfi).requireSuccess("navigate to stored progress")
    val resolution = engine.cfiNavigator.resolve(cfi).requireSuccess("resolve stored progress")
    assertEquals(EpubCfiTargetKind.POINT, resolution.kind)
}

private fun assertSemanticTextMatches(expected: String, resolution: EpubCfiResolution) {
    val expectedSnippet = expected.normalizedText().codePointPrefix(EXPECTED_TEXT_LIMIT)
    val actual = resolution.selectedText.orEmpty().normalizedText()
    assertTrue(
        "Resolved range did not preserve the expected semantic text.",
        expectedSnippet.isNotEmpty() && actual.contains(expectedSnippet)
    )
}

private suspend fun captureSession(
    client: AuthenticatedSecondPassClient,
    sessionId: String
): ReadOnlySessionCapture {
    val detail = client.marginalia.sessions.get(sessionId)
    val progress = client.marginalia.sessions.getProgress(sessionId)
    val annotations = client.marginalia.sessions.listAnnotations(sessionId)
    return ReadOnlySessionCapture(
        snapshot = ReadOnlySessionSnapshot(
            session = detail.session.summary,
            progress = progress,
            annotations = annotations
        )
    )
}

private fun String.normalizedText(): String = trim().replace(Regex("\\s+"), " ")

private fun String.codePointPrefix(maxCodePoints: Int): String {
    val codePoints = codePointCount(0, length)
    return substring(0, offsetByCodePoints(0, minOf(maxCodePoints, codePoints)))
}

private fun String.looksLikeRangeCfi(): Boolean {
    var assertionDepth = 0
    var escaped = false
    var separators = 0
    forEach { character ->
        when {
            escaped -> escaped = false
            character == '^' -> escaped = true
            character == '[' -> assertionDepth += 1
            character == ']' && assertionDepth > 0 -> assertionDepth -= 1
            character == ',' && assertionDepth == 0 -> separators += 1
        }
    }
    return separators >= 2
}

private fun <T> EpubCfiOutcome<T>.requireSuccess(operation: String): T = when (this) {
    is EpubCfiOutcome.Success -> value
    is EpubCfiOutcome.Failure -> error("Could not $operation: ${reason.name}")
}

private data class RealRangeCandidate(
    val session: ReadingSessionListItem,
    val annotation: MarginaliaAnnotation.Highlight
)

private data class RealInteropSubject(
    val candidate: RealRangeCandidate,
    val book: ResolvedReaderBook,
    val progressCfi: String?,
    val before: ReadOnlySessionSnapshot
)

private data class ReadOnlySessionSnapshot(
    val session: ReadingSessionSummary,
    val progress: ReadingProgress?,
    val annotations: List<MarginaliaAnnotation>
)

private data class ReadOnlySessionCapture(val snapshot: ReadOnlySessionSnapshot) {
    val progressCfi: String?
        get() = snapshot.progress?.cfi
}

private val REAL_PHRASE_SELECTION_SCRIPT =
    """
    (() => {
      const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      let node;
      while ((node = walker.nextNode())) {
        const parent = node.parentElement;
        if (!parent || parent.closest("script, style")) continue;
        const text = node.nodeValue || "";
        const start = text.search(/\S/);
        if (start < 0) continue;
        let end = Math.min(text.length, start + 32);
        if (end < text.length && /[\uD800-\uDBFF]/.test(text.charAt(end - 1))) end -= 1;
        while (end > start && /\s/.test(text.charAt(end - 1))) end -= 1;
        if (end - start < 8) continue;
        const range = document.createRange();
        range.setStart(node, start);
        range.setEnd(node, end);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        return selection.toString();
      }
      return null;
    })();
    """.trimIndent()
