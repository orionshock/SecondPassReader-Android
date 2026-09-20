package com.secondpasslibrary.reader.reader.readium.cfi

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumPublicationInteractionIntegrationTest : ReadiumEpubCfiNavigatorTestSupport() {
    @Test
    fun bodyTapAndEdgePagingUseOneActionPerGesture() = withFixture(
        "publication-input.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val view = host.navigator.publicationView
            runBlocking {
                host.engine.cfiNavigator.goTo(
                    EpubCfi(CROSS_SPINE_POINT_CFI)
                ).requireSuccess()
            }
            val initial = awaitStatus(host.engine.hudEvents.readingStatus, "initial page") {
                it.pagesRemaining > 2
            }
            val tapCount = AtomicInteger()
            val tapScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            tapScope.launch(start = CoroutineStart.UNDISPATCHED) {
                host.engine.hudEvents.publicationTaps().collect { tapCount.incrementAndGet() }
            }
            try {
                runBlocking {
                    val bodyTap = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(HOST_TIMEOUT_MILLIS) {
                            host.engine.hudEvents.publicationTaps().first()
                        }
                    }
                    view.tapAt(0.5f)
                    try {
                        bodyTap.await()
                    } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
                        throw AssertionError("Center touch did not produce a body tap", failure)
                    }
                }
                assertEquals(initial, host.engine.hudEvents.readingStatus.value)

                view.tapAt(0.98f)
                awaitStatus(host.engine.hudEvents.readingStatus, "right edge tap") {
                    it.pagesRemaining == initial.pagesRemaining - 1
                }
                view.tapAt(0.02f)
                awaitStatus(host.engine.hudEvents.readingStatus, "left edge tap") {
                    it.pagesRemaining == initial.pagesRemaining
                }

                view.dragFrom(0.98f, dxDp = -100f, dyDp = 0f)
                awaitStatus(host.engine.hudEvents.readingStatus, "right edge swipe") {
                    it.pagesRemaining == initial.pagesRemaining - 1
                }
                assertEquals(
                    initial.pagesRemaining - 1,
                    host.engine.hudEvents.readingStatus.value?.pagesRemaining
                )
                assertEquals(1, tapCount.get())
            } finally {
                tapScope.cancel()
            }
        }
    }

    @Test
    fun verticalDragAtEdgeDoesNotTurnAPage() = withFixture(
        "vertical-input.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            runBlocking {
                host.engine.cfiNavigator.goTo(
                    EpubCfi(CROSS_SPINE_POINT_CFI)
                ).requireSuccess()
            }
            val initial = awaitStatus(host.engine.hudEvents.readingStatus, "initial page") {
                it.pagesRemaining > 2
            }
            host.navigator.publicationView.dragFrom(0.98f, dxDp = -8f, dyDp = 100f)
            runBlocking {
                withContext(Dispatchers.Main) {
                    host.navigator.evaluateJavascript("true")
                }
            }
            assertEquals(initial, host.engine.hudEvents.readingStatus.value)
        }
    }

    @Test
    fun activeSelectionSuppressesEdgePaging() = withFixture(
        "selection-input.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            runBlocking {
                host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI)).requireSuccess()
                awaitSelectionObserver(host.navigator)
                selectRestoreMarker(host.navigator, 1)
                check(awaitCurrentSelection(host.engine) != null)
            }
            val initial = awaitStatus(host.engine.hudEvents.readingStatus, "initial page") {
                it.pagesRemaining > 2
            }
            host.engine.hudEvents.setInteractionSuppressed(true)
            host.navigator.publicationView.dragFrom(0.98f, dxDp = -100f, dyDp = 0f)
            runBlocking {
                withContext(Dispatchers.Main) {
                    host.navigator.evaluateJavascript("true")
                }
            }
            assertEquals(initial, host.engine.hudEvents.readingStatus.value)
        }
    }
}

private fun awaitStatus(
    status: kotlinx.coroutines.flow.StateFlow<ReaderReadingStatus?>,
    stage: String,
    predicate: (ReaderReadingStatus) -> Boolean
): ReaderReadingStatus = runBlocking {
    try {
        withTimeout(HOST_TIMEOUT_MILLIS) {
            status.first { it != null && predicate(it) }!!
        }
    } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError("$stage timed out; current status=${status.value}", failure)
    }
}

private fun View.tapAt(horizontalFraction: Float) {
    val point = screenPoint(horizontalFraction)
    sendTap(point.first, point.second)
}

private fun sendTap(x: Float, y: Float) {
    val time = SystemClock.uptimeMillis()
    sendTouch(time, time, MotionEvent.ACTION_DOWN, x, y)
    sendTouch(time, time + 16, MotionEvent.ACTION_UP, x, y)
}

private fun View.dragFrom(horizontalFraction: Float, dxDp: Float, dyDp: Float) {
    val point = screenPoint(horizontalFraction)
    val density = resources.displayMetrics.density
    val dx = density * dxDp
    val dy = density * dyDp
    val time = SystemClock.uptimeMillis()
    sendTouch(time, time, MotionEvent.ACTION_DOWN, point.first, point.second)
    (1..6).forEach { step ->
        sendTouch(
            time,
            time + step * 16L,
            MotionEvent.ACTION_MOVE,
            point.first + dx * step / 6f,
            point.second + dy * step / 6f
        )
    }
    sendTouch(time, time + 112L, MotionEvent.ACTION_UP, point.first + dx, point.second + dy)
}

private fun View.screenPoint(horizontalFraction: Float): Pair<Float, Float> {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return (location[0] + width * horizontalFraction) to (location[1] + height / 2f)
}

private fun sendTouch(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
    val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    try {
        InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
    } finally {
        event.recycle()
    }
}
