package com.secondpasslibrary.reader.connection.discovery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class AndroidNsdLibraryUrlDiscoveryAdapterTest {
    @Test
    fun `browses SPL services and extracts exact TXT URL`() = runTest {
        val browser = FakeNsdBrowser()
        val emissions = mutableListOf<Set<String>>()
        val collection = collect(browser, emissions)

        assertEquals("_secondpass._tcp", browser.serviceType)
        browser.found("one")
        browser.resolved("one", mapOf("url" to URL.encodeToByteArray()))

        assertEquals(setOf(URL), emissions.last())
        collection.cancel()
    }

    @Test
    fun `missing and malformed TXT URLs are ignored`() = runTest {
        val browser = FakeNsdBrowser()
        val emissions = mutableListOf<Set<String>>()
        val collection = collect(browser, emissions)

        browser.found("missing")
        browser.resolved("missing", emptyMap())
        browser.found("malformed")
        browser.resolved("malformed", mapOf("url" to "not a URL".encodeToByteArray()))

        assertTrue(emissions.all(Set<String>::isEmpty))
        collection.cancel()
    }

    @Test
    fun `duplicate URL is retained until its final service disappears`() = runTest {
        val browser = FakeNsdBrowser()
        val emissions = mutableListOf<Set<String>>()
        val collection = collect(browser, emissions)
        for (key in listOf("one", "two")) {
            browser.found(key)
            browser.resolved(key, mapOf("url" to URL.encodeToByteArray()))
        }

        browser.lost("one")
        assertEquals(setOf(URL), emissions.last())
        browser.lost("two")
        assertTrue(emissions.last().isEmpty())
        collection.cancel()
    }

    @Test
    fun `loss and stop prevent stale resolved callbacks`() = runTest {
        val browser = FakeNsdBrowser()
        val emissions = mutableListOf<Set<String>>()
        val collection = collect(browser, emissions)
        browser.found("one")
        browser.lost("one")
        browser.resolved("one", mapOf("url" to URL.encodeToByteArray()))
        assertTrue(emissions.last().isEmpty())

        collection.cancel()
        runCurrent()
        val countAfterStop = emissions.size
        browser.found("late")
        browser.resolved("late", mapOf("url" to URL.encodeToByteArray()))

        assertTrue(browser.closed)
        assertEquals(countAfterStop, emissions.size)
    }

    private fun kotlinx.coroutines.test.TestScope.collect(
        browser: FakeNsdBrowser,
        emissions: MutableList<Set<String>>
    ): Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        AndroidNsdLibraryUrlDiscoveryAdapter(browser).candidateUrls().collect(emissions::add)
    }

    private companion object {
        const val URL = "https://secondpasslibrary.zcaprica.duckdns.org"
    }
}

private class FakeNsdBrowser : NsdBrowser {
    var serviceType: String? = null
    var closed = false
    private lateinit var listener: NsdBrowserListener

    override fun browse(serviceType: String, listener: NsdBrowserListener): AutoCloseable {
        this.serviceType = serviceType
        this.listener = listener
        return AutoCloseable { closed = true }
    }

    fun found(key: String) = listener.onServiceFound(key)

    fun resolved(key: String, attributes: Map<String, ByteArray>) =
        listener.onServiceResolved(key, attributes)

    fun lost(key: String) = listener.onServiceLost(key)
}
