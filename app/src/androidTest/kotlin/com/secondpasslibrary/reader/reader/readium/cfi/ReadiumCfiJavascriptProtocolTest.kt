package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumCfiJavascriptProtocolTest : ReadiumCfiJavascriptRuntimeTestSupport() {
    @Test
    fun mapsMissingPackageItemrefIdAsNull() {
        val target = readPackageTarget(
            JSONObject(
                """{"spineIndex":28,"itemrefId":null,"idref":"id97","kind":"range"}"""
            )
        )

        assertNull(target.itemrefId)
    }

    @Test
    fun installsPinnedBundleAndRuntimeIdempotently() = withHarness { harness ->
        assertEquals(
            "function",
            harness.evaluate("typeof SecondPassColibrio.EpubCfiParser.parse").jsonString()
        )
        assertEquals(
            CfiProtocol.RUNTIME_VERSION,
            harness.evaluate("__secondPassEpubCfi.runtimeVersion()").jsonString()
        )

        harness.evaluate(asset("reader/cfi/secondpass-epub-cfi-runtime.js"))

        assertEquals(
            CfiProtocol.RUNTIME_VERSION,
            harness.evaluate("__secondPassEpubCfi.runtimeVersion()").jsonString()
        )
    }

    @Test
    fun documentReadinessRequiresReadiumPublicationRuntime() = withHarness { harness ->
        assertTrue(successBoolean(harness.runtime("isDocumentReady")))

        harness.evaluate("delete window.readium")

        assertFalse(successBoolean(harness.runtime("isDocumentReady")))

        harness.evaluate("window.readium = { isReflowable: false, isFixedLayout: true }")

        assertFailure(harness.runtime("isDocumentReady"), "UNSUPPORTED_FIXED_LAYOUT")
    }

    @Test
    fun reportsMalformedAndUnsupportedCfisAsBoundedFailures() = withHarness { harness ->
        assertFailure(harness.runtime("parse", "not-a-cfi"), "INVALID_CFI")

        val unsupported = "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root])"
        assertFailure(
            harness.runtime(
                "resolvePackage",
                unsupported,
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )

        val sideBiased =
            "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root]/4" +
                "[repeated-phrase]/1:4[;s=b])"
        assertFailure(
            harness.runtime(
                "resolvePackage",
                sideBiased,
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )
    }

    @Test
    fun rejectsTerminalPointWithoutTruthfulMovementAnchor() = withHarness { harness ->
        val packageCfi = harness.packageCfi()
        val terminalContent =
            harness.generateContentCfi(
                """
                const node = document.querySelector("#post-lookalike-target").firstChild;
                builder.appendTerminalDomPosition(node, node.length);
                """.trimIndent()
            )

        assertFailure(
            harness.runtime(
                "resolveContent",
                harness.compose(packageCfi, terminalContent),
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH,
                0,
                "chapter-one",
                "spine-chapter-one",
                SyntheticEpubCfiSources.CHAPTER_ONE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )
    }
}
