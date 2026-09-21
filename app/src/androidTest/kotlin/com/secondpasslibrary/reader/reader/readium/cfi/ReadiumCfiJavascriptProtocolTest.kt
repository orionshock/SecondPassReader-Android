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
        assertFailure(
            harness.runtime("parse", "epubcfi(/6/2[unterminated!/4/2/1:0)"),
            "INVALID_CFI"
        )

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
    fun durableProfileAcceptsStructuralIdsAndRejectsTextAssertions() = withHarness { harness ->
        val historicalWebCfi =
            "epubcfi(/6/34!/4[x9780451492128_EPUB-15]/2,/310/1:0,/314/1:17)"
        val historicalTarget = successObject(
            harness.runtime(
                "resolvePackage",
                historicalWebCfi,
                historicalPackageDocument(),
                "/OPS/package.opf"
            )
        )
        assertEquals(16, historicalTarget.getInt("spineIndex"))
        assertEquals("range", historicalTarget.getString("kind"))

        val crossSpineCfi =
            "epubcfi(/6/4[spine-chapter-two]!/4/2[chapter-two-root]/" +
                "4[cross-spine-target]/1:4)"
        val crossSpineTarget = successObject(
            harness.runtime(
                "resolvePackage",
                crossSpineCfi,
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            )
        )
        assertEquals(1, crossSpineTarget.getInt("spineIndex"))

        val textAssertion =
            "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root]/1:17" +
                "[some asserted surrounding text])"
        assertFailure(
            harness.runtime(
                "resolvePackage",
                textAssertion,
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )

        val structuralParameter =
            "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root;vendor=value]/1:0)"
        assertFailure(
            harness.runtime(
                "resolvePackage",
                structuralParameter,
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )
    }

    @Test
    fun durableProfileEnforcesServerElementIdLimits() = withHarness { harness ->
        suspend fun profileResult(firstId: String, secondId: String) = harness.runtime(
            "resolvePackage",
            "epubcfi(/6/2!/4[$firstId]/2[$secondId]/1:0)",
            SyntheticEpubCfiSources.packageDocument,
            SyntheticEpubCfiSources.PACKAGE_PATH
        )

        successObject(profileResult("a".repeat(128), "b".repeat(128)))
        assertFailure(
            profileResult("a".repeat(129), "b"),
            "UNSUPPORTED_CFI_FEATURE"
        )
        assertFailure(
            profileResult("a".repeat(128), "b".repeat(129)),
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

    private fun historicalPackageDocument(): String {
        val itemrefs = (1..17).joinToString("") { index ->
            "<itemref id=\"spine-$index\" idref=\"chapter-$index\"/>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata/>
              <manifest/>
              <spine>$itemrefs</spine>
            </package>
        """.trimIndent()
    }
}
