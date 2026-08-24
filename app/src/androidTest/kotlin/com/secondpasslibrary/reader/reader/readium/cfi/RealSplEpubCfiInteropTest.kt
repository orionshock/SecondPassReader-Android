package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetRequest
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import dagger.hilt.android.EntryPointAccessors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

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
            verifyGeneratedPointRoundTrip(engine)
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
                val range = client.marginalia.sessions.listAnnotations(session.session.id)
                    .filterIsInstance<MarginaliaAnnotation.Highlight>()
                    .firstOrNull {
                        it.body.text.isNotBlank() && it.location.cfi.looksLikeRangeCfi()
                    }
                if (range != null) {
                    val candidate = RealRangeCandidate(session, range)
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

private suspend fun verifyGeneratedPointRoundTrip(engine: ReaderEngine) {
    val generated = engine.cfiNavigator.currentPosition().requireSuccess("capture visible point")
    val resolution = engine.cfiNavigator.resolve(generated).requireSuccess("resolve visible point")
    assertEquals(EpubCfiTargetKind.POINT, resolution.kind)
    engine.cfiNavigator.goTo(generated).requireSuccess("navigate to visible point")
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
        progressCfi = progress?.cfi,
        snapshot = ReadOnlySessionSnapshot(
            sessionUpdatedAt = detail.session.summary.updatedAt,
            progressUpdatedAt = progress?.updatedAt,
            annotations = annotations.map { AnnotationVersion(it.id, it.updatedAt) }
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
    val sessionUpdatedAt: String,
    val progressUpdatedAt: String?,
    val annotations: List<AnnotationVersion>
)

private data class ReadOnlySessionCapture(
    val progressCfi: String?,
    val snapshot: ReadOnlySessionSnapshot
)

private data class AnnotationVersion(val id: String, val updatedAt: String)
