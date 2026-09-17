import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException

class CfiProtocolCheckTest {
    @Test
    fun `current declarations and adapters match the manifest`() = fixture { check() }

    @Test
    fun `changed Kotlin method fails`() = fixture {
        kotlin.writeText(kotlin.readText().replace("= \"resolveContent\"", "= \"renamed\""))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `changed Kotlin adapter method fails`() = fixture {
        adapter.writeText(adapter.readText().replace("CfiRuntimeMethod.RESOLVE_CONTENT,", "CfiRuntimeMethod.GENERATE_PACKAGE,"))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `changed TypeScript method fails`() = fixture {
        typescript.writeText(typescript.readText().replace("METHOD_RESOLVE_CONTENT: \"resolveContent\"", "METHOD_RESOLVE_CONTENT: \"renamed\""))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `changed entrypoint name fails even with current declarations`() = fixture {
        entry.writeText(entry.readText().replace("[P.METHOD_RESOLVE_CONTENT]:", "renamed:"))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `changed positional argument order fails`() = fixture {
        entry.writeText(entry.readText().replace("cfi, packageXml, packagePath, spineIndex", "packageXml, cfi, packagePath, spineIndex"))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `changed runtime version fails`() = fixture {
        runtime.writeText(runtime.readText().replace(checker.version, "0.0.0"))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `changed result discriminator fails`() = fixture {
        typescript.writeText(typescript.readText().replace("readonly ok:", "readonly success:"))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `edited asset method fails even with an unchanged source digest`() = fixture {
        runtime.writeText(runtime.readText().replace("METHOD_RESOLVE_CONTENT: \"resolveContent\"", "METHOD_RESOLVE_CONTENT: \"renamed\""))
        assertFailsWith<GradleException> { check() }
    }

    @Test
    fun `edited asset envelope fails even with an unchanged source digest`() = fixture {
        runtime.writeText(runtime.readText().replace("FIELD_OK: \"ok\"", "FIELD_OK: \"success\""))
        assertFailsWith<GradleException> { check() }
    }

    private fun fixture(block: Fixture.() -> Unit) {
        val directory = Files.createTempDirectory("cfi-protocol-test").toFile()
        try {
            Fixture(directory).block()
        } finally {
            directory.deleteRecursively()
        }
    }

    private class Fixture(directory: File) {
        private val repository = File("..").canonicalFile
        private val workspace = repository.resolve("tools/reader-cfi-runtime")
        val checker = CfiProtocolCheck(workspace.resolve("protocol.json"))
        val kotlin = repository.resolve("app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/CfiProtocol.kt")
            .copyTo(directory.resolve("CfiProtocol.kt"))
        val typescript = workspace.resolve("src/protocol.generated.ts").copyTo(directory.resolve("protocol.ts"))
        val entry = workspace.resolve("src/runtime.ts").copyTo(directory.resolve("runtime.ts"))
        val runtime = repository.resolve("app/src/main/assets/reader/cfi/secondpass-epub-cfi-runtime.js")
            .copyTo(directory.resolve("runtime.js"))
        val adapter = repository.resolve("app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/ReadiumCfiJavascriptRuntime.kt")
            .copyTo(directory.resolve("adapter.kt"))

        fun check() = checker.check(kotlin, typescript, entry, runtime, adapter)
    }
}
