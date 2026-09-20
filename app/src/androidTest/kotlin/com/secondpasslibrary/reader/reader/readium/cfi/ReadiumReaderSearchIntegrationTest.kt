package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumReaderSearchIntegrationTest : ReadiumEpubCfiNavigatorTestSupport() {
    @Test
    fun searchFindsBothSpineItemsAndNavigatesToExactResult() = withFixture(
        "reader-search.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val engine = scenario.awaitReadyHost().engine
            val results = runBlocking {
                engine.search.search("repeated phrase").toList().flatten()
            }
            assertTrue(results.size >= 3)
            assertTrue(results.all { it.match.isNotBlank() })
            assertTrue(results.any { it.before.isNotBlank() || it.after.isNotBlank() })
            assertTrue(runBlocking { engine.search.goTo(results.first().target) })
            awaitPublicationResource(engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
            val secondChapter = results.last()
            assertTrue(runBlocking { engine.search.goTo(secondChapter.target) })
            awaitPublicationResource(
                engine,
                SyntheticEpubCfiSources.CHAPTER_TWO_PATH
            )
        }
    }
}
