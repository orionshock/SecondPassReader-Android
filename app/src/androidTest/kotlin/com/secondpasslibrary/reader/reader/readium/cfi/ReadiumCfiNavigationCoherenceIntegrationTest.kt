package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
internal class ReadiumCfiNavigationCoherenceIntegrationTest : ReadiumEpubCfiNavigatorTestSupport() {
    @Test
    fun rapidNavigationKeepsNewestCfiAsFinalDestination() = withFixture(
        "latest-navigation.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val originalPosition = runBlocking {
                host.engine.cfiNavigator.currentPosition().requireSuccess()
            }

            runBlocking {
                val first = async {
                    host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                }
                val second = async { host.engine.cfiNavigator.goTo(originalPosition) }
                runCatching { first.await() }
                second.await().requireSuccess()
            }

            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
        }
    }

    @Test
    fun positionCaptureDuringCrossResourceArrivalDoesNotCancelNavigation() = withFixture(
        "navigation-priority.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()

            runBlocking {
                val navigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                }
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    host.navigator.currentLocator.first { locator ->
                        normalizeEpubHref(locator.href.toString()) ==
                            SyntheticEpubCfiSources.CHAPTER_TWO_PATH
                    }
                }
                assertFalse(navigation.isCompleted)
                val positionCapture = async {
                    host.engine.cfiNavigator.currentPositionWithContext()
                }

                navigation.await().requireSuccess()
                positionCapture.await().requireSuccess()
            }

            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
        }
    }

    @Test
    fun positionCaptureWaitsForInFlightTocNavigation() = withFixture(
        "toc-read-priority.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val chapterTwo = requireNotNull(
                host.engine.tableOfContents.entries.single { it.title == "Chapter Two" }.target
            )

            runBlocking {
                val tocNavigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.tableOfContents.goTo(chapterTwo)
                }
                assertFalse(tocNavigation.isCompleted)
                val positionCapture = async {
                    host.engine.cfiNavigator.currentPositionWithContext()
                }

                assertEquals(
                    ReaderPublicationNavigationResult.NAVIGATED,
                    tocNavigation.await()
                )
                positionCapture.await().requireSuccess()
            }

            awaitPublicationResource(host.engine, SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
        }
    }

    @Test
    fun cfiNavigationSupersedesInFlightTocNavigation() = withFixture(
        "cfi-supersedes-toc.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val originalPosition = runBlocking {
                host.engine.cfiNavigator.currentPosition().requireSuccess()
            }
            val chapterTwo = requireNotNull(
                host.engine.tableOfContents.entries.single { it.title == "Chapter Two" }.target
            )

            runBlocking {
                val tocNavigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.tableOfContents.goTo(chapterTwo)
                }
                assertFalse(tocNavigation.isCompleted)
                val cfiNavigation = async {
                    host.engine.cfiNavigator.goTo(originalPosition)
                }

                runCatching { tocNavigation.await() }
                cfiNavigation.await().requireSuccess()
            }

            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
        }
    }

    @Test
    fun tocNavigationSupersedesInFlightCfiNavigation() = withFixture(
        "toc-supersedes-cfi.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val chapterOne = requireNotNull(
                host.engine.tableOfContents.entries.single { it.title == "Chapter One" }.target
            )

            runBlocking {
                val cfiNavigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                }
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    host.navigator.currentLocator.first { locator ->
                        normalizeEpubHref(locator.href.toString()) ==
                            SyntheticEpubCfiSources.CHAPTER_TWO_PATH
                    }
                }
                assertFalse(cfiNavigation.isCompleted)
                val tocNavigation = async {
                    host.engine.tableOfContents.goTo(chapterOne)
                }

                runCatching { cfiNavigation.await() }
                assertEquals(
                    ReaderPublicationNavigationResult.NAVIGATED,
                    tocNavigation.await()
                )
            }

            awaitPublicationResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
        }
    }

    @Test
    fun resourceTransitionDuringBoundCaptureIsRejected() = withFixture(
        "resource-coherence.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val testBinding = ReadiumCfiNavigatorBinding(
                ReadiumCfiJavascriptRuntime(targetContext)
            )
            scenario.onActivity { testBinding.bind(host.navigator) }
            runBlocking {
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    testBinding.readiness.first { it == EpubCfiReadiness.Available }
                }
            }

            val capture = runBlocking {
                testBinding.withNavigator { navigator, _ ->
                    val before = testBinding.resourceIdentity(navigator)
                    val moved = navigator.go(
                        Link(
                            href = requireNotNull(
                                Url(SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
                            ),
                            mediaType = requireNotNull(MediaType("application/xhtml+xml"))
                        ),
                        animated = false
                    )
                    assertTrue(moved)
                    withTimeout(HOST_TIMEOUT_MILLIS) {
                        navigator.currentLocator.first { locator ->
                            normalizeEpubHref(locator.href.toString()) ==
                                SyntheticEpubCfiSources.CHAPTER_TWO_PATH
                        }
                    }
                    val coherent = coherentResourceCapture(
                        before,
                        testBinding.resourceIdentity(navigator),
                        "captured content"
                    )
                    assertEquals(
                        EpubCfiReadiness.PreparingDocument,
                        testBinding.readiness.value
                    )
                    coherent
                }
            }

            assertEquals(ReadiumCfiResourceCapture.Changed, capture)
            assertEquals(
                EpubCfiReadiness.Available,
                runBlocking {
                    withTimeout(HOST_TIMEOUT_MILLIS) {
                        testBinding.readiness.first { it == EpubCfiReadiness.Available }
                    }
                }
            )
            testBinding.close()
        }
    }
}
